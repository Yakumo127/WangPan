# 企业级文件管理系统

> 当前版本：后端 v2.0.0（Spring Boot 3.2.0） / 前端 v2.0.0（Vue 3.3.x）

## 项目概述

本项目是一个基于 Spring Boot + Vue 3 实现的企业级文件管理系统，面向中小型企业以及团队协作场景，提供从文件上传、存储、管理、预览、分享，到回收站与审计日志的一整套能力。

系统采用前后端分离架构，后端提供 RESTful API，前端提供现代化文件管理界面，支持 Docker 容器化部署和本地物理机部署。系统在设计上重点关注：

- 数据安全（权限控制、审计日志、回收站多阶段删除）
- 大文件处理能力（分片上传、秒传、断点续传）
- 用户体验（拖拽上传、预览、批量操作、响应式界面）
- 运维可观测性（健康检查、监控指标、日志分级）

适合作为企业内部文件盘、项目资料库、团队知识库等基础设施，也可以作为二次开发的基础项目。

## 🚀 核心特性

- **用户管理**：支持用户注册、登录、注销、管理员登录，提供角色（USER/ADMIN）区分、登录失败锁定、邮箱字段与基础资料维护，配合图形验证码降低暴力破解风险。
- **文件管理**：支持普通文件上传/下载/删除/重命名/移动/复制/搜索/批量操作，支持大文件分片上传与合并、文件秒传（基于文件哈希）、版本管理（历史版本与回滚）、浏览器原生下载直链。
- **文件夹管理**：支持无限级文件夹层级，创建/删除/重命名/移动，提供面包屑导航与树形目录选择，支持文件夹复制与批量操作。
- **回收站**：采用软删除机制，用户删除文件/文件夹后首先进入个人回收站，可恢复或彻底删除；管理员可统一管理全局回收站，并支持排期删除、自动清理策略。
- **管理员后台**：提供用户管理、系统配置、存储配额管理、备份与清理任务、操作日志与审计查看、全局回收站管理等能力。
- **文件分享**：支持生成文件下载直链，一次性 Token 验证，结合前端浏览器原生下载行为，可实现更贴近浏览器默认体验的下载流程（可扩展为带密码/过期时间的分享）。
- **存储配额**：为每个用户维护总配额与已用空间，所有上传/删除/回滚操作均会更新配额，管理员可通过系统配置统一调整默认配额。
- **操作日志与审计**：通过 `user_logs` 表记录登录、上传、下载、预览、移动/复制、删除/恢复、系统设置变更等关键操作，包含 IP、UA、执行耗时、结果状态与错误信息，可支持安全审计与问题追踪。
- **文件预览**：支持图片、PDF 等类型在线预览，后端根据可预览后缀进行校验并输出 inline 响应，前端提供专门的预览对话框与预览页面，所有预览行为均写入审计日志。
- **大文件上传与断点续传**：基于 `file_chunks` 与 Blob 存储设计实现分片上传，支持上传进度显示、失败重试、秒传检查等场景，显著降低大文件上传失败带来的影响。
- **下载直链与一次性 Token**：后端为每个下载请求生成短期有效的一次性 Token，并提供 `/api/files/direct-download` 直链接口，前端通过该直链触发浏览器原生下载、避免 Axios 超时问题，同时在审计日志中完整记录下载行为。
- **容器化与自动化运维**：提供 Dockerfile 与 `docker-compose.yml`，同时提供容器内启动脚本（`start-project.sh` / `simple-start.sh` / `h2-start.sh`），支持一键拉起完整开发/测试环境。

## 📋 目录结构

下面是项目在本仓库中的主要目录结构示例（以仓库根目录 `WangPan/` 为例）：

