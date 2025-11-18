package com.filemanager.service;

import com.filemanager.entity.Blob;
import com.filemanager.repository.BlobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BlobService {

    private final BlobRepository blobRepository;
    private final SystemSettingService systemSettingService;

    @Value("${file.storage.path}")
    private String storagePath;

    public Path blobPath(String hash) {
        String h = hash.toLowerCase();
        if (h.length() >= 4) {
            String d1 = h.substring(0, 2);
            String d2 = h.substring(2, 4);
            return Paths.get(storagePath, "blobs", d1, d2, h);
        } else {
            return Paths.get(storagePath, "blobs", "xx", "yy", h);
        }
    }

    public Blob find(String hash) {
        return blobRepository.findById(hash.toLowerCase()).orElse(null);
    }

    // 确保 Blob 存在：若不存在则将 tempPath 原子移动至 blob 路径并创建元数据
    public Blob ensureFromTemp(String hash, long size, String contentType, Path tempPath) {
        String h = hash.toLowerCase();
        if (h.length() < 4) {
            // 非致命：记录到 stderr，继续尝试
            System.err.println("[BlobService] 非预期哈希长度: " + h);
        }
        Blob existed = find(h);
        if (existed != null) {
            // 清理本地临时/用户文件
            try { if (tempPath != null) Files.deleteIfExists(tempPath); } catch (Exception ignore) {}
            return existed;
        }
        Path dst = blobPath(h);
        try {
            if (!Files.exists(dst.getParent())) {
                Files.createDirectories(dst.getParent());
            }
            if (tempPath != null && Files.exists(tempPath)) {
                // 原子移动：若并发创建，CREATE_NEW 可能失败，降级为使用已存在
                try {
                    java.nio.file.Files.move(tempPath, dst, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception e) {
                    // 回退：尝试普通移动/覆盖（若仍失败且目标不存在，则抛出）
                    try {
                        if (!Files.exists(dst)) {
                            java.nio.file.Files.move(tempPath, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e2) {
                        if (!Files.exists(dst)) throw e2;
                    } finally {
                        try { java.nio.file.Files.deleteIfExists(tempPath); } catch (Exception ignore) {}
                    }
                }
            } else {
                // 若无源文件，仅登记元数据（用于外部导入/存在性校验），但一般不走这里
                if (!Files.exists(dst)) {
                    throw new IllegalStateException("Blob 源文件缺失，无法创建: " + h);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("写入 Blob 失败", e);
        }

        Blob b = new Blob();
        b.setHash(h);
        b.setSize(size);
        b.setContentType(contentType);
        b.setPath(dst.toString());
        b.setCreatedAt(LocalDateTime.now());
        b.setLastAccessAt(LocalDateTime.now());
        return blobRepository.save(b);
    }

    // 垃圾回收：删除未被任何版本引用的 Blob 及物理文件
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_BLOB_GC,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_BLOB,
            userId = "@auditSpel.currentUserId()",
            description = "'手动 Blob GC'"
    )
    public int gcUnreferenced() {
        // 在线备份窗口内冻结 GC（对用户无感）
        try {
            if (systemSettingService.getBoolean("backup.gc.freeze", false) || systemSettingService.getBackupGcFreezeCount() > 0) { return 0; }
        } catch (Exception ignore) {}
        int removed = 0;
        java.util.List<Blob> list = blobRepository.findUnreferenced();
        for (Blob b : list) {
            try {
                Path p = Paths.get(b.getPath());
                try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignore) {}
                if (b.getThumbnailPath() != null) {
                    try { java.nio.file.Files.deleteIfExists(Paths.get(b.getThumbnailPath())); } catch (Exception ignore) {}
                }
                blobRepository.delete(b);
                removed++;
            } catch (Exception ignore) {}
        }
        return removed;
    }
}
