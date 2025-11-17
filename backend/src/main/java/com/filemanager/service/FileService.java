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
    
    public File uploadFile(MultipartFile file, Long userId, Long folderId) throws IOException {
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
                String allowStr = String.join(",", allowed);
                throw new RuntimeException(allowed.isEmpty()
                        ? "当前未配置允许的后缀，已禁止上传，请联系管理员"
                        : (suffix.isEmpty() ? "不允许上传无后缀文件" : ("不允许上传该类型文件（仅允许：" + allowStr + ")")));
            }
        }

        // 原子配额扣减（并发安全）——在写盘前预占额度，失败将随事务回滚
        long size = file.getSize();
        if (size < 0) size = 0; // 理论上不会，但避免异常值
        int updated = userRepository.tryUseQuota(userId, size);
        if (updated == 0) {
            throw new RuntimeException("存储空间不足，无法上传该文件");
        }

        // 创建存储目录
        Path uploadPath = Paths.get(storagePath, "user_" + userId);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成文件名与路径
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
                    throw new RuntimeException("目标文件夹不存在或无权限");
                }
            }

            // 创建文件记录（避免依赖 Lombok builder）
            File fileEntity = new File();
            fileEntity.setFilename(newFilename);
            fileEntity.setOriginalFilename(originalFilename);
            fileEntity.setContentType(file.getContentType());
            fileEntity.setSize(size);
            fileEntity.setFilePath(filePath.toString());
            fileEntity.setFileHash(fileHashHex);
            fileEntity.setUser(userEntity);
            fileEntity.setFolder(folder);
            fileEntity.setCreateTime(LocalDateTime.now());
            fileEntity.setUpdateTime(LocalDateTime.now());
            fileEntity.setDeleted(false);
            fileEntity.setDownloadCount(0);

            File saved = fileRepository.save(fileEntity);
            try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_UPLOAD,
                    com.filemanager.entity.UserLog.RESOURCE_FILE, saved.getId(), saved.getOriginalFilename(),
                    "上传文件：" + saved.getOriginalFilename() + "（" + saved.getSize() + "字节）",
                    System.currentTimeMillis() - start); } catch (Exception ignore) {}
            return saved;
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
    public File mergeChunks(Long userId,
                            String fileHash,
                            String originalFilename,
                            Integer totalChunks,
                            Long folderId) throws IOException {
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
                String allowStr = String.join(",", allowed);
                throw new RuntimeException(allowed.isEmpty()
                        ? "当前未配置允许的后缀，已禁止上传，请联系管理员"
                        : (suffix.isEmpty() ? "不允许上传无后缀文件" : ("不允许上传该类型文件（仅允许：" + allowStr + ")")));
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

        // 创建记录
        File fileEntity = new File();
        fileEntity.setFilename(newFilename);
        fileEntity.setOriginalFilename(originalFilename);
        fileEntity.setContentType(guessContentTypeByName(originalFilename));
        fileEntity.setSize(totalSize);
        fileEntity.setFilePath(target.toString());
        fileEntity.setFileHash(mergedHash);
        fileEntity.setUser(userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在")));
        fileEntity.setFolder(folder);
        fileEntity.setCreateTime(LocalDateTime.now());
        fileEntity.setUpdateTime(LocalDateTime.now());
        fileEntity.setDeleted(false);
        fileEntity.setDownloadCount(0);
        File saved = fileRepository.save(fileEntity);

        // 清理分片目录
        try { deleteDirectoryRecursively(chunkDir); } catch (Exception ignore) {}

        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_UPLOAD,
                com.filemanager.entity.UserLog.RESOURCE_FILE, saved.getId(), saved.getOriginalFilename(),
                "合并上传文件：" + saved.getOriginalFilename() + "（" + saved.getSize() + "字节）",
                System.currentTimeMillis() - start); } catch (Exception ignore) {}
        return saved;
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
    
    public void deleteFile(Long fileId, Long userId) {
        long start = System.currentTimeMillis();
        File file = getFile(fileId, userId);
        file.setDeleted(true);
        file.setDeleteTime(LocalDateTime.now());
        fileRepository.save(file);
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_DELETE,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), file.getOriginalFilename(),
                "删除文件（移至个人回收站）：" + file.getOriginalFilename(),
                System.currentTimeMillis() - start); } catch (Exception ignore) {}
    }
    
    public File renameFile(Long fileId, Long userId, String newName) {
        File file = getFile(fileId, userId);
        file.setOriginalFilename(newName);
        file.setUpdateTime(LocalDateTime.now());
        return fileRepository.save(file);
    }
    
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
    
    public File copyFile(Long fileId, Long userId, Long targetFolderId) {
        File originalFile = getFile(fileId, userId);
        // 配额校验：复制占用同等大小
        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!userEntity.hasEnoughQuota(originalFile.getSize())) {
            throw new RuntimeException("存储空间不足，无法复制该文件");
        }
        
        // 复制文件
        Path sourcePath = Paths.get(originalFile.getFilePath());
        Path targetPath = Paths.get(storagePath, "user_" + userId, UUID.randomUUID().toString() + getFileExtension(originalFile.getOriginalFilename()));
        
        try {
            Files.copy(sourcePath, targetPath);
        } catch (IOException e) {
            throw new RuntimeException("文件复制失败", e);
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
        newFile.setFilename(targetPath.getFileName().toString());
        newFile.setOriginalFilename(originalFile.getOriginalFilename());
        newFile.setContentType(originalFile.getContentType());
        newFile.setSize(originalFile.getSize());
        newFile.setFilePath(targetPath.toString());
        newFile.setFileHash(originalFile.getFileHash());
        newFile.setUser(user);
        newFile.setFolder(targetFolder);
        newFile.setCreateTime(LocalDateTime.now());
        newFile.setUpdateTime(LocalDateTime.now());
        newFile.setDeleted(false);
        newFile.setDownloadCount(0);
        
        File saved = fileRepository.save(newFile);
        // 扣减配额
        user.useQuota(originalFile.getSize());
        userRepository.save(user);
        return saved;
    }
    
    public Path getFilePath(Long fileId, Long userId) {
        long start = System.currentTimeMillis();
        File file = getFile(fileId, userId);
        
        // 增加下载次数（原子操作）
        try { fileRepository.incrementDownloadCount(file.getId()); } catch (Exception ignore) {}
        
        Path p = Paths.get(file.getFilePath());
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), file.getOriginalFilename(),
                "下载文件：" + file.getOriginalFilename(), System.currentTimeMillis() - start); } catch (Exception ignore) {}
        return p;
    }

    // 供控制层调用，用于管理员下载也能口径一致地增加计数
    public void incrementDownloadCount(Long fileId) {
        try { fileRepository.incrementDownloadCount(fileId); } catch (Exception ignore) {}
    }

    public String getStorageRoot() {
        return storagePath;
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

        // 生成新文件名并移动物理文件
        String ext = getFileExtension(file.getOriginalFilename());
        String newName = java.util.UUID.randomUUID().toString() + ext;
        Path src = Paths.get(file.getFilePath());
        Path dst = adminDir.resolve(newName);
        try {
            if (Files.exists(src)) {
                Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException("源文件不存在，无法恢复");
            }
        } catch (IOException ioe) {
            throw new RuntimeException("移动文件失败", ioe);
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
        file.setFilePath(dst.toString());
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
        try { auditLogService.logSuccess(adminId, com.filemanager.entity.UserLog.ACTION_ADMIN_RESTORE,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), file.getOriginalFilename(),
                "管理员恢复到管理员网盘：" + file.getOriginalFilename(), System.currentTimeMillis() - start); } catch (Exception ignore) {}
    }
    
    // 管理员：为已删除文件排期彻底删除（进入冷静期）。旧的“立即彻删”改为调度删除。
    public void adminPermanentDeleteFile(Long fileId) {
        adminScheduleDeleteFile(fileId, "管理员删除");
    }

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
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_RECYCLE_REMOVE,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), file.getOriginalFilename(),
                "个人回收站彻底删除：" + file.getOriginalFilename() + "（配额已释放，移入系统回收站）", System.currentTimeMillis() - start); } catch (Exception ignore) {}
    }
    public List<File> getUserRecycleBinFiles(Long userId) {
        return fileRepository.findByUserIdAndDeletedTrueAndOwnerHiddenFalseOrderByDeleteTimeDesc(userId);
    }

    public List<File> searchFiles(Long userId, String keyword) {
        return fileRepository.findByUserIdAndOriginalFilenameContainingAndDeletedFalseOrderByCreateTimeDesc(userId, keyword);
    }

    // 用户回收站：恢复文件（若之前释放过配额，需要回加并校验剩余容量）
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
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_RESTORE,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), file.getOriginalFilename(),
                "恢复文件：" + file.getOriginalFilename(), System.currentTimeMillis() - start); } catch (Exception ignore) {}
    }

    // 用户回收站：清空回收站（等同批量“彻底删除”：移入系统回收站 + 释放配额，不物理删除）
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
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_RECYCLE_EMPTY,
                com.filemanager.entity.UserLog.RESOURCE_FILE, null, null,
                "清空个人回收站（彻底删除并移入系统回收站）：共" + released + "项", System.currentTimeMillis() - start); } catch (Exception ignore) {}
    }

    // 管理员：清理到期的排期删除项（物理删除 -> 兜底释放配额 -> 删除记录）
    public int purgeExpiredScheduledDeletions() {
        List<File> files = fileRepository.findByAdminDeleteScheduledTrueAndAdminDeleteExecuteTimeBefore(LocalDateTime.now());
        int count = 0;
        for (File file : files) {
            try {
                Files.deleteIfExists(Paths.get(file.getFilePath()));
                if (file.getThumbnailPath() != null) {
                    Files.deleteIfExists(Paths.get(file.getThumbnailPath()));
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

            fileRepository.delete(file);
            count++;
        }
        return count;
    }

    // 缩略图：公开获取缩略图路径（若无则生成）
    public Path getThumbnailPathPublic(Long fileId) {
        File file = fileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        // 仅为图片类型生成缩略图
        if (!isImageType(file.getContentType(), file.getOriginalFilename())) {
            throw new RuntimeException("不支持为该文件生成缩略图");
        }

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