```text
WangPan/
├── backend/                     # 后端 Spring Boot 项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/filemanager/
│   │   │   │   ├── entity/          # 实体类（User、File、Folder、UserLog、SystemConfig、FileChunk 等）
│   │   │   │   ├── dto/             # 数据传输对象（文件版本、管理端文件视图等）
│   │   │   │   ├── controller/      # 控制器（Auth、File、Folder、Admin* 等）
│   │   │   │   ├── service/         # 服务层（文件服务、文件夹服务、系统设置、审计日志等）
│   │   │   │   ├── repository/      # 数据访问层（JPA Repository）
│   │   │   │   ├── security/        # 安全配置（JWT 认证、权限控制）
│   │   │   │   ├── audit/           # 审计日志 AOP 与工具
│   │   │   │   ├── metrics/         # 下载等监控指标
│   │   │   │   └── config/          # 通用配置类
│   │   │   └── resources/
│   │   │       ├── application.yml          # 本地/开发环境默认配置
│   │   │       └── application-docker.yml   # Docker / 生产环境配置
│   └── pom.xml
│
├── frontend/                    # 前端 Vue 3 项目
│   ├── src/
│   │   ├── components/          # 通用组件（文件列表、上传组件、对话框等）
│   │   ├── views/               # 页面组件（文件管理、预览页、系统设置等）
│   │   ├── layout/              # 布局组件（导航栏、侧边栏、面包屑等）
│   │   ├── api/                 # API 封装（Axios 封装、下载直链调用等）
│   │   ├── store/               # 状态管理（Pinia）
│   │   ├── router/              # 路由配置
│   │   └── utils/               # 工具函数（哈希计算、下载工具、格式化等）
│   ├── public/                  # 静态资源
│   ├── dist/                    # 构建产物（生产部署用）
│   └── package.json             # 前端依赖与脚本配置
│
├── config/                      # 部署相关配置
│   ├── backend/                 # 后端 Dockerfile 与配置
│   ├── frontend/                # 前端 Dockerfile 与 Nginx 配置
│   ├── nginx/                   # Nginx 站点配置示例
│   ├── mysql.conf               # MySQL 配置（可选）
│   ├── redis.conf               # Redis 配置（可选）
│   └── init.sql                 # 数据库初始化脚本示例
│
├── database/                    # 数据库相关文件
│   ├── init.sql                 # 初始化脚本（可按需导入）
│   └── enterprise_file_manager_backup.sql  # 示例备份（启动脚本使用）
│
├── docs/                        # 文档中心
│   ├── 文档变更总记录.md          # 文档/代码变更总记录（当前文件遵循此规范）
│   ├── 系统设计/                 # 系统架构与模块设计文档
│   ├── analysis/                # 分析类文档
│   ├── changes/                 # 具体变更实施记录（含文件预览、直链下载等）
│   ├── specs/                   # 设计方案（下载直链、预览方案等）
│   ├── troubleshooting/         # 故障与问题排查记录
│   ├── reports/                 # 专题报告
│   └── ...                      # 其他文档（README、release-notes 等）
│
├── scripts/                     # 辅助脚本（如备份、清理等，按需扩展）
│
├── docker-compose.yml           # Docker Compose 编排文件
├── h2-start.sh                  # 使用 H2 内存数据库 + Redis 的快速开发启动脚本
├── start-project.sh             # 容器内完整开发环境启动脚本（含 MySQL 初始化）
├── simple-start.sh              # 容器内精简启动脚本
├── setup-env.sh                 # 容器内环境初始化脚本（代理、Node、Maven 镜像等）
├── backend.log                  # 后端运行日志（由脚本生成）
├── frontend.log                 # 前端运行日志（由脚本生成）
├── LICENSE                      # 开源许可证（Apache 2.0）
└── README.md                    # 项目说明（本文件）
```

## 🏗️ 项目架构

### 技术栈

#### 后端技术

- **语言与框架**：Java 17 + Spring Boot 3.2.0
- **Web 框架**：Spring Web / Spring MVC
- **数据访问**：Spring Data JPA + Hibernate（MySQL8Dialect），支持实体审计字段自动填充
- **数据库**：MySQL 8.0.x（生产）；H2 内存数据库（开发/快速调试）
- **缓存**：Redis（Spring Data Redis，支持基于 Redis 的缓存与配置）
- **安全**：Spring Security + JWT（登录 Token 与下载直链 Token 分离）
- **配置管理**：`application.yml` + `application-docker.yml`，配合 `SystemConfig` 动态配置表
- **工具库**：Lombok（减少样板代码）、Hutool（工具方法）、Apache POI（导出 Excel）、jjwt（JWT 实现）
- **监控与运维**：Spring Boot Actuator、下载指标收集（DownloadMetrics）、审计日志（AuditLogService）

#### 前端技术

- **框架**：Vue 3.3.x（Composition API）
- **UI 组件库**：Element Plus 2.4.x
- **状态管理**：Pinia 2.1.x
- **路由管理**：Vue Router 4.2.x
- **HTTP 客户端**：Axios 1.6.x（业务接口）+ 原生 `fetch` / 下载直链配合 `<a>` 方式下载
- **构建工具**：Vue CLI 5.0.8（`vue-cli-service`）
- **辅助库**：`pdfjs-dist`（PDF 在线预览）、`xlsx`（Excel 导出）、图标库 `@element-plus/icons-vue`

#### 基础设施

- **容器化**：Docker + Docker Compose
- **Web 服务器**：Nginx 反向代理前端静态资源与后端 API
- **数据库与缓存**：MySQL 8、Redis 7
- **日志与监控**：基于 Logback 的滚动日志、Actuator 健康检查、业务自定义指标

### 架构设计

系统整体采用经典的前后端分离三层架构，并针对文件场景做了额外的分层与安全设计：

```
前端（Vue 3 SPA） → 后端 REST API（Spring Boot） → 数据库（MySQL） + 缓存（Redis） + 文件存储（本地磁盘/挂载卷）
```

- **表现层（前端）**：提供文件列表、上传队列、预览对话框、回收站、系统设置等界面，所有业务通过 API 交互完成。
- **控制层（Controller）**：`AuthController` / `FileController` / `FolderController` / 多个 `Admin*Controller`，负责请求路由、参数校验、权限检查与返回统一响应。
- **业务层（Service）**：`FileService`、`FolderService`、`UserService`、`SystemSettingService`、`DownloadTokenService` 等，实现文件上传/合并、配额检查、预览鉴权、下载直链生成、系统配置读取等核心业务逻辑。
- **数据访问层（Repository）**：基于 Spring Data JPA 的 Repository 接口，负责实体的持久化操作（用户、文件、文件夹、文件分片、系统配置、审计日志等）。
- **缓存与配置层**：Redis 用于缓存部分配置与热点数据，`SystemConfig` 表和系统设置服务提供运行时可调的系统参数（配额、上传策略、清理策略等）。
- **审计与安全**：
  - 使用 Spring Security + JWT 实现登录鉴权与接口权限控制；
  - 对关键操作（下载、预览、删除、恢复、系统设置修改等）通过审计 AOP 写入 `user_logs` 表；
  - 下载直链 Token 使用独立密钥与短 TTL，避免登录 Token 泄露风险，并通过 Redis 或内存机制限制重放。

