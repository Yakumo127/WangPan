package com.filemanager.service;

import com.filemanager.entity.File;
import com.filemanager.entity.Folder;
import com.filemanager.entity.User;
import com.filemanager.repository.FileRepository;
import com.filemanager.repository.UserRepository;
import com.filemanager.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Transactional
public class FileService {
    
    @Value("${file.storage.path}")
    private String storagePath;
    @Value("${file.storage.thumbnail.max-side:256}")
    private int thumbnailMaxSide;
    @Value("${file.storage.thumbnail.max-source-bytes:52428800}")
    private long thumbnailMaxSourceBytes; // 50MB 默认
    @Value("${file.storage.thumbnail.max-source-pixels:50000000}")
    private long thumbnailMaxSourcePixels; // 5000万像素默认
    @Value("${file.storage.imageio.use-cache:true}")
    private boolean imageIoUseCache;
    @Value("${file.storage.temp-dir:}")
    private String tempDir;
    @Value("${recycle.admin.retention-days:15}")
    private int adminRetentionDays;
    
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final BlobService blobService;
    private final com.filemanager.repository.FileVersionRepository fileVersionRepository;
    private final com.filemanager.repository.BlobRepository blobRepository;

    @Value("${file.quota.mode:current}")
    private String quotaMode;

    private boolean isAllHistoryQuota() {
        return quotaMode != null && quotaMode.trim().equalsIgnoreCase("all-history");
    }

    // 根据配额模式调整：current=按差值；all-history=按新版本完整大小
    public void adjustQuotaForNewVersion(Long userId, long oldActiveSize, long newSize) {
        long delta = isAllHistoryQuota() ? newSize : (newSize - oldActiveSize);
        if (delta > 0) {
            int ok = userRepository.tryUseQuota(userId, delta);
            if (ok == 0) throw new RuntimeException("存储空间不足，无法创建新版本");
        } else if (delta < 0) {
            userRepository.releaseQuotaAmount(userId, -delta);
        }
    }
    
    public File uploadFile(MultipartFile file, Long userId, Long folderId) throws IOException { return uploadFile(file, userId, folderId, null); }

