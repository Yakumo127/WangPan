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
        // 配额校验
        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!userEntity.hasEnoughQuota(file.getSize())) {
            throw new RuntimeException("存储空间不足，无法上传该文件");
        }
        // 创建存储目录
        Path uploadPath = Paths.get(storagePath, "user_" + userId);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // 生成文件名
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String newFilename = UUID.randomUUID().toString() + fileExtension;
        Path filePath = uploadPath.resolve(newFilename);
        
        // 保存文件
        Files.copy(file.getInputStream(), filePath);
        
        // 计算文件哈希
        String fileHash = calculateFileHash(filePath);
        
        // 查找用户（实体）
        User user = userEntity;
        
        // 查找文件夹
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new RuntimeException("文件夹不存在"));
        }
        
        // 创建文件记录（避免依赖 Lombok builder）
        File fileEntity = new File();
        fileEntity.setFilename(newFilename);
        fileEntity.setOriginalFilename(originalFilename);
        fileEntity.setContentType(file.getContentType());
        fileEntity.setSize(file.getSize());
        fileEntity.setFilePath(filePath.toString());
        fileEntity.setFileHash(fileHash);
        fileEntity.setUser(user);
        fileEntity.setFolder(folder);
        fileEntity.setCreateTime(LocalDateTime.now());
        fileEntity.setUpdateTime(LocalDateTime.now());
        fileEntity.setDeleted(false);
        fileEntity.setDownloadCount(0);
        
        File saved = fileRepository.save(fileEntity);
        // 扣减配额
        user.useQuota(file.getSize());
        userRepository.save(user);
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_UPLOAD,
                com.filemanager.entity.UserLog.RESOURCE_FILE, saved.getId(), saved.getOriginalFilename(),
                "上传文件：" + saved.getOriginalFilename() + "（" + saved.getSize() + "字节）",
                System.currentTimeMillis() - start); } catch (Exception ignore) {}
        return saved;
    }
    
    public File getFile(Long fileId, Long userId) {
        return fileRepository.findByIdAndUserIdAndDeletedFalse(fileId, userId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
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
        // 用户删除后立即释放容量（仅一次）
        userRepository.findById(userId).ifPresent(u -> {
            if (!Boolean.TRUE.equals(file.getQuotaReleased())) {
                u.releaseQuota(file.getSize());
                userRepository.save(u);
                file.setQuotaReleased(true);
            }
        });
        fileRepository.save(file);
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_DELETE,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), file.getOriginalFilename(),
                "删除文件（移至回收站）：" + file.getOriginalFilename() + "（释放" + file.getSize() + "字节）",
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
        
        // 增加下载次数
        file.setDownloadCount(file.getDownloadCount() + 1);
        fileRepository.save(file);
        
        Path p = Paths.get(file.getFilePath());
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), file.getOriginalFilename(),
                "下载文件：" + file.getOriginalFilename(), System.currentTimeMillis() - start); } catch (Exception ignore) {}
        return p;
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
                .orElseThrow(() -> new RuntimeException("文件不存在"));
    }
    
    // 回收站相关方法
    // 管理员回收站方法
    public List<File> getAllRecycleBinFiles() {
        return fileRepository.findByDeletedTrueOrderByDeleteTimeDesc();
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
    
    // 保留旧实现但不再对外暴露接口（危险操作）。
    public void adminEmptyAllRecycleBin() {
        List<File> files = fileRepository.findByDeletedTrue();
        for (File file : files) {
            adminScheduleDeleteFile(file.getId(), "批量清空");
        }
    }

    // 用户回收站：彻底删除（对用户隐藏，不物理删除，不改配额）
    public void permanentDeleteFile(Long fileId, Long userId) {
        long start = System.currentTimeMillis();
        File file = fileRepository.findByIdAndUserIdAndDeletedTrue(fileId, userId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));
        file.setOwnerHidden(true);
        file.setUpdateTime(LocalDateTime.now());
        fileRepository.save(file);
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_RECYCLE_REMOVE,
                com.filemanager.entity.UserLog.RESOURCE_FILE, file.getId(), file.getOriginalFilename(),
                "从用户回收站移除：" + file.getOriginalFilename(), System.currentTimeMillis() - start); } catch (Exception ignore) {}
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

    // 用户回收站：清空回收站（仅对用户隐藏，不物理删除，不改配额）
    public void emptyRecycleBin(Long userId) {
        long start = System.currentTimeMillis();
        List<File> files = fileRepository.findByUserIdAndDeletedTrueAndOwnerHiddenFalseOrderByDeleteTimeDesc(userId);
        for (File file : files) {
            file.setOwnerHidden(true);
            file.setUpdateTime(LocalDateTime.now());
        }
        fileRepository.saveAll(files);
        try { auditLogService.logSuccess(userId, com.filemanager.entity.UserLog.ACTION_RECYCLE_EMPTY,
                com.filemanager.entity.UserLog.RESOURCE_FILE, null, null,
                "清空用户回收站（仅隐藏）：共" + files.size() + "项", System.currentTimeMillis() - start); } catch (Exception ignore) {}
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
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);
            
            StringBuilder hexString = new StringBuilder();
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