## 🗄️ 数据库架构

### 核心数据表

#### 1. users (用户表)

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键，自增 ID |
| username | VARCHAR(50) | 用户名（唯一，不可为空） |
| password | VARCHAR(255) | 登录密码（加密存储） |
| email | VARCHAR(100) | 邮箱（唯一，用于通知与找回密码） |
| display_name | VARCHAR(100) | 显示名称/昵称 |
| avatar_url | VARCHAR(255) | 头像 URL |
| phone_number | VARCHAR(20) | 手机号（可选） |
| role | VARCHAR(20) | 角色枚举（USER / ADMIN） |
| quota_limit | BIGINT | 存储配额上限（字节） |
| quota_used | BIGINT | 已用存储空间（字节） |
| enabled | BOOLEAN | 是否启用账号 |
| locked | BOOLEAN | 是否锁定（登录失败过多等） |
| login_attempts | INT | 连续登录失败次数 |
| last_login_time | DATETIME | 最后登录时间 |
| last_login_ip | VARCHAR(45) | 最后登录 IP |
| email_verified | BOOLEAN | 邮箱是否已验证 |
| verification_token | VARCHAR(255) | 邮箱验证 Token |
| password_reset_token | VARCHAR(255) | 找回密码 Token |
| password_reset_expiry | DATETIME | 找回密码 Token 过期时间 |
| create_time | DATETIME | 创建时间（注册时间） |
| update_time | DATETIME | 最近更新时间 |

#### 2. files (文件表)

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键，自增 ID |
| filename | VARCHAR(255) | 存储层文件名（内部使用） |
| original_filename | VARCHAR(255) | 原始文件名（展示给用户） |
| content_type | VARCHAR(100) | MIME 类型（如 `image/png`、`application/pdf`） |
| size | BIGINT | 文件大小（字节） |
| file_path | VARCHAR(500) | 文件物理存储路径 |
| thumbnail_path | VARCHAR(500) | 缩略图路径（图片等） |
| file_hash | VARCHAR(64) | 文件哈希值（用于秒传与去重） |
| user_id | BIGINT | 所属用户 ID（外键） |
| folder_id | BIGINT | 所属文件夹 ID（可为空表示根目录） |
| download_count | INT | 下载次数 |
| description | VARCHAR(255) | 文件描述信息（可选） |
| deleted | BOOLEAN | 是否已删除（软删标记） |
| delete_time | DATETIME | 删除时间（进入回收站的时间） |
| owner_hidden | BOOLEAN | 是否对文件所有者隐藏（用户彻底删除后，仅管理员可见） |
| admin_delete_scheduled | BOOLEAN | 是否已被管理员排期删除（进入冷静期） |
| admin_delete_request_time | DATETIME | 管理员发起排期删除的时间 |
| admin_delete_execute_time | DATETIME | 管理员计划执行删除的时间 |
| admin_delete_reason | VARCHAR(500) | 管理员排期删除原因 |
| quota_released | BOOLEAN | 用户删除时是否已经释放配额，避免重复扣减 |
| active_version_id | BIGINT | 当前激活的文件版本 ID（关联 file_versions 表） |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

> 备注：实际实现中还存在 `file_versions` 与 `blobs` 等表，用于支持多版本与 Blob 去重存储，本 README 重点说明主业务表。

#### 3. folders (文件夹表)

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键，自增 ID |
| name | VARCHAR(255) | 文件夹名称 |
| parent_id | BIGINT | 父文件夹 ID（根目录为空） |
| user_id | BIGINT | 所属用户 ID |
| is_root | BOOLEAN | 是否为用户根目录 |
| description | VARCHAR(255) | 文件夹说明（可选） |
| deleted | BOOLEAN | 是否删除（软删标记） |
| delete_time | DATETIME | 删除时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### 4. user_logs (用户日志表)

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键，自增 ID |
| user_id | BIGINT | 用户 ID（外键） |
| action_type | VARCHAR(50) | 操作类型（如 LOGIN/UPLOAD/DOWNLOAD/PREVIEW 等） |
| action_description | VARCHAR(1000) | 操作描述（自然语言） |
| resource_type | VARCHAR(50) | 资源类型（FILE/FOLDER/USER/SYSTEM 等） |
| resource_id | BIGINT | 资源 ID（可空） |
| resource_name | VARCHAR(255) | 资源名称（如文件名） |
| ip_address | VARCHAR(45) | 客户端 IP |
| user_agent | VARCHAR(500) | 浏览器 UA 信息 |
| status | VARCHAR(20) | 操作状态（SUCCESS/FAILED/PENDING） |
| error_message | VARCHAR(1000) | 错误信息（失败时记录） |
| execution_time | BIGINT | 执行耗时（毫秒） |
| create_time | DATETIME | 日志创建时间 |