    // 支持可选 parentId：若传入则为该文件创建新版本
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPLOAD,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#result?.id",
            resourceName = "#result?.originalFilename",
            description = "'上传文件'"
    )
    public File uploadFile(MultipartFile file, Long userId, Long folderId, Long parentId) throws IOException {
        long start = System.currentTimeMillis();
        // 校验用户存在
        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 上传策略校验：后缀白名单（allowAll=false 时生效）
        if (!systemSettingService.isUploadAllowAll()) {
            String originalFilenameChk = file.getOriginalFilename();
            String ext = getFileExtension(originalFilenameChk);
            String suffix = (ext == null) ? "" : ext.replaceFirst("^\\.", "").toLowerCase();
            java.util.List<String> allowed = systemSettingService.getAllowedSuffixes();
            if (suffix.isEmpty() || allowed.isEmpty() || !allowed.contains(suffix)) {
                throw new com.filemanager.exception.UploadTypeNotAllowedException("不允许上传该类型文件", allowed);
            }
        }

        // 原子配额扣减（并发安全）——在写盘前预占额度，失败将随事务回滚
        long size = file.getSize();
        if (size < 0) size = 0; // 理论上不会，但避免异常值
        int updated = userRepository.tryUseQuota(userId, size);
        if (updated == 0) {
            long available = Math.max(0L, userRepository.findById(userId).map(com.filemanager.entity.User::getRemainingQuota).orElse(0L));
            throw new com.filemanager.exception.QuotaExceededException("存储空间不足，无法上传该文件", size, available);
        }

        // 创建存储目录
        Path uploadPath = Paths.get(storagePath, "user_" + userId);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成文件名与路径（临时落到用户目录，完成后转存 Blob 或删除）
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String newFilename = UUID.randomUUID().toString() + fileExtension;
        Path filePath = uploadPath.resolve(newFilename);

        String fileHashHex = null;
        boolean writeOk = false;
        try {
            // 一次 IO：边写盘边计算哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(file.getInputStream());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(filePath))) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                }
                out.flush();
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            fileHashHex = hex.toString();
            writeOk = true;

            // 目标文件夹归属校验（防越权）
            Folder folder = null;
            if (folderId != null) {
                folder = folderRepository.findByIdAndUserIdAndDeletedFalse(folderId, userId);
                if (folder == null) {
                    throw new com.filemanager.exception.ForbiddenException("目标文件夹不存在或无权限");
                }
        }

            // 创建文件记录（避免依赖 Lombok builder）
            File fileEntity = new File();
            fileEntity.setFilename(newFilename);
            fileEntity.setOriginalFilename(originalFilename);
            fileEntity.setContentType(file.getContentType());
            fileEntity.setSize(size);
            // 确保 Blob：若已存在则删除临时文件；否则将临时文件移动至 Blob 路径
            com.filemanager.entity.Blob blob = blobService.ensureFromTemp(
                    fileHashHex, size, (file.getContentType() == null ? guessContentTypeByName(originalFilename) : file.getContentType()), filePath);

            // 如指定 parentId，则创建新版本；否则创建新文件 + v1
            if (parentId != null) {
                // 计算配额差值
                File parent = getFile(parentId, userId);
                long oldSize = parent.getSize() == null ? 0L : parent.getSize();
                adjustQuotaForNewVersion(userId, oldSize, size);
                // 新版本号
                java.util.Optional<com.filemanager.entity.FileVersion> last = fileVersionRepository.findFirstByFile_IdOrderByVersionNoDesc(parent.getId());
                int nextNo = last.map(com.filemanager.entity.FileVersion::getVersionNo).orElse(0) + 1;
                com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
                v.setFile(parent);
                v.setVersionNo(nextNo);
                v.setBlobHash(blob.getHash());
                v.setSize(size);
                v.setContentType(fileEntity.getContentType());
                v.setCreatedBy(userId);
                v.setCreateTime(LocalDateTime.now());
                v.setComment("upload new version");
                v = fileVersionRepository.save(v);
                parent.setActiveVersion(v);
                parent.setFilePath(blob.getPath());
                parent.setFileHash(blob.getHash());
                parent.setSize(size);
                parent.setUpdateTime(LocalDateTime.now());
                parent = fileRepository.save(parent);
                return parent;
            } else {
                // 新文件 + v1
                fileEntity.setFilePath(blob.getPath());
                fileEntity.setFileHash(blob.getHash());
                fileEntity.setUser(userEntity);
                fileEntity.setFolder(folder);
                fileEntity.setCreateTime(LocalDateTime.now());
                fileEntity.setUpdateTime(LocalDateTime.now());
                fileEntity.setDeleted(false);
                fileEntity.setDownloadCount(0);
                File saved = fileRepository.save(fileEntity);
                com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
                v.setFile(saved);
                v.setVersionNo(1);
                v.setBlobHash(blob.getHash());
                v.setSize(size);
                v.setContentType(saved.getContentType());
                v.setCreatedBy(userId);
                v.setCreateTime(LocalDateTime.now());
                v.setComment("initial upload");
                v = fileVersionRepository.save(v);
                saved.setActiveVersion(v);
                saved = fileRepository.save(saved);
                return saved;
            }
        } catch (Exception e) {
            // 失败兜底：删除落地的物理文件
            try {
                if (filePath != null && Files.exists(filePath)) {
                    Files.deleteIfExists(filePath);
                }
            } catch (Exception ignore) {}
            // 抛出运行时异常以回滚事务（配额预占将回滚）
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("文件上传失败", e);
        }
    }

    // 秒传：根据文件哈希判断是否存在（未删除）
    public java.util.Optional<File> findByHashIfExists(Long userId, String fileHash) {
        if (fileHash == null || fileHash.isBlank() || userId == null) return java.util.Optional.empty();
        return fileRepository.findFirstByFileHashAndUserIdAndDeletedFalse(fileHash, userId);
    }

    // 分片：保存单个分片到 {storagePath}/chunks/user_{uid}/{hash}/{chunkNumber}.part
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPLOAD_CHUNK,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE_CHUNK,
            userId = "#userId",
            resourceName = "#fileHash + ' #' + #chunkNumber",
            description = "'分片上传'"
    )
    public void saveChunk(MultipartFile chunk,
                          Long userId,
                          String fileHash,
                          Integer chunkNumber,
                          Integer totalChunks) throws IOException {
        if (chunk == null || chunk.isEmpty()) throw new IllegalArgumentException("分片不能为空");
        if (fileHash == null || fileHash.isBlank()) throw new IllegalArgumentException("缺少文件哈希");
        if (chunkNumber == null || chunkNumber < 1) throw new IllegalArgumentException("分片序号不合法");
        if (totalChunks == null || totalChunks < 1) throw new IllegalArgumentException("总分片数不合法");

        Path dir = Paths.get(storagePath, "chunks", "user_" + userId, fileHash);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        Path partPath = dir.resolve("chunk_" + chunkNumber + ".part");
        try (InputStream in = new BufferedInputStream(chunk.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(partPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
    }

    // 分片：列出已存在的分片编号（用于断点续传）
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_CHUNK_QUERY,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE_CHUNK,
            userId = "#userId",
            resourceName = "#fileHash",
            description = "'查询分片状态'"
    )
    public java.util.List<Integer> listUploadedChunks(Long userId, String fileHash) throws IOException {
        if (userId == null || fileHash == null || fileHash.isBlank()) return java.util.List.of();
        Path dir = Paths.get(storagePath, "chunks", "user_" + userId, fileHash);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return java.util.List.of();
        java.util.List<Integer> list = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.startsWith("chunk_") && name.endsWith(".part")) {
                    String numStr = name.substring(6, name.length() - 5);
                    try { list.add(Integer.parseInt(numStr)); } catch (NumberFormatException ignore) {}
                }
            });
        }
        list.sort(java.util.Comparator.naturalOrder());
        return list;
    }

    // 分片：合并分片为最终文件并入库
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPLOAD_MERGE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#result?.id",
            resourceName = "#result?.originalFilename",
            description = "'合并分片'"
    )
    public File mergeChunks(Long userId,
                            String fileHash,
                            String originalFilename,
                            Integer totalChunks,
                            Long folderId,
                            Long parentId) throws IOException {
        long start = System.currentTimeMillis();
        if (fileHash == null || fileHash.isBlank()) throw new IllegalArgumentException("缺少文件哈希");
        if (originalFilename == null || originalFilename.isBlank()) throw new IllegalArgumentException("缺少原文件名");
        if (totalChunks == null || totalChunks < 1) throw new IllegalArgumentException("总分片数不合法");

        // 上传策略校验
        if (!systemSettingService.isUploadAllowAll()) {
            String ext = getFileExtension(originalFilename);
            String suffix = (ext == null) ? "" : ext.replaceFirst("^\\.", "").toLowerCase();
            java.util.List<String> allowed = systemSettingService.getAllowedSuffixes();
            if (suffix.isEmpty() || allowed.isEmpty() || !allowed.contains(suffix)) {
                throw new com.filemanager.exception.UploadTypeNotAllowedException("不允许上传该类型文件", allowed);
            }
        }

        Path chunkDir = Paths.get(storagePath, "chunks", "user_" + userId, fileHash);
        if (!Files.exists(chunkDir)) throw new RuntimeException("分片不存在或已过期");

        // 计算总大小，预占配额（并发安全）
        long totalSize = 0L;
        for (int i = 1; i <= totalChunks; i++) {
            Path part = chunkDir.resolve("chunk_" + i + ".part");
            if (!Files.exists(part)) throw new RuntimeException("缺少分片：" + i);
            totalSize += Files.size(part);
        }
        int ok = userRepository.tryUseQuota(userId, totalSize);
        if (ok == 0) throw new RuntimeException("存储空间不足，无法合并该文件");

        // 确保用户目录
        Path uploadPath = Paths.get(storagePath, "user_" + userId);
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        String ext = getFileExtension(originalFilename);
        String newFilename = UUID.randomUUID().toString() + ext;
        Path target = uploadPath.resolve(newFilename);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) { throw new RuntimeException("初始化哈希失败", e); }

        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW))) {
            byte[] buf = new byte[8192];
            for (int i = 1; i <= totalChunks; i++) {
                Path part = chunkDir.resolve("chunk_" + i + ".part");
                try (InputStream in = new BufferedInputStream(Files.newInputStream(part))) {
                    int n; while ((n = in.read(buf)) != -1) { out.write(buf, 0, n); digest.update(buf, 0, n); }
                }
            }
            out.flush();
        } catch (Exception e) {
            try { Files.deleteIfExists(target); } catch (Exception ignore) {}
            throw new RuntimeException("合并分片失败", e);
        }

        // 校验哈希一致
        String mergedHash = toHex(digest.digest());
        if (!mergedHash.equalsIgnoreCase(fileHash)) {
            try { Files.deleteIfExists(target); } catch (Exception ignore) {}
            throw new RuntimeException("文件校验失败，哈希不一致");
        }

        // 文件夹归属校验
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndUserIdAndDeletedFalse(folderId, userId);
            if (folder == null) throw new RuntimeException("目标文件夹不存在或无权限");
        }

        // 创建记录（将合并后的临时文件写入 Blob 并创建版本）
        File fileEntity = new File();
        fileEntity.setFilename(newFilename);
        fileEntity.setOriginalFilename(originalFilename);
        fileEntity.setContentType(guessContentTypeByName(originalFilename));
        fileEntity.setSize(totalSize);
        // 转存到 Blob
        com.filemanager.entity.Blob blob = blobService.ensureFromTemp(mergedHash, totalSize, fileEntity.getContentType(), target);
        if (parentId != null) {
            File parent = getFile(parentId, userId);
            long oldSize = parent.getSize() == null ? 0L : parent.getSize();
            adjustQuotaForNewVersion(userId, oldSize, totalSize);
            // 新版本
            java.util.Optional<com.filemanager.entity.FileVersion> last = fileVersionRepository.findFirstByFile_IdOrderByVersionNoDesc(parent.getId());
            int nextNo = last.map(com.filemanager.entity.FileVersion::getVersionNo).orElse(0) + 1;
            com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
            v.setFile(parent);
            v.setVersionNo(nextNo);
            v.setBlobHash(blob.getHash());
            v.setSize(totalSize);
            v.setContentType(fileEntity.getContentType());
            v.setCreatedBy(userId);
            v.setCreateTime(LocalDateTime.now());
            v.setComment("chunk merged new version");
            v = fileVersionRepository.save(v);
            parent.setActiveVersion(v);
            parent.setFilePath(blob.getPath());
            parent.setFileHash(blob.getHash());
            parent.setSize(totalSize);
            parent.setUpdateTime(LocalDateTime.now());
            File saved = fileRepository.save(parent);
            // 清理分片目录
            try { deleteDirectoryRecursively(chunkDir); } catch (Exception ignore) {}
            return saved;
        } else {
            fileEntity.setFilePath(blob.getPath());
            fileEntity.setFileHash(blob.getHash());
            fileEntity.setUser(userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在")));
            fileEntity.setFolder(folder);
            fileEntity.setCreateTime(LocalDateTime.now());
            fileEntity.setUpdateTime(LocalDateTime.now());
            fileEntity.setDeleted(false);
            fileEntity.setDownloadCount(0);
            File saved = fileRepository.save(fileEntity);
            com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
            v.setFile(saved);
            v.setVersionNo(1);
            v.setBlobHash(blob.getHash());
            v.setSize(totalSize);
            v.setContentType(saved.getContentType());
            v.setCreatedBy(userId);
            v.setCreateTime(LocalDateTime.now());
            v.setComment("chunk merged");
            v = fileVersionRepository.save(v);
            saved.setActiveVersion(v);
            saved = fileRepository.save(saved);
            // 清理分片目录
            try { deleteDirectoryRecursively(chunkDir); } catch (Exception ignore) {}
            return saved;
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        java.nio.file.Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (IOException ignore) {} });
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }

    private String guessContentTypeByName(String name) {
        if (name == null) return "application/octet-stream";
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    // 清理超过保留期的分片目录（单位：小时）
    public int cleanupExpiredChunks(long olderThanHours) {
        if (olderThanHours <= 0) return 0;
        Path chunksRoot = Paths.get(storagePath, "chunks");
        if (!Files.exists(chunksRoot) || !Files.isDirectory(chunksRoot)) return 0;
        long now = System.currentTimeMillis();
        long thresholdMs = olderThanHours * 3600_000L;
        final int[] count = {0};
        try (java.util.stream.Stream<Path> users = Files.list(chunksRoot)) {
            users.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("user_"))
                    .forEach(userDir -> {
                        try (java.util.stream.Stream<Path> hashes = Files.list(userDir)) {
                            hashes.filter(Files::isDirectory).forEach(hashDir -> {
                                long latest = getLatestModifiedMillis(hashDir);
                                if (latest > 0 && (now - latest) > thresholdMs) {
                                    try {
                                        deleteDirectoryRecursively(hashDir);
                                        count[0]++;
                                    } catch (IOException ignore) {}
                                }
                            });
                        } catch (IOException ignore) {}
                    });
        } catch (IOException ignore) {}
        return count[0];
    }

    private long getLatestModifiedMillis(Path dir) {
        long[] latest = {0L};
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(dir)) {
            stream.forEach(p -> {
                try {
                    long m = java.nio.file.Files.getLastModifiedTime(p).toMillis();
                    if (m > latest[0]) latest[0] = m;
                } catch (IOException ignore) {}
            });
        } catch (IOException ignore) {}
        return latest[0];
    }
    
    public File getFile(Long fileId, Long userId) {
        return fileRepository.findByIdAndUserIdAndDeletedFalse(fileId, userId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
    }

    // 下载专用：区分 404 与 403
    public File getFileForDownload(Long fileId, Long userId) {
        java.util.Optional<File> opt = fileRepository.findByIdAndDeletedFalse(fileId);
        if (opt.isEmpty()) {
            throw new com.filemanager.exception.NotFoundException("文件不存在");
        }
        File file = opt.get();
        Long ownerId = (file.getUser() != null ? file.getUser().getId() : null);
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new com.filemanager.exception.ForbiddenException("无权限访问该文件");
        }
        return file;
    }
    
    public List<File> getUserFiles(Long userId, Long folderId) {
        if (folderId == null) {
            return fileRepository.findByUserIdAndFolderIsNullAndDeletedFalseOrderByCreateTimeDesc(userId);
        } else {
            return fileRepository.findByUserIdAndFolderIdAndDeletedFalseOrderByCreateTimeDesc(userId, folderId);
        }
    }
    
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_DELETE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#fileId",
            resourceName = "#result?.originalFilename",
            description = "'删除文件（移至个人回收站）'"
    )
    public void deleteFile(Long fileId, Long userId) {
        long start = System.currentTimeMillis();
        File file = getFile(fileId, userId);
        file.setDeleted(true);
        file.setDeleteTime(LocalDateTime.now());
        fileRepository.save(file);
    }
    
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_RENAME,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#fileId",
            resourceName = "#newName",
            description = "'重命名文件'"
    )
    public File renameFile(Long fileId, Long userId, String newName) {
        File file = getFile(fileId, userId);
        file.setOriginalFilename(newName);
        file.setUpdateTime(LocalDateTime.now());
        return fileRepository.save(file);
    }
    
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_MOVE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#fileId",
            resourceName = "#result?.originalFilename",
            description = "'移动文件'"
    )
    public File moveFile(Long fileId, Long userId, Long targetFolderId) {
        File file = getFile(fileId, userId);

        Folder targetFolder = null;
        if (targetFolderId != null) {
            targetFolder = folderRepository.findByIdAndUserIdAndDeletedFalse(targetFolderId, userId);
            if (targetFolder == null) {
                throw new RuntimeException("目标文件夹不存在");
            }
        }

        file.setFolder(targetFolder);
        file.setUpdateTime(LocalDateTime.now());
        return fileRepository.save(file);
    }
    
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_COPY,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#result?.id",
            resourceName = "#result?.originalFilename",
            description = "'复制文件'"
    )
    public File copyFile(Long fileId, Long userId, Long targetFolderId) {
        File originalFile = getFile(fileId, userId);
        // 配额校验：复制占用同等大小
        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!userEntity.hasEnoughQuota(originalFile.getSize())) {
            throw new RuntimeException("存储空间不足，无法复制该文件");
        }
        
        // 复用或补充 Blob：无论原文件是否在 Blob，副本都引用 Blob
        com.filemanager.entity.Blob blob = blobService.find(originalFile.getFileHash());
        if (blob == null) {
            // 创建临时副本用于落入 Blob，避免移动原文件导致源路径失效
            Path sourcePath = Paths.get(originalFile.getFilePath());
            Path tmpDir = (tempDir != null && !tempDir.isBlank()) ? Paths.get(tempDir) : Paths.get(storagePath, "tmp");
            try { if (!Files.exists(tmpDir)) Files.createDirectories(tmpDir); } catch (IOException ignore) {}
            Path tmp = tmpDir.resolve("copytemp_" + java.util.UUID.randomUUID());
            try { Files.copy(sourcePath, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING); } catch (IOException e) { throw new RuntimeException("文件复制失败", e); }
            String ctype = (originalFile.getContentType() == null || originalFile.getContentType().isBlank())
                    ? guessContentTypeByName(originalFile.getOriginalFilename())
                    : originalFile.getContentType();
            blob = blobService.ensureFromTemp(originalFile.getFileHash(), originalFile.getSize(), ctype, tmp);
        }
        
        // 查找目标文件夹
        Folder targetFolder = null;
        if (targetFolderId != null) {
            targetFolder = folderRepository.findByIdAndUserIdAndDeletedFalse(targetFolderId, userId);
            if (targetFolder == null) {
                throw new RuntimeException("目标文件夹不存在");
            }
        }
        
        // 创建新文件记录
        User user = userEntity;
        
        File newFile = new File();
        // 一致性：filename 统一使用随机名（与物理 Blob 路径无耦合）
        newFile.setFilename(UUID.randomUUID().toString() + getFileExtension(originalFile.getOriginalFilename()));
        newFile.setOriginalFilename(originalFile.getOriginalFilename());
        newFile.setContentType(originalFile.getContentType());
        newFile.setSize(originalFile.getSize());
        newFile.setFilePath(blob.getPath());
        newFile.setFileHash(blob.getHash());
        newFile.setUser(user);
        newFile.setFolder(targetFolder);
        newFile.setCreateTime(LocalDateTime.now());
        newFile.setUpdateTime(LocalDateTime.now());
        newFile.setDeleted(false);
        newFile.setDownloadCount(0);
        
        File saved = fileRepository.save(newFile);
        // 版本记录：v1 指向与原文件相同的 Blob
        try {
            com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
            v.setFile(saved);
            v.setVersionNo(1);
            v.setBlobHash(blob.getHash());
            v.setSize(originalFile.getSize());
            v.setContentType(originalFile.getContentType());
            v.setCreatedBy(userId);
            v.setCreateTime(LocalDateTime.now());
            v.setComment("copy from #" + originalFile.getId());
            v = fileVersionRepository.save(v);
            saved.setActiveVersion(v);
            fileRepository.save(saved);
        } catch (Exception ignore) {}
        // 扣减配额
        user.useQuota(originalFile.getSize());
        userRepository.save(user);
        return saved;
    }
    
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#fileId",
            description = "'下载文件'"
    )
    public Path getFilePath(Long fileId, Long userId) {
        long start = System.currentTimeMillis();
        File file = getFile(fileId, userId);
        
        // 增加下载次数（原子操作）
        try { fileRepository.incrementDownloadCount(file.getId()); } catch (Exception ignore) {}
        
        Path p = Paths.get(file.getFilePath());
        return p;
    }

    // 供控制层调用，用于管理员下载也能口径一致地增加计数
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "@auditSpel.currentUserId()",
            resourceId = "#fileId",
            description = "'下载文件（计数）'"
    )
    public void incrementDownloadCount(Long fileId) {
        try { fileRepository.incrementDownloadCount(fileId); } catch (Exception ignore) {}
    }

    // 下载记录（带用户与版本信息），用于控制器替代手工审计
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#fileId",
            description = "'下载' + (#versionNo != null ? ' 版本 #' + #versionNo : '')"
    )
    public void recordDownload(Long fileId, Long userId, Integer versionNo) {
        try { fileRepository.incrementDownloadCount(fileId); } catch (Exception ignore) {}
    }

    public String getStorageRoot() {
        return storagePath;
    }

    // 全局哈希存在性（跨用户秒传）：命中 Blob 即存在
    public java.util.Optional<com.filemanager.entity.Blob> findBlobByHash(String hash) {
        if (hash == null || hash.isBlank()) return java.util.Optional.empty();
        com.filemanager.entity.Blob b = blobService.find(hash);
        return java.util.Optional.ofNullable(b);
    }

    // 基于已有 Blob 快速创建文件或新版本
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPLOAD_QUICK,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#result?.id",
            resourceName = "#result?.originalFilename",
            description = "#comment ?: 'quick create'"
    )
    public File createOrUpdateFromBlob(Long userId,
                                       Long folderId,
                                       Long parentId,
                                       com.filemanager.entity.Blob blob,
                                       String originalFilename,
                                       String comment) {
        String ctype = (blob.getContentType() == null || blob.getContentType().isBlank())
                ? guessContentTypeByName(originalFilename)
                : blob.getContentType();
        long size = blob.getSize() == null ? 0L : blob.getSize();
        String ext = getFileExtension(originalFilename);
        if (parentId != null) {
            File parent = getFile(parentId, userId);
            long oldSize = parent.getSize() == null ? 0L : parent.getSize();
            adjustQuotaForNewVersion(userId, oldSize, size);
            // 新版本
            java.util.Optional<com.filemanager.entity.FileVersion> last = fileVersionRepository.findFirstByFile_IdOrderByVersionNoDesc(parent.getId());
            int nextNo = last.map(com.filemanager.entity.FileVersion::getVersionNo).orElse(0) + 1;
            com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
            v.setFile(parent);
            v.setVersionNo(nextNo);
            v.setBlobHash(blob.getHash());
            v.setSize(size);
            v.setContentType(ctype);
            v.setCreatedBy(userId);
            v.setCreateTime(LocalDateTime.now());
            v.setComment(comment == null ? "quick create" : comment);
            v = fileVersionRepository.save(v);
            parent.setActiveVersion(v);
            parent.setFilePath(blob.getPath());
            parent.setFileHash(blob.getHash());
            parent.setSize(size);
            parent.setContentType(ctype);
            parent.setUpdateTime(LocalDateTime.now());
            return fileRepository.save(parent);
        } else {
            // 新文件
            int ok = userRepository.tryUseQuota(userId, size);
            if (ok == 0) throw new RuntimeException("存储空间不足，无法创建文件");
            Folder folder = null;
            if (folderId != null) {
                folder = folderRepository.findByIdAndUserIdAndDeletedFalse(folderId, userId);
                if (folder == null) throw new RuntimeException("目标文件夹不存在或无权限");
            }
            String newName = java.util.UUID.randomUUID().toString() + ext;
            File f = new File();
            f.setFilename(newName);
            f.setOriginalFilename(originalFilename);
            f.setContentType(ctype);
            f.setSize(size);
            f.setFilePath(blob.getPath());
            f.setFileHash(blob.getHash());
            f.setUser(userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在")));
            f.setFolder(folder);
            f.setCreateTime(LocalDateTime.now());
            f.setUpdateTime(LocalDateTime.now());
            f.setDeleted(false);
            f.setDownloadCount(0);
            f = fileRepository.save(f);
            com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
            v.setFile(f);
            v.setVersionNo(1);
            v.setBlobHash(blob.getHash());
            v.setSize(size);
            v.setContentType(ctype);
            v.setCreatedBy(userId);
            v.setCreateTime(LocalDateTime.now());
            v.setComment(comment == null ? "quick create" : comment);
            v = fileVersionRepository.save(v);
            f.setActiveVersion(v);
            return fileRepository.save(f);
        }
    }

    // 迁移一批旧文件到 Blob（CAS），并补齐版本记录；返回处理数量
    private final java.util.concurrent.atomic.AtomicLong migrateCursor = new java.util.concurrent.atomic.AtomicLong(0L);

    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_ADMIN_RESTORE,
            resourceType = "MIGRATION",
            userId = "@auditSpel.currentUserId()",
            description = "'手动迁移批次: ' + #limit"
    )
    public int migrateFilesBatch(int limit) {
        long lastId = migrateCursor.get();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit), org.springframework.data.domain.Sort.by("id").ascending());
        org.springframework.data.domain.Page<File> page = fileRepository.findByDeletedFalseAndIdGreaterThanOrderByIdAsc(lastId, pageable);
        int processed = 0;
        long maxId = lastId;
        for (File f : page.getContent()) {
            try {
                boolean isBlob = false;
                try {
                    String p = f.getFilePath();
                    if (p != null) {
                        java.nio.file.Path abs = java.nio.file.Paths.get(p).toAbsolutePath().normalize();
                        java.nio.file.Path root = java.nio.file.Paths.get(storagePath).toAbsolutePath().normalize();
                        isBlob = abs.startsWith(root.resolve("blobs"));
                    }
                } catch (Exception ignore) {}

                if (isBlob) {
                    // 仅补齐版本记录
                    if (f.getActiveVersion() == null) {
                        com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
                        v.setFile(f);
                        v.setVersionNo(1);
                        v.setBlobHash(f.getFileHash());
                        v.setSize(f.getSize());
                        v.setContentType(f.getContentType());
                        v.setCreatedBy(f.getUser() != null ? f.getUser().getId() : 0L);
                        v.setCreateTime(java.time.LocalDateTime.now());
                        v.setComment("migrated v1");
                        v = fileVersionRepository.save(v);
                        f.setActiveVersion(v);
                        fileRepository.save(f);
                        processed++;
                    }
                    maxId = Math.max(maxId, f.getId() == null ? maxId : f.getId());
                    continue;
                }

                // 非 Blob：将文件移动到 Blob 路径
                java.nio.file.Path src = java.nio.file.Paths.get(f.getFilePath());
                if (!java.nio.file.Files.exists(src)) {
                    maxId = Math.max(maxId, f.getId() == null ? maxId : f.getId());
                    continue; // 源缺失，跳过
                }
                String hash = (f.getFileHash() != null && !f.getFileHash().isBlank()) ? f.getFileHash() : calculateFileHash(src);
                long size = (f.getSize() != null ? f.getSize() : java.nio.file.Files.size(src));
                String ctype = (f.getContentType() == null || f.getContentType().isBlank()) ? guessContentTypeByName(f.getOriginalFilename()) : f.getContentType();
                com.filemanager.entity.Blob blob = blobService.ensureFromTemp(hash, size, ctype, src);
                f.setFilePath(blob.getPath());
                f.setFileHash(blob.getHash());
                f.setContentType(ctype);
                f.setSize(size);
                // 补 v1
                com.filemanager.entity.FileVersion v = new com.filemanager.entity.FileVersion();
                v.setFile(f);
                v.setVersionNo(1);
                v.setBlobHash(blob.getHash());
                v.setSize(size);
                v.setContentType(ctype);
                v.setCreatedBy(f.getUser() != null ? f.getUser().getId() : 0L);
                v.setCreateTime(java.time.LocalDateTime.now());
                v.setComment("migrated v1");
                v = fileVersionRepository.save(v);
                f.setActiveVersion(v);
                fileRepository.save(f);
                processed++;
                maxId = Math.max(maxId, f.getId() == null ? maxId : f.getId());
            } catch (Exception ignore) {}
        }
        if (page.isEmpty()) {
            migrateCursor.set(0L); // 重置游标，下一轮从头扫
        } else {
            migrateCursor.set(maxId);
        }
        return processed;
    }

    // 管理员分页查询封装
    public Page<File> listActive(Pageable pageable) { return fileRepository.findByDeletedFalse(pageable); }
    public Page<File> listDeleted(Pageable pageable) { return fileRepository.findByDeletedTrue(pageable); }
    public Page<File> listAll(Pageable pageable) { return (pageable == null) ? Page.empty() :
            ( (org.springframework.data.domain.Page<File>) fileRepository.findByOriginalFilenameContaining("", pageable) ); }
    public Page<File> searchActive(String keyword, Pageable pageable) { return fileRepository.findByOriginalFilenameContainingAndDeletedFalse(keyword, pageable); }
    public Page<File> searchDeleted(String keyword, Pageable pageable) { return fileRepository.findByOriginalFilenameContainingAndDeletedTrue(keyword, pageable); }
    public Page<File> searchAll(String keyword, Pageable pageable) { return fileRepository.findByOriginalFilenameContaining(keyword, pageable); }

    public File getFileByIdForAdmin(Long fileId) {
        return fileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new com.filemanager.exception.NotFoundException("文件不存在"));
    }
    
    // 回收站相关方法
    // 管理员回收站方法
    public List<File> getAllRecycleBinFiles() { return fileRepository.findByDeletedTrueAndOwnerHiddenTrueOrderByDeleteTimeDesc(); }

    public List<File> getAllRecycleBinFiles(java.time.LocalDateTime fromExec,
                                            java.time.LocalDateTime toExec,
                                            Boolean scheduledOnly,
                                            String keyword,
                                            String reason) {
        boolean only = scheduledOnly != null && scheduledOnly;
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String rsn = (reason == null || reason.isBlank()) ? null : reason.trim();
        return fileRepository.findAdminRecycleFiltered(fromExec, toExec, only, kw, rsn);
    }
    
    // 管理员恢复文件
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_ADMIN_RESTORE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#adminId",
            resourceId = "#fileId",
            description = "'管理员恢复到管理员网盘'"
    )
    public void adminRestoreFile(Long fileId, Long adminId) {
        long start = System.currentTimeMillis();
        File file = fileRepository.findByIdAndDeletedTrue(fileId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));

        // 管理员实体与容量校验
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("管理员不存在"));
        if (!admin.hasEnoughQuota(file.getSize())) {
            throw new RuntimeException("管理员存储空间不足，无法恢复该文件");
        }

        // 若历史数据未释放过原属主配额，则先释放，避免原用户继续占用
        Long oldOwnerId = file.getUser() != null ? file.getUser().getId() : null;
        if (oldOwnerId != null && !Boolean.TRUE.equals(file.getQuotaReleased())) {
            userRepository.findById(oldOwnerId).ifPresent(u -> {
                u.releaseQuota(file.getSize());
                userRepository.save(u);
            });
            file.setQuotaReleased(true);
        }

        // 目标目录：管理员用户空间
        Path adminDir = Paths.get(storagePath, "user_" + adminId);
        try {
            if (!Files.exists(adminDir)) {
                Files.createDirectories(adminDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("创建管理员存储目录失败", e);
        }

        // 判断是否为 Blob 路径（CAS）
        boolean isBlob = false;
        try {
            String p = file.getFilePath();
            if (p != null) {
                Path abs = Paths.get(p).toAbsolutePath().normalize();
                Path root = Paths.get(storagePath).toAbsolutePath().normalize();
                // 粗略判断：位于 {storagePath}/blobs 下即视为 Blob
                isBlob = abs.startsWith(root.resolve("blobs"));
            }
        } catch (Exception ignore) {}

        String ext = getFileExtension(file.getOriginalFilename());
        String newName = java.util.UUID.randomUUID().toString() + ext;
        Path dst = adminDir.resolve(newName);
        if (!isBlob) {
            // 旧数据：物理移动
            Path src = Paths.get(file.getFilePath());
            try {
                if (Files.exists(src)) {
                    Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    file.setFilePath(dst.toString());
                } else {
                    throw new RuntimeException("源文件不存在，无法恢复");
                }
            } catch (IOException ioe) {
                throw new RuntimeException("移动文件失败", ioe);
            }
        } else {
            // CAS：不移动物理文件，只变更归属
            // filePath 仍指向 Blob 路径
        }

        // 删除旧缩略图并清空缩略图路径（恢复后按需重新生成）
        try {
            if (file.getThumbnailPath() != null) {
                Files.deleteIfExists(Paths.get(file.getThumbnailPath()));
            }
        } catch (IOException ignore) {}
        file.setThumbnailPath(null);

        // 扣除管理员配额（文件归属转移给管理员）
        admin.useQuota(file.getSize());
        userRepository.save(admin);

        // 更新文件信息为管理员归属 + 清理删除/排期状态
        file.setUser(admin);
        file.setFolder(null);
        file.setFilename(newName);
        file.setDeleted(false);
        file.setDeleteTime(null);
        file.setOwnerHidden(false);
        file.setAdminDeleteScheduled(false);
        file.setAdminDeleteRequestTime(null);
        file.setAdminDeleteExecuteTime(null);
        file.setAdminDeleteReason(null);
        // 标记为未释放（因为现在由管理员占用配额），以匹配后续用户/管理员删除逻辑
        file.setQuotaReleased(false);
        file.setUpdateTime(LocalDateTime.now());
        fileRepository.save(file);
    }
    
    // 管理员：为已删除文件排期彻底删除（进入冷静期）。旧的“立即彻删”改为调度删除。
    public void adminPermanentDeleteFile(Long fileId) {
        adminScheduleDeleteFile(fileId, "管理员删除");
    }

    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_ADMIN_SCHEDULE_DELETE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "@auditSpel.currentUserId()",
            resourceId = "#fileId",
            description = "'管理员排期删除: ' + #reason"
    )
    public LocalDateTime adminScheduleDeleteFile(Long fileId, String reason) {
        File file = fileRepository.findByIdAndDeletedTrue(fileId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));
        file.setAdminDeleteScheduled(true);
        file.setAdminDeleteRequestTime(LocalDateTime.now());
        file.setAdminDeleteReason(reason);
        int days = Math.max(1, systemSettingService.getRetentionDaysOrDefault(adminRetentionDays));
        LocalDateTime execTime = LocalDateTime.now().plusDays(days);
        file.setAdminDeleteExecuteTime(execTime);
        fileRepository.save(file);
        return execTime;
    }
    
    // 注释：清空系统回收站（批量排期删除）旧实现，后端未对外开放对应路由，暂不使用。
    // public void adminEmptyAllRecycleBin() {
    //     List<File> files = fileRepository.findByDeletedTrue();
    //     for (File file : files) {
    //         adminScheduleDeleteFile(file.getId(), "批量清空");
    //     }
    // }

    // 用户回收站：彻底删除（移入系统回收站 + 释放配额，不物理删除）
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_RECYCLE_REMOVE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#fileId",
            description = "'个人回收站彻底删除'"
    )
    public void permanentDeleteFile(Long fileId, Long userId) {
        long start = System.currentTimeMillis();
        File file = fileRepository.findByIdAndUserIdAndDeletedTrue(fileId, userId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));
        file.setOwnerHidden(true);
        // 释放配额（仅一次）
        userRepository.findById(userId).ifPresent(u -> {
            if (!Boolean.TRUE.equals(file.getQuotaReleased())) {
                u.releaseQuota(file.getSize());
                userRepository.save(u);
                file.setQuotaReleased(true);
            }
        });
        file.setUpdateTime(LocalDateTime.now());
        fileRepository.save(file);
    }
    public List<File> getUserRecycleBinFiles(Long userId) {
        return fileRepository.findByUserIdAndDeletedTrueAndOwnerHiddenFalseOrderByDeleteTimeDesc(userId);
    }

    public List<File> searchFiles(Long userId, String keyword) {
        return fileRepository.findByUserIdAndOriginalFilenameContainingAndDeletedFalseOrderByCreateTimeDesc(userId, keyword);
    }

    // 用户回收站：恢复文件（若之前释放过配额，需要回加并校验剩余容量）
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_RESTORE,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            resourceId = "#fileId",
            description = "'恢复文件'"
    )
    public void restoreFile(Long fileId, Long userId) {
        long start = System.currentTimeMillis();
        File file = fileRepository.findByIdAndUserIdAndDeletedTrue(fileId, userId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));

        // 如之前释放过配额，需要回加
        userRepository.findById(userId).ifPresent(u -> {
            if (Boolean.TRUE.equals(file.getQuotaReleased())) {
                if (!u.hasEnoughQuota(file.getSize())) {
                    throw new RuntimeException("存储空间不足，无法恢复该文件");
                }
                u.useQuota(file.getSize());
                userRepository.save(u);
                file.setQuotaReleased(false);
            }
        });

        file.setDeleted(false);
        file.setDeleteTime(null);
        file.setOwnerHidden(false);
        // 清理管理员排期状态
        file.setAdminDeleteScheduled(false);
        file.setAdminDeleteRequestTime(null);
        file.setAdminDeleteExecuteTime(null);
        file.setAdminDeleteReason(null);
        file.setUpdateTime(LocalDateTime.now());
        fileRepository.save(file);
    }

    // 用户回收站：清空回收站（等同批量“彻底删除”：移入系统回收站 + 释放配额，不物理删除）
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_RECYCLE_EMPTY,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "#userId",
            description = "'清空个人回收站'"
    )
    public void emptyRecycleBin(Long userId) {
        long start = System.currentTimeMillis();
        List<File> files = fileRepository.findByUserIdAndDeletedTrueAndOwnerHiddenFalseOrderByDeleteTimeDesc(userId);
        int released = 0;
        for (File file : files) {
            file.setOwnerHidden(true);
            // 释放配额（仅一次）
            userRepository.findById(userId).ifPresent(u -> {
                if (!Boolean.TRUE.equals(file.getQuotaReleased())) {
                    u.releaseQuota(file.getSize());
                    userRepository.save(u);
                    file.setQuotaReleased(true);
                }
            });
            file.setUpdateTime(LocalDateTime.now());
            released++;
        }
        fileRepository.saveAll(files);
    }

    // 管理员：清理到期的排期删除项（物理删除 -> 兜底释放配额 -> 删除记录）
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_ADMIN_PURGE_EXPIRED,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_FILE,
            userId = "@auditSpel.currentUserId()",
            description = "'清理到期文件'"
    )
    public int purgeExpiredScheduledDeletions() {
        List<File> files = fileRepository.findByAdminDeleteScheduledTrueAndAdminDeleteExecuteTimeBefore(LocalDateTime.now());
        int count = 0;
        for (File file : files) {
            // 若为旧数据（非 Blob 路径），尝试物理删除；CAS 下不删物理文件，由 GC 负责
            try {
                boolean isBlob = false;
                try {
                    String p = file.getFilePath();
                    if (p != null) {
                        Path abs = Paths.get(p).toAbsolutePath().normalize();
                        Path root = Paths.get(storagePath).toAbsolutePath().normalize();
                        isBlob = abs.startsWith(root.resolve("blobs"));
                    }
                } catch (Exception ignore) {}
                if (!isBlob) {
                    Files.deleteIfExists(Paths.get(file.getFilePath()));
                    if (file.getThumbnailPath() != null) {
                        Files.deleteIfExists(Paths.get(file.getThumbnailPath()));
                    }
                }
            } catch (IOException e) {
                System.err.println("物理文件删除失败: " + file.getFilePath());
            }
            // 兜底释放配额（兼容历史数据）
            try {
                Long ownerId = file.getUser() != null ? file.getUser().getId() : null;
                if (ownerId != null && !Boolean.TRUE.equals(file.getQuotaReleased())) {
                    userRepository.findById(ownerId).ifPresent(u -> {
                        u.releaseQuota(file.getSize());
                        userRepository.save(u);
                    });
                }
            } catch (Exception ignore) {}

            // 删除版本记录，防止残留引用
            try {
                java.util.List<com.filemanager.entity.FileVersion> vers = fileVersionRepository.findByFile_IdOrderByVersionNoAsc(file.getId());
                if (!vers.isEmpty()) {
                    fileVersionRepository.deleteAll(vers);
                }
            } catch (Exception ignore) {}

            fileRepository.delete(file);
            count++;
        }
        return count;
    }

    // 缩略图：公开获取缩略图路径（若无则生成）——优先 Blob 级缩略图
    public Path getThumbnailPathPublic(Long fileId) {
        File file = fileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        // 仅为图片类型生成缩略图
        if (!isImageType(file.getContentType(), file.getOriginalFilename())) {
            throw new RuntimeException("不支持为该文件生成缩略图");
        }

        // 如果是 Blob 或能找到 Blob，则生成/返回 Blob 级缩略图
        try {
            com.filemanager.entity.Blob blob = blobService.find(file.getFileHash());
            if (blob != null) {
                return ensureBlobThumbnail(blob);
            }
        } catch (Exception ignore) {}

        try {
            return ensureThumbnail(file);
        } catch (IOException e) {
            throw new RuntimeException("生成缩略图失败", e);
        }
    }

    private boolean isImageType(String contentType, String filename) {
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return true;
        }
        String name = filename == null ? "" : filename.toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp");
    }

    private Path ensureThumbnail(File file) throws IOException {
        // 目标目录：{storagePath}/thumbnails/user_{userId}
        Long userId = file.getUser() != null ? file.getUser().getId() : null;
        Path thumbDir = Paths.get(storagePath, "thumbnails", userId != null ? ("user_" + userId) : "common");
        if (!Files.exists(thumbDir)) {
            Files.createDirectories(thumbDir);
        }

        // 已有缩略图则返回（存在性检查）
        if (file.getThumbnailPath() != null) {
            Path existed = Paths.get(file.getThumbnailPath());
            if (Files.exists(existed)) {
                return existed;
            }
        }

        // 读取原图（内存友好：按需子采样）
        Path source = Paths.get(file.getFilePath());
        if (!Files.exists(source)) {
            throw new IOException("源文件不存在");
        }
        long sizeBytes = Files.size(source);
        if (sizeBytes > thumbnailMaxSourceBytes) {
            throw new IOException("原始图片过大，不生成缩略图");
        }

        // 配置 ImageIO 缓存（使用磁盘，降低堆内存占用）
        try {
            ImageIO.setUseCache(imageIoUseCache);
            if (imageIoUseCache && tempDir != null && !tempDir.isBlank()) {
                java.io.File cacheDir = new java.io.File(tempDir);
                if (cacheDir.exists() || cacheDir.mkdirs()) {
                    ImageIO.setCacheDirectory(cacheDir);
                }
            }
        } catch (Exception ignore) {}

        BufferedImage thumbnail;
        try (ImageInputStream iis = ImageIO.createImageInputStream(source.toFile())) {
            if (iis == null) {
                throw new IOException("无法打开图片流");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("不支持的图片格式");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int ow = reader.getWidth(0);
                int oh = reader.getHeight(0);
                long pixels = (long) ow * (long) oh;

                // 计算子采样因子，保证最长边<=thumbnailMaxSide，同时在超大像素下进一步提高因子
                int baseSub = Math.max(1, (int) Math.ceil((double) Math.max(ow, oh) / (double) thumbnailMaxSide));
                if (pixels > thumbnailMaxSourcePixels) {
                    int extra = (int) Math.ceil(Math.sqrt((double) pixels / (double) thumbnailMaxSourcePixels));
                    baseSub = Math.max(baseSub, extra);
                }

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(baseSub, baseSub, 0, 0);
                BufferedImage sampled = reader.read(0, param);
                if (sampled == null) {
                    throw new IOException("无法读取原始图片");
                }

                int sw = sampled.getWidth();
                int sh = sampled.getHeight();
                double scale = Math.min(1.0 * thumbnailMaxSide / Math.max(sw, sh), 1.0);
                int nw = Math.max(1, (int) Math.round(sw * scale));
                int nh = Math.max(1, (int) Math.round(sh * scale));

                if (nw != sw || nh != sh) {
                    thumbnail = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2d = thumbnail.createGraphics();
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.drawImage(sampled, 0, 0, nw, nh, null);
                    g2d.dispose();
                } else {
                    thumbnail = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2d = thumbnail.createGraphics();
                    g2d.drawImage(sampled, 0, 0, null);
                    g2d.dispose();
                }
            } finally {
                reader.dispose();
            }
        } catch (OutOfMemoryError oom) {
            throw new RuntimeException("生成缩略图内存不足", oom);
        }

        Path target = thumbDir.resolve("thumb_" + file.getId() + ".jpg");
        ImageIO.write(thumbnail, "jpg", target.toFile());

        // 更新记录
        file.setThumbnailPath(target.toString());
        file.setUpdateTime(LocalDateTime.now());
        fileRepository.save(file);

        return target;
    }

    // 基于 Blob 生成缩略图：路径 {storagePath}/thumbs/aa/bb/{hash}.jpg
    private Path ensureBlobThumbnail(com.filemanager.entity.Blob blob) throws IOException {
        String h = blob.getHash();
        String d1 = h.substring(0, 2);
        String d2 = h.substring(2, 4);
        Path dir = Paths.get(storagePath, "thumbs", d1, d2);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        Path target = dir.resolve(h + ".jpg");

        // 已存在且记录中有路径则返回
        if (blob.getThumbnailPath() != null) {
            Path p = Paths.get(blob.getThumbnailPath());
            if (Files.exists(p)) return p;
        }
        if (Files.exists(target)) {
            blob.setThumbnailPath(target.toString());
            try { blobRepository.save(blob); } catch (Exception ignore) {}
            return target;
        }

        // 简易并发锁：best-effort（不阻塞）
        Path lock = dir.resolve(h + ".lock");
        boolean locked = false;
        try { java.nio.file.Files.createFile(lock); locked = true; } catch (Exception ignore) {}

        try {
            if (!Files.exists(target)) {
                Path source = Paths.get(blob.getPath());
                if (!Files.exists(source)) throw new IOException("源文件不存在");
                long sizeBytes = Files.size(source);
                if (sizeBytes > thumbnailMaxSourceBytes) throw new IOException("原始图片过大，不生成缩略图");

                BufferedImage thumbnail;
                try (ImageInputStream iis = ImageIO.createImageInputStream(source.toFile())) {
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                    if (readers.hasNext()) {
                        ImageReader reader = readers.next();
                        reader.setInput(iis, true, true);
                        int ow = reader.getWidth(0);
                        int oh = reader.getHeight(0);
                        long pixels = 1L * ow * oh;
                        int baseSub = Math.max(1, (int) Math.ceil((double) Math.max(ow, oh) / (double) thumbnailMaxSide));
                        if (pixels > thumbnailMaxSourcePixels) {
                            int extra = (int) Math.ceil(Math.sqrt((double) pixels / (double) thumbnailMaxSourcePixels));
                            baseSub = Math.max(baseSub, extra);
                        }
                        ImageReadParam param = reader.getDefaultReadParam();
                        param.setSourceSubsampling(baseSub, baseSub, 0, 0);
                        BufferedImage img = reader.read(0, param);
                        int sw = img.getWidth();
                        int sh = img.getHeight();
                        double scale = Math.min(1.0 * thumbnailMaxSide / Math.max(sw, sh), 1.0);
                        int nw = Math.max(1, (int) Math.round(sw * scale));
                        int nh = Math.max(1, (int) Math.round(sh * scale));
                        Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
                        thumbnail = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2d = thumbnail.createGraphics();
                        g2d.drawImage(scaled, 0, 0, null);
                        g2d.dispose();
                        reader.dispose();
                    } else {
                        BufferedImage img = ImageIO.read(source.toFile());
                        int sw = img.getWidth();
                        int sh = img.getHeight();
                        double scale = Math.min(1.0 * thumbnailMaxSide / Math.max(sw, sh), 1.0);
                        int nw = Math.max(1, (int) Math.round(sw * scale));
                        int nh = Math.max(1, (int) Math.round(sh * scale));
                        Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
                        thumbnail = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2d = thumbnail.createGraphics();
                        g2d.drawImage(scaled, 0, 0, null);
                        g2d.dispose();
                    }
                } catch (OutOfMemoryError oom) {
                    throw new RuntimeException("生成缩略图内存不足", oom);
                }
                ImageIO.write(thumbnail, "jpg", target.toFile());
            }
            // 更新 Blob 记录
            try { blob.setThumbnailPath(target.toString()); blobRepository.save(blob); } catch (Exception ignore) {}
            return target;
        } finally {
            if (locked) {
                try { java.nio.file.Files.deleteIfExists(lock); } catch (Exception ignore) {}
            }
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
    
    private String calculateFileHash(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(Files.newInputStream(filePath))) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("文件哈希计算失败", e);
        }
    }
}
