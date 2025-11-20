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
        Path dst = blobPath(h);

        // 若已有元数据，需校验物理文件是否存在；不存在则尝试用本次上传的临时文件自愈
        Blob existed = find(h);
        if (existed != null) {
            Path existedPath = null;
            try { existedPath = java.nio.file.Paths.get(existed.getPath()); } catch (Exception ignore) {}
            boolean existedPhysical = existedPath != null && java.nio.file.Files.exists(existedPath);
            boolean dstPhysical = java.nio.file.Files.exists(dst);

            // 情况1：元数据路径存在，直接清理临时文件并返回
            if (existedPhysical) {
                try { if (tempPath != null) Files.deleteIfExists(tempPath); } catch (Exception ignore) {}
                return existed;
            }

            // 情况2：元数据路径不存在，但按照规则计算的 dst 已存在（例如路径规则升级/迁移后），更新元数据路径
            if (dstPhysical) {
                try {
                    if (!dst.toString().equals(existed.getPath())) {
                        existed.setPath(dst.toString());
                        // 补齐缺失字段
                        if (existed.getSize() == null) existed.setSize(size);
                        if (existed.getContentType() == null || existed.getContentType().isBlank()) existed.setContentType(contentType);
                        existed.setLastAccessAt(LocalDateTime.now());
                        existed = blobRepository.save(existed);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("修正 Blob 元数据路径失败", e);
                } finally {
                    try { if (tempPath != null) Files.deleteIfExists(tempPath); } catch (Exception ignore) {}
                }
                return existed;
            }

            // 情况3：物理文件缺失，但有临时文件，可自愈恢复
            if (tempPath != null && java.nio.file.Files.exists(tempPath)) {
                try {
                    if (!java.nio.file.Files.exists(dst.getParent())) {
                        java.nio.file.Files.createDirectories(dst.getParent());
                    }
                    try {
                        java.nio.file.Files.move(tempPath, dst, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    } catch (Exception e) {
                        try {
                            if (!java.nio.file.Files.exists(dst)) {
                                java.nio.file.Files.move(tempPath, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (Exception e2) {
                            if (!java.nio.file.Files.exists(dst)) throw e2;
                        } finally {
                            try { java.nio.file.Files.deleteIfExists(tempPath); } catch (Exception ignore) {}
                        }
                    }
                    // 更新元数据并返回
                    existed.setPath(dst.toString());
                    existed.setSize(size);
                    existed.setContentType(contentType);
                    existed.setLastAccessAt(LocalDateTime.now());
                    existed = blobRepository.save(existed);
                    return existed;
                } catch (Exception e) {
                    throw new RuntimeException("Blob 自愈恢复失败", e);
                }
            }

            // 情况4：既无物理文件也无临时源文件，不能伪装成功
            throw new RuntimeException("Blob 元数据存在但物理文件缺失，且无源文件可修复: " + h);
        }

        // 正常创建：无元数据，使用临时文件落盘并登记
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