#### 5. system_config (系统配置表)

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键，自增 ID |
| config_key | VARCHAR(100) | 配置键（唯一），如 `file.max.size` |
| config_value | VARCHAR(1000) | 配置值（字符串形式） |
| config_type | VARCHAR(50) | 配置类型（数值/布尔/字符串等） |
| description | VARCHAR(500) | 配置说明 |
| is_system | BOOLEAN | 是否系统内置配置（不允许普通用户删除） |
| is_active | BOOLEAN | 是否生效 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 最近更新时间 |
| updated_by | BIGINT | 最后修改人（关联用户 ID） |

#### 6. file_chunks (文件分块表)

| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键，自增 ID |
| file_id | BIGINT | 所属文件 ID |
| user_id | BIGINT | 上传用户 ID |
| chunk_number | INT | 分块编号（从 1 开始） |
| chunk_size | BIGINT | 分块大小（字节） |
| chunk_hash | VARCHAR(64) | 分块哈希（可选） |
| chunk_path | VARCHAR(500) | 分块存储路径 |
| upload_status | VARCHAR(20) | 上传状态（PENDING/UPLOADING/COMPLETED/FAILED） |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### 数据库关系图

关系示意如下：

```text
users (1)      → (N) files
users (1)      → (N) folders
folders (1)    → (N) files
users (1)      → (N) user_logs
files (1)      → (N) file_chunks
files (1)      → (N) file_versions        # 文件历史版本（简要说明）
file_versions  → blobs (N:1 逻辑关联)     # 多版本共享 Blob 存储
```

## 🔌 API 接口文档

> 说明：本节列出常用接口示例，方便前后端联调与第三方系统集成。实际接口字段可参考 `backend/src/main/java/com/filemanager/controller` 目录中的实现。

### 认证相关接口

#### 1. 用户注册

- **URL**：`POST /api/auth/register`
- **功能**：注册普通用户账号。
- **请求参数**（JSON）：

```json
{
  "username": "string (3-20 字符，唯一)",
  "password": "string (6-20 字符)",
  "confirmPassword": "string（确认密码）",
  "email": "string (邮箱格式，唯一)",
  "phone": "string (可选，手机号)"
}
```

- **返回数据**：

```json
{
  "message": "注册成功"
}
```

#### 2. 用户登录

- **URL**：`POST /api/auth/login`
- **功能**：普通用户登录，返回 JWT Token。
- **请求参数**（JSON）：

```json
{
  "username": "string",
  "password": "string"
}
```

> 若启用图形验证码校验，可扩展携带 `captchaKey` 与 `captchaCode` 字段，具体以后端配置为准。

- **返回数据**：

```json
{
  "token": "JWT token",
  "message": "登录成功"
}
```

#### 3. 获取用户信息

- **URL**：`GET /api/auth/userinfo`
- **功能**：获取当前登录用户信息。
- **请求头**：`Authorization: Bearer {token}`
- **返回数据** 示例：

```json
{
  "id": 1,
  "username": "admin",
  "email": "admin@example.com",
  "displayName": "系统管理员",
  "role": "ROLE_ADMIN",
  "roleEnum": "ADMIN",
  "authorities": ["ROLE_ADMIN"],
  "isAdmin": true,
  "quotaLimit": 1073741824,
  "quotaUsed": 0,
  "createTime": "2024-01-01T00:00:00",
  "lastLoginTime": "2024-01-02T12:34:56"
}
```

#### 4. 管理员登录

- **URL**：`POST /api/auth/admin-login`
- **功能**：管理员专用登录入口，后端会校验用户角色为 ADMIN。
- **请求参数**：与用户登录相同。
- **返回数据**：与用户登录相同（JWT token），但后端会在审计日志中记录为管理员登录。

#### 5. 获取图形验证码（旧接口）

- **URL**：`GET /api/auth/captcha`
- **功能**：生成图形验证码图片，返回 PNG 二进制流。
- **返回数据**：`Content-Type: image/png`，前端需要配合 `img` 标签或 Blob 显示。

#### 6. 获取图形验证码（推荐接口）

- **URL**：`GET /api/auth/captcha/new`
- **功能**：生成带 key 的验证码图片（Base64），前端可直接在登录页展示。
- **返回数据** 示例：

```json
{
  "key": "captcha-key-uuid",
  "imageBase64": "data:image/png;base64,...",
  "expiresIn": 300
}
```

#### 7. 更新个人资料

- **URL**：`PUT /api/auth/profile`
- **功能**：更新当前用户的基础资料（昵称、邮箱、头像等）。
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**（JSON，部分字段可选）：

```json
{
  "displayName": "新的显示名称",
  "email": "new-email@example.com",
  "avatarUrl": "https://example.com/avatar.png"
}
```

#### 8. 修改密码

- **URL**：`POST /api/auth/change-password`
- **功能**：当前用户修改密码。
- **请求头**：`Authorization: Bearer {token}`
- **请求参数**：

```json
{
  "oldPassword": "旧密码",
  "newPassword": "新密码"
}
```

- **返回数据**：

