# 文件下载 404（上传成功但未生成 blobs 文件）问题解决报告（2025-11-20 10:00）

## 背景与症状

- 现象：前端点击下载提示 404。
  - 浏览器控制台报错：
    - `GET http://116.198.32.12:18800/api/files/download/9 404 (Not Found)`
    - Axios 报错 `ERR_BAD_REQUEST`（后端返回 HTTP 404）。
- 网络拓扑：
  - `116.198.32.12` 为 FTP 服务器，端口 `18800` 映射至客户端（宿主机）前端端口 `3000`。
  - 前端在宿主机 `3000` 端口运行，通过 `vue.config.js` 代理 `'/api' -> 'http://localhost:8080'` 访问后端。
  - 后端服务端口 `8080` 仅在宿主机，未对外暴露。
- 存储目录：宿主机 `/tmp/cunchu` 下只有 `user_1` 目录，且为空；不存在 `blobs` 目录。

## 影响范围

- 所有依赖 `GET /api/files/download/{id}` 的下载请求在文件物理缺失时均返回 404。
- 若 blobs 元数据存在但物理文件缺失，“重复上传同一文件”会显示上传成功，但仍无法下载，用户体验受损并可能造成数据错觉。

## 排查过程（时间线）

1. 前端接口与代理确认（定位是否路径问题）：
   - `frontend/src/utils/request.js` 使用 `baseURL: '/api'`。
   - `frontend/vue.config.js` 代理 `'/api' -> 'http://localhost:8080'`。
   - 前端下载 API：`frontend/src/api/file.js: downloadFile(id) => GET /files/download/{id}`。
   - 结论：前端请求路径与代理配置正确，请求确实到后端 `8080`。

2. 后端路由与权限确认（定位是否 404 路由缺失或被拦截）：
   - 路由存在：`backend/src/main/java/com/filemanager/controller/FileController.java: @GetMapping("/download/{fileId}")`。
   - 安全配置：`backend/src/main/java/com/filemanager/config/SecurityConfig.java` 要求鉴权，但该接口对已登录用户开放；管理员下载接口为 `/api/files/admin/download/{fileId}`。
   - 结论：路由存在，不是“找不到路由”的 404。

3. 后端 404 的来源定位（业务逻辑）：
   - 下载流程：
     - 校验文件归属与状态：`FileService.getFileForDownload(...)`，找不到记录或非属主 → 抛 `NotFound/Forbidden`。
     - 物理文件存在性检查：`FileController.downloadFile(...)` 中 `Files.exists(filePath)` 不存在 → 抛 `NotFoundException("文件不存在")`。
     - 全局异常处理：`GlobalExceptionHandler` 将 `NotFoundException` 转为 HTTP 404。
   - 结论：404 是“文件物理不存在”导致，而非权限或路由问题。

4. 存储路径与数据库对比（核验真实路径）：
   - 存储配置：`backend/src/main/resources/application.yml` 中 `file.storage.path: /tmp/cunchu`。
   - 数据库查询（id=9）：
     - `files.file_path = /tmp/cunchu/blobs/59/bc/59bc9f1a...c89b6`
     - `files.file_hash = 59bc9f1a...c89b6`
     - `blobs.path = /tmp/cunchu/blobs/59/bc/59bc9f1a...c89b6`
   - 文件系统查验：上述路径在磁盘上均不存在；`/tmp/cunchu/blobs` 目录也不存在。
   - 结论：数据库有条目，但物理文件缺失；根因需回溯到上传落盘逻辑。

5. 上传落盘逻辑审计（关键根因发现）：
   - 上传流程（简述）：
     1) 将用户上传的文件临时写入 `/tmp/cunchu/user_{uid}/随机名`；
     2) 计算哈希后，调用 `BlobService.ensureFromTemp(hash, ...)` 将临时文件移入 `blobs` 目录，写入/复用 `blobs` 元数据；
     3) 将文件记录 `files.file_path` 指向 `blob.path`。
   - 问题代码：`backend/src/main/java/com/filemanager/service/BlobService.java`
     - 原实现逻辑（简化伪码）：
       ```java
       Blob existed = find(hash);
       if (existed != null) {
           // 直接删除临时文件并返回，完全不校验 existed.path 对应的物理文件是否存在
           Files.deleteIfExists(tempPath);
           return existed;
       }
       // 否则将 tempPath 移动到 blobs 路径，创建元数据
       ```
     - 后果：如果曾经存在某个哈希的 blobs 元数据，但因为运维清理/迁移等因素导致物理文件被删，那么后续“重复上传同一文件”会：
       - 删除本次上传的临时文件；
       - 不创建 `blobs` 物理文件；
       - 返回“上传成功”，但下载必然 404。
   - 结论：这是功能性 Bug（元数据短路），直接导致“上传成功但未生成 blobs 文件”。

## 根因总结

- `BlobService.ensureFromTemp` 在 `existed != null` 的分支没有校验物理文件存在性，也没有“自愈”逻辑；一旦物理文件缺失，该分支会删除临时文件并返回已有元数据，导致磁盘不落文件且对外表现为“上传成功”。
- 使用 `/tmp` 作为长期存储路径存在被系统清理的风险，放大了该问题的触发概率。

## 修复思路与方案设计

目标：
1) 保证上传成功时，磁盘必然存在对应的 blobs 物理文件；
2) 当发现元数据存在而物理文件缺失时，能优先利用当前上传的临时文件进行“自愈”恢复；
3) 在无法恢复的情况下（既无物理文件亦无临时源文件）明确失败（抛错），而不是虚假成功。