```json
{
  "message": "密码修改成功"
}
```

### 文件管理接口

#### 1. 文件上传

- **URL**：`POST /api/files/upload`
- **功能**：上传单个文件（支持大文件）。
- **请求参数**（`multipart/form-data`）：
  - `file`：必填，上传文件。
  - `folderId`：可选，目标文件夹 ID。
  - `parentId`：可选，版本管理场景下父文件 ID。
- **返回数据** 示例：

```json
{
  "message": "文件上传成功",
  "fileId": 1,
  "filename": "example.txt",
  "size": 1024,
  "uploadTime": "2024-01-01T00:00:00"
}
```

> 大文件上传会结合分片上传接口：前端先计算文件哈希，调用 `/api/files/exists` 或 `/api/files/exists-global` 检查是否可以秒传，再按策略调用分片上传与合并接口；本 README 仅给出常用入口说明，详细协议可参见相关设计文档。

#### 2. 获取文件列表

- **URL**：`GET /api/files/list`
- **功能**：获取当前用户在指定文件夹下的文件列表。
- **请求参数**：
  - `folderId`：可选，文件夹 ID（为空则表示根目录）。
- **返回数据**：`File` 对象数组，字段参考数据库 `files` 表定义。

#### 3. 下载文件

- **URL**：`GET /api/files/download/{fileId}`
- **功能**：下载指定文件，支持可选版本号参数。
- **请求参数**：
  - `version`：可选，文件版本号。
- **返回数据**：文件二进制流，Content-Type 根据文件类型设置，响应头将包含合理的 `Content-Disposition` 与 `Accept-Ranges` 以支持断点续传。

#### 4. 删除文件

- **URL**：`DELETE /api/files/{fileId}`
- **功能**：删除（软删）指定文件，将其移动到回收站，并立即释放用户配额。
- **返回数据**：

```json
{
  "message": "文件删除成功"
}
```

#### 5. 重命名文件

- **URL**：`PUT /api/files/{fileId}/rename`
- **功能**：重命名指定文件。
- **请求参数**：

```json
{
  "name": "新文件名"
}
```

- **返回数据**：

```json
{
  "message": "文件重命名成功",
  "fileId": 1,
  "filename": "新文件名"
}
```

#### 6. 获取一次性下载直链

- **URL**：`GET /api/files/{fileId}/download-url`
- **功能**：为当前用户生成指定文件的一次性下载链接（短期有效 Token）。
- **返回数据** 示例：

```json
{
  "url": "/api/files/direct-download?token=...",
  "expiresAt": 1704096000000
}
```

> 前端通常通过 `window.location.href` 或隐藏 `<a>` 标签点击的方式使用该直链，配合浏览器原生下载体验，并避免 Axios 在大文件下载场景中的超时限制。

### 文件夹管理接口

#### 1. 创建文件夹

- **URL**：`POST /api/folders/create`
- **功能**：创建新文件夹。
- **请求参数**：

```json
{
  "name": "文件夹名称",
  "parentId": 1
}
```

> `parentId` 可为空，表示在用户根目录下创建文件夹。

- **返回数据**：

```json
{
  "message": "文件夹创建成功",
  "folderId": 1,
  "name": "文件夹名称",
  "parentId": null,
  "createTime": "2024-01-01T00:00:00"
}
```

#### 2. 获取文件夹列表

- **URL**：`GET /api/folders/list`
- **功能**：获取当前用户的文件夹列表（支持按 `parentId` 获取子目录，用于移动/复制弹窗树形选择）。
- **请求参数**：
  - `parentId`：可选，父文件夹 ID。
- **返回数据**：Folder 对象数组。

### 管理员接口

#### 1. 获取所有用户

- **URL**：`GET /api/admin/users`
- **功能**：获取系统所有用户列表。
- **权限**：仅管理员可调用。
- **返回数据**：User 对象数组，包含配额与状态信息，可用于后台管理。

#### 2. 管理员恢复文件

- **URL**：`PUT /api/files/admin/{fileId}/restore`
- **功能**：管理员在全局回收站中恢复任意用户的文件。
- **权限**：仅管理员可调用。
- **返回数据**：

```json
{
  "message": "文件恢复成功"
}
```

> 管理员还可以通过其他管理端接口调整文件配额、排期删除等，详细可以参考 `Admin*Controller` 中的接口定义。

### 回收站接口

#### 1. 获取回收站文件

- **URL**：`GET /api/files/recycle/bin`
- **功能**：获取当前用户回收站中的文件列表。
- **返回数据**：回收站文件列表（与 `files` 类似，增加删除时间等信息）。

#### 2. 管理员清空所有回收站

- **URL**：`DELETE /api/files/admin/recycle/bin/empty`
- **功能**：清空系统中所有用户的回收站（结合配额释放与 Blob 清理策略）。
- **权限**：仅管理员可调用。
- **返回数据**：

```json
{
  "message": "所有回收站清空成功"
}
```

## 🚀 部署指南

### 环境要求

- **操作系统**：Linux / Windows / macOS（推荐 Linux 服务器）
- **JDK**：OpenJDK 17+
- **Node.js**：16+（推荐 18 LTS），已安装 npm
- **数据库**：MySQL 8.0+
- **缓存**：Redis 6+（推荐 7）
- **Docker**：20.10+（如使用容器部署）
- **Docker Compose**：2.x（`docker compose` 或 `docker-compose`）

### 物理机部署

> 以下示例以 Debian/Ubuntu 为例，其他发行版请根据包管理器调整命令。

#### 1. 环境准备

```bash
# 安装 Java 17
sudo apt update
sudo apt install -y openjdk-17-jdk

# 安装 Node.js（以 18 版本为例）
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# 安装 MySQL 8.0
sudo apt install -y mysql-server
sudo systemctl enable --now mysql

# 安装 Redis
sudo apt install -y redis-server
sudo systemctl enable --now redis-server
```

#### 2. 数据库初始化

```bash
mysql -u root -p

CREATE DATABASE enterprise_file_manager
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'filemanager'@'localhost' IDENTIFIED BY 'filemanager_password';
GRANT ALL PRIVILEGES ON enterprise_file_manager.* TO 'filemanager'@'localhost';
FLUSH PRIVILEGES;
```

#### 3. 后端部署

```bash
# 进入后端目录
cd backend

# 编译打包
mvn clean package -DskipTests

# 运行应用（本地环境使用 application.yml）
java -jar target/enterprise-file-manager-2.0.0.jar
# 或指定配置文件
# java -jar target/enterprise-file-manager-2.0.0.jar --spring.config.name=application
```

#### 4. 前端部署

```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 开发模式（本地调试）
npm run serve

# 构建生产版本
npm run build

# 使用 Nginx 部署 dist 目录
sudo mkdir -p /var/www/filemanager
sudo cp -r dist/* /var/www/filemanager/
# 配置 Nginx 站点后重载服务
```

#### 5. 使用启动脚本（容器/一体化环境）

在容器化开发环境或一体化环境中，可以使用仓库根目录下的启动脚本简化操作：

```bash
# 使用完整启动脚本（初始化 MySQL + 构建后端 + 启动前后端）
chmod +x start-project.sh
./start-project.sh

# 或使用简化启动脚本（假设依赖已安装）
chmod +x simple-start.sh
./simple-start.sh
```

> 提示：脚本内部默认使用 `/workspace` 作为项目根路径，并调用 `npm run dev` 启动前端。如你在本地直接使用，请根据实际路径与前端脚本（`npm run serve`）自行调整或仅参考其步骤逻辑。

### Docker 部署

#### 1. 使用 Docker Compose 一键启动

```bash
# 克隆项目
git clone <repository-url>
cd WangPan   # 或项目实际目录名

# 如需要自定义环境变量，可在根目录创建 .env 文件
# 例如设置数据库密码、端口等（参考 docker-compose.yml 中的变量）
# touch .env

# 启动所有服务
docker compose up -d
# 或 docker-compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f
```

#### 2. 单独构建镜像并运行

```bash
# 在项目根目录构建后端镜像
docker build -f config/backend/Dockerfile -t filemanager-backend:latest .

# 构建前端镜像
docker build -f config/frontend/Dockerfile -t filemanager-frontend:latest .

# 运行容器
docker run -d --name filemanager-backend -p 8080:8080 filemanager-backend:latest
docker run -d --name filemanager-frontend -p 8081:80 filemanager-frontend:latest
```

### 环境配置

#### 1. 后端配置（application.yml）

`backend/src/main/resources/application.yml` 示例（本地开发）：

```yaml
spring:
  application:
    name: enterprise-file-manager
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/enterprise_file_manager?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: filemanager
    password: filemanager_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQL8Dialect
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
  redis:
    host: localhost
    port: 6379
    password:
    database: 0
  cache:
    type: redis
    redis:
      time-to-live: 600000    # 缓存过期时间（毫秒）

server:
  port: 8080
  servlet:
    context-path: /

logging:
  level:
    com.filemanager: DEBUG
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

file:
  storage:
    path: /tmp/cunchu
    default-location: /tmp/cunchu
    default-path: /tmp/cunchu
    max-file-size: 10485760      # 10MB
    chunk-size: 1048576          # 1MB
    temp-dir: /app/temp
    imageio:
      use-cache: true
    thumbnail:
      max-side: 256
      max-source-bytes: 52428800   # 50MB
      max-source-pixels: 50000000  # 5000万像素

jwt:
  secret: enterpriseFileManagerSecretKey2024
  expiration: 86400000    # 24 小时（登录 Token）

download:
  token:
    secret: enterpriseFileManagerDownloadSecretKey2024
    ttl-seconds: 10        # 下载一次性 Token 有效期（秒）

recycle:
  admin:
    retention-days: 15
  purge:
    fixed-delay-ms: 3600000   # 1 小时巡检
  manual-purge:
    enabled: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env
      base-path: /actuator
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  health:
    redis:
      enabled: true
    db:
      enabled: true

audit:
  enabled: true
  retention-days: 180
  export:
    max-rows: 50000
  purge:
    cron: "0 0 3 * * ?"
  success-fallback:
    enabled: true
    only-get: true
```

> 生产环境建议通过 `application-docker.yml` 或外部化配置管理敏感信息（如密码、密钥），避免直接写死在仓库中。

#### 2. 前端配置