方案（对 `ensureFromTemp` 的健壮化改造）：
- 分支处理：
  - 情况1：元数据存在且物理文件存在 → 清理临时文件并返回（保持高效复用）。
  - 情况2：元数据存在但物理文件不存在，且“计算得到的目标 dst 路径”已存在 → 修正元数据路径，清理临时文件并返回（兼容路径规则变化）。
  - 情况3：元数据存在且物理文件不存在，但本次上传的临时文件存在 → 创建父目录，将临时文件移动至 blobs 目标路径，更新元数据字段（path/size/contentType/lastAccessAt），实现自愈恢复。
  - 情况4：既无物理文件也无临时源文件 → 抛出 `RuntimeException`，防止“假成功”。
- 并发与稳健性：继续使用 `ATOMIC_MOVE` → 失败后退化为 `REPLACE_EXISTING`，并在 finally 中清理临时文件；父目录不存在时先创建。

## 实施修改（实际变更）

- 修改文件：`backend/src/main/java/com/filemanager/service/BlobService.java`
  - 方法：`ensureFromTemp(String hash, long size, String contentType, Path tempPath)`
  - 主要变更点：
    - 将 `existed != null` 分支改为四段式健壮逻辑（见“修复思路与方案设计”）。
    - 对路径 `dst` 统一用 `blobPath(hash)` 计算并对比 `existed.getPath()`。
    - 自愈成功后更新 `existed` 的 `path/size/contentType/lastAccessAt` 并保存。
    - 在无临时源文件情况下，明确抛出异常，避免虚假成功。

代码摘要（关键片段）：

```java
Blob existed = find(h);
if (existed != null) {
    Path existedPath = ...; boolean existedPhysical = exists(existedPath);
    boolean dstPhysical = Files.exists(dst);

    if (existedPhysical) { // 情况1
        deleteIfExists(tempPath); return existed;
    }
    if (dstPhysical) {     // 情况2
        // 校正元数据路径
        existed.setPath(dst.toString());
        // 补齐缺失字段并保存
        existed = blobRepository.save(existed);
        deleteIfExists(tempPath);
        return existed;
    }
    if (tempPath != null && Files.exists(tempPath)) { // 情况3
        // 目录创建 + 原子移动（失败回退）
        move(tempPath -> dst);
        // 更新元数据并保存
        existed.setPath(dst.toString());
        existed.setSize(size);
        existed.setContentType(contentType);
        existed.setLastAccessAt(LocalDateTime.now());
        existed = blobRepository.save(existed);
        return existed;
    }
    // 情况4：不可自愈
    throw new RuntimeException("Blob 元数据存在但物理文件缺失，且无源文件可修复: " + h);
}
// 原逻辑保留：首次创建分支
```

## 验证方案

1) 功能验证：
   - 新上传一个小文件：应自动在 `/tmp/cunchu/blobs/<两级目录>/<hash>` 生成文件；下载 200；数据库写入 `blobs` 与 `files.file_path` 正确。
   - 复测历史问题：删除某个已有 blobs 记录的物理文件（仅测试环境），再次上传相同文件，应触发“自愈”并重新创建物理文件，下载恢复正常。

2) 回归验证：
   - 并发上传同一文件：应仍能正确落盘（`ATOMIC_MOVE` + 回退策略）；最终不会出现“元数据存在但物理缺失”。
   - 非图片/大文件：不受影响，路径规则一致。

## 风险评估与兼容性

- 新逻辑在“无法自愈”的场景抛出异常，改变了原有“虚假成功”的行为，短期看会暴露真实问题；长期看可防止更多坏数据。
- 对已有正常数据无影响；对历史不一致数据将尽力自愈或显式失败。
- 并发与权限不受影响；只改动了 Blob 落盘/自愈环节。

## 运维建议

- 避免将长期数据放在 `/tmp`，建议改为持久目录（如 `/data/efm`）并在 `application.yml` 中配置：
  ```yaml
  file:
    storage:
      path: /data/efm
      default-location: /data/efm
      default-path: /data/efm
  ```
- 增加定期自检任务（可选）：
  - 扫描 `blobs` 表，校验 `path` 是否存在；
  - 对于缺失项：若存在 `files` 引用且近期有上传临时文件，可尝试修复；否则记录告警。

## 附录：核查与验证命令

- 检查存储根与 blobs：
  ```bash
  ls -ld /tmp/cunchu /tmp/cunchu/blobs
  find /tmp/cunchu/blobs -type f | head -n 50
  ```

- 查询文件与 blob：
  ```bash
  mysql -h127.0.0.1 -P3306 -ufilemanager -pfilemanager_password enterprise_file_manager \
    -e "SELECT id, original_filename, file_path, file_hash FROM files WHERE id=9\G"
  mysql -h127.0.0.1 -P3306 -ufilemanager -pfilemanager_password enterprise_file_manager \
    -e "SELECT hash, path FROM blobs WHERE hash=(SELECT file_hash FROM files WHERE id=9)\G"
  ls -l /tmp/cunchu/blobs/59/bc/59bc9f1af958ab209a47de4c4739c5814b7b2a89e4cecf8e486a3431653c89b6
  ```

## 变更文件清单（最终）

- `backend/src/main/java/com/filemanager/service/BlobService.java`

以上为完整问题解决与修复记录。