前端通过环境变量控制后端 API 地址等信息，通常在 `.env.*` 或 `vue.config.js` 中指定。示例：

```js
// 示例配置：src/api/request.js 中的基础地址
const API_BASE_URL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080';

export { API_BASE_URL };
```

构建生产包时可通过设置 `VUE_APP_API_BASE_URL` 环境变量来指向实际后端地址。

#### 3. Nginx 配置

示例 Nginx 配置（前后端同域部署）：

```nginx
server {
    listen 80;
    server_name localhost;

    # 前端静态资源
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 文件下载/预览代理（可与 /api/ 合并）
    location /files/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## ✨ 已实现功能

### 用户功能

#### 1. 用户认证

- ✅ 用户注册（用户名/邮箱唯一校验）
- ✅ 用户登录（JWT 认证）
- ✅ 管理员登录专用接口
- ✅ 图形验证码（旧版图片接口与新版 Base64 接口）
- ✅ 登录失败次数累计与锁定机制
- ✅ 密码修改
- ✅ 个人资料管理（昵称、邮箱、头像）
- ✅ 角色管理（USER/ADMIN）

#### 2. 文件管理

- ✅ 文件上传（支持大文件）
- ✅ 文件下载（支持断点续传与多版本下载）
- ✅ 文件删除（软删除，进入回收站）
- ✅ 文件重命名
- ✅ 文件移动（支持批量）
- ✅ 文件复制（支持批量，支持跨目录复制）
- ✅ 文件搜索（基于名称等字段）
- ✅ 文件预览（图片、PDF 等）
- ✅ 文件分享（下载直链 + 一次性 Token）
- ✅ 批量操作（批量移动/复制/删除/下载）
- ✅ 文件版本管理（版本列表、指定版本下载与回滚）
- ✅ 秒传（按文件哈希检查已存在文件/Blob）

#### 3. 文件夹管理

- ✅ 创建文件夹（支持层级结构）
- ✅ 删除文件夹（软删，进入回收站）
- ✅ 重命名文件夹
- ✅ 移动文件夹
- ✅ 文件夹层级管理与树状导航
- ✅ 面包屑导航
- ✅ 文件夹复制（按需扩展）

#### 4. 回收站功能

- ✅ 个人回收站（每个用户独立）
- ✅ 文件/文件夹恢复
- ✅ 永久删除（从回收站彻底删除）
- ✅ 管理员回收站管理（可跨用户恢复/删除）
- ✅ 批量恢复
- ✅ 批量删除
- ✅ 管理员排期删除与冷静期配置

### 管理员功能

#### 1. 用户管理

- ✅ 用户列表查看
- ✅ 用户搜索
- ✅ 用户创建/编辑/删除
- ✅ 用户状态管理（启用/禁用/锁定）
- ✅ 用户角色与权限管理
- ✅ 配额管理（调整单个用户存储配额）

#### 2. 系统管理

- ✅ 系统配置管理（上传策略、回收站策略、备份策略等）
- ✅ 存储配额全局配置（默认配额、最大文件大小、分片大小等）
- ✅ 用户操作日志查看与导出
- ✅ 系统监控面板（健康检查、存储使用等）
- ✅ 数据备份与清理策略配置

#### 3. 回收站管理

- ✅ 全局回收站浏览
- ✅ 恢复任意用户的文件/文件夹
- ✅ 永久删除指定文件/文件夹
- ✅ 一键清空所有回收站（结合配额与 Blob 清理）

### 系统特性

#### 1. 安全特性

- ✅ JWT 令牌认证（登录 Token 与下载 Token 分离）
- ✅ 密码加密存储
- ✅ 用户角色与权限分级管理
- ✅ 防止 SQL 注入（基于 JPA 与参数化查询）
- ✅ 文件访问控制（按用户/管理员区分）
- ✅ 审计日志记录（关键操作全记录）
- ✅ 下载直链 Token 短期有效、防重放设计

#### 2. 性能优化

- ✅ Redis 缓存系统配置等热点数据
- ✅ 文件分片上传与断点续传
- ✅ 秒传（按文件哈希快速创建文件）
- ✅ 图片缩略图生成与缓存
- ✅ 数据库索引优化（常用查询字段建索引）
- ✅ 前端懒加载、列表分页与虚拟滚动（按需）

#### 3. 用户体验

- ✅ 响应式布局（适配常见桌面分辨率）
- ✅ 拖拽上传
- ✅ 上传进度与上传队列管理
- ✅ 实时搜索与过滤
- ✅ 多语言支持（可扩展）
- ✅ 深色模式（可扩展/实验性）

### 前端界面

#### 1. 用户界面

- ✅ 登录页面（验证码、错误提示）
- ✅ 注册页面
- ✅ 个人中心（个人资料与修改密码）
- ✅ 文件管理界面（新版文件列表 + 工具栏 + 上传队列）
- ✅ 文件夹树与面包屑导航
- ✅ 回收站界面
- ✅ 文件预览界面（图片/PDF 等）
- ✅ 文件分享/下载直链触发入口

#### 2. 管理员界面

- ✅ 管理员登录入口
- ✅ 用户管理页面
- ✅ 配额与系统设置页面
- ✅ 操作日志与审计页面
- ✅ 全局回收站管理页面
- ✅ 备份与清理策略配置界面

#### 3. 通用组件

- ✅ 文件列表组件（支持多选、批量操作、列宽调整等）
- ✅ 文件上传组件（队列、进度、暂停/恢复）
- ✅ 文件夹树组件
- ✅ 搜索与过滤组件
- ✅ 分页组件
- ✅ 通用对话框组件（确认、表单等）

## 📊 系统监控

### 健康检查

- 后端健康检查：`GET /actuator/health`
- 数据库健康：通过 Actuator `db` 指标或 `mysqladmin ping` 检查
- Redis 健康：通过 Actuator `redis` 指标或 `redis-cli ping` 检查

### 日志管理

- 应用日志：后端日志文件（容器中通常位于 `/app/logs` 或根目录 `backend.log`）
- 访问日志：Nginx 访问日志，用于分析访问流量与状态码
- 错误日志：Nginx 错误日志 + 应用异常日志，用于排查故障
- 审计日志：数据库表 `user_logs`，记录所有关键业务操作

### 性能监控

- 文件上传/下载耗时与错误率（DownloadMetrics 指标）
- 系统资源使用情况（CPU/内存/磁盘，可通过外部监控系统接入）
- 数据库连接池使用情况
- Redis 缓存命中率与连接数

## 🔧 开发指南

### 本地开发环境

#### 1. 后端开发

```bash
cd backend
mvn spring-boot:run
```

> 默认使用 `application.yml`。如需切换到 Docker 配置，可通过 `--spring.config.location` 或 `--spring.profiles.active` 指定。

#### 2. 前端开发

```bash
cd frontend
npm install
npm run serve
```

> 开发环境下前端默认请求 `http://localhost:8080`，可通过 `.env.development` 或代理配置修改。

#### 3. 数据库开发（使用 H2 内存数据库）

```bash
# 启动依赖 Redis 与 H2 的简化环境
./h2-start.sh
```

> 注意：`h2-start.sh` 会直接覆盖 `backend/src/main/resources/application.yml`，将数据源改为 H2 内存数据库，仅适用于本地临时实验。使用后如需切回 MySQL，请恢复原配置或从版本控制中重新 checkout。

### 代码规范

#### 后端代码规范

- 使用 Spring Boot 与 Spring Data JPA 的最佳实践：分层清晰（Controller/Service/Repository）。
- 使用 Lombok 简化实体与 DTO，但同时在关键实体中保留手写 Getter/Setter 以保证编译稳定性。
- 统一异常处理与返回格式，避免泄露敏感信息。
- 对关键业务（文件上传、下载、预览、删除等）编写单元测试或集成测试。

#### 前端代码规范

- 优先使用 Vue 3 Composition API 编写组件逻辑。
- 遵循 ESLint 与 `eslint-plugin-vue` 的规范。
- 组件化开发：将文件列表、上传组件、预览组件等拆分为可复用单元。
- 避免在组件中直接拼接 URL，统一通过 API 封装模块调用后端接口。

### 测试

#### 单元测试（后端）

```bash
cd backend
mvn test
```

#### 前端单元测试（如有配置）

```bash
cd frontend
npm run test:unit
```

> 生产前建议至少对核心流程（登录、上传、下载、预览、回收站恢复/删除）进行手工回归与自动化测试。

## 📈 版本历史

### v2.0.0（当前版本）

- ✅ 引入文件预览能力（图片/PDF），并在前端提供专门预览对话框与预览页。
- ✅ 实现下载直链与一次性 Token 机制，配合浏览器原生下载，解决大文件下载 Axios 超时问题。
- ✅ 新版文件管理界面：上传队列、大文件分片上传与秒传、批量移动/复制/删除/下载等体验优化。
- ✅ 完善回收站与管理员排期删除逻辑，引入 `owner_hidden`、`admin_delete_*` 等字段。
- ✅ 增强系统配置与审计能力：`system_config` 配置键体系、`user_logs` 细化字段、下载/预览审计记录。
- ✅ 补充 H2 开发环境脚本与 Docker 部署配置，提升开发/测试便捷性。

### v1.0.0（历史版本）

- ✅ 完整的基础文件管理功能（上传/下载/删除/重命名/移动/复制）。
- ✅ 用户认证和权限管理（USER/ADMIN）。
- ✅ 管理员后台基础能力（用户管理、回收站管理等）。
- ✅ Docker 容器化部署雏形。
- ✅ 基础 API 文档与目录结构说明。

## 📄 许可证

本项目采用 Apache 2.0 许可证，详情见根目录 `LICENSE` 文件。

## 🆘 技术支持

- 如在部署或二次开发过程中遇到问题，建议优先：
  - 查看 `docs/troubleshooting` 下的常见问题与排查记录；
  - 查看 `docs/specs`、`docs/changes` 中相关功能的设计与实施文档。
- 如仍无法解决，可在代码托管平台提交 Issue，或结合日志/接口返回信息向大模型提问，附上尽量完整的上下文信息（请求参数、错误堆栈、日志片段等），以便快速定位问题。

---

**企业级文件管理系统** —— 为企业用户提供安全、高效、可审计的文件管理解决方案。

