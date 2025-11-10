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
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;

@Service
@RequiredArgsConstructor
@Transactional
public class FileService {
    
    @Value("${file.storage.path}")
    private String storagePath;
    
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    
    public File uploadFile(MultipartFile file, Long userId, Long folderId) throws IOException {
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
        
        // 创建文件记录
        File fileEntity = File.builder()
                .filename(newFilename)
                .originalFilename(originalFilename)
                .contentType(file.getContentType())
                .size(file.getSize())
                .filePath(filePath.toString())
                .fileHash(fileHash)
                .user(user)
                .folder(folder)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .deleted(false)
                .downloadCount(0)
                .build();
        
        File saved = fileRepository.save(fileEntity);
        // 扣减配额
        user.useQuota(file.getSize());
        userRepository.save(user);
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
        File file = getFile(fileId, userId);
        file.setDeleted(true);
        file.setDeleteTime(LocalDateTime.now());
        fileRepository.save(file);
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
            targetFolder = folderRepository.findById(targetFolderId)
                    .orElseThrow(() -> new RuntimeException("目标文件夹不存在"));
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
            targetFolder = folderRepository.findById(targetFolderId)
                    .orElseThrow(() -> new RuntimeException("目标文件夹不存在"));
        }
        
        // 创建新文件记录
        User user = userEntity;
        
        File newFile = File.builder()
                .filename(targetPath.getFileName().toString())
                .originalFilename(originalFile.getOriginalFilename())
                .contentType(originalFile.getContentType())
                .size(originalFile.getSize())
                .filePath(targetPath.toString())
                .fileHash(originalFile.getFileHash())
                .user(user)
                .folder(targetFolder)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .deleted(false)
                .downloadCount(0)
                .build();
        
        File saved = fileRepository.save(newFile);
        // 扣减配额
        user.useQuota(originalFile.getSize());
        userRepository.save(user);
        return saved;
    }
    
    public Path getFilePath(Long fileId, Long userId) {
        File file = getFile(fileId, userId);
        
        // 增加下载次数
        file.setDownloadCount(file.getDownloadCount() + 1);
        fileRepository.save(file);
        
        return Paths.get(file.getFilePath());
    }
    
    // 回收站相关方法
    // 管理员回收站方法
    public List<File> getAllRecycleBinFiles() {
        return fileRepository.findByDeletedTrueOrderByDeleteTimeDesc();
    }
    
    // 管理员恢复文件
    public void adminRestoreFile(Long fileId) {
        File file = fileRepository.findByIdAndDeletedTrue(fileId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));
        file.setDeleted(false);
        file.setDeleteTime(null);
        file.setUpdateTime(LocalDateTime.now());
        fileRepository.save(file);
    }
    
    // 管理员彻底删除文件
    public void adminPermanentDeleteFile(Long fileId) {
        File file = fileRepository.findByIdAndDeletedTrue(fileId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));
        
        // 删除物理文件
        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
        } catch (IOException e) {
            // 物理文件删除失败，只删除数据库记录
            System.err.println("物理文件删除失败: " + file.getFilePath());
        }
        
        // 释放配额
        try {
            Long ownerId = file.getUser() != null ? file.getUser().getId() : null;
            if (ownerId != null) {
                userRepository.findById(ownerId).ifPresent(u -> {
                    u.releaseQuota(file.getSize());
                    userRepository.save(u);
                });
            }
        } catch (Exception ignore) {}
        fileRepository.delete(file);
    }
    
    // 管理员清空所有回收站
    public void adminEmptyAllRecycleBin() {
        List<File> files = fileRepository.findByDeletedTrue();
        for (File file : files) {
            // 删除物理文件
            try {
                Files.deleteIfExists(Paths.get(file.getFilePath()));
            } catch (IOException e) {
                // 物理文件删除失败，只删除数据库记录
                System.err.println("物理文件删除失败: " + file.getFilePath());
            }
        }
        // 释放配额
        for (File file : files) {
            try {
                Long ownerId = file.getUser() != null ? file.getUser().getId() : null;
                if (ownerId != null) {
                    userRepository.findById(ownerId).ifPresent(u -> {
                        u.releaseQuota(file.getSize());
                        userRepository.save(u);
                    });
                }
            } catch (Exception ignore) {}
        }
        fileRepository.deleteAll(files);
    }

    // 用户回收站：彻底删除文件（包含物理删除）
    public void permanentDeleteFile(Long fileId, Long userId) {
        File file = fileRepository.findByIdAndUserIdAndDeletedTrue(fileId, userId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));

        // 删除物理文件
        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
            if (file.getThumbnailPath() != null) {
                Files.deleteIfExists(Paths.get(file.getThumbnailPath()));
            }
        } catch (IOException e) {
            System.err.println("物理文件删除失败: " + file.getFilePath());
        }

        // 释放配额
        userRepository.findById(userId).ifPresent(u -> {
            u.releaseQuota(file.getSize());
            userRepository.save(u);
        });

        fileRepository.delete(file);
    }
    public List<File> getUserRecycleBinFiles(Long userId) {
        return fileRepository.findByUserIdAndDeletedTrueOrderByDeleteTimeDesc(userId);
    }

    public List<File> searchFiles(Long userId, String keyword) {
        return fileRepository.findByUserIdAndOriginalFilenameContainingAndDeletedFalseOrderByCreateTimeDesc(userId, keyword);
    }

    // 用户回收站：恢复文件
    public void restoreFile(Long fileId, Long userId) {
        File file = fileRepository.findByIdAndUserIdAndDeletedTrue(fileId, userId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));
        file.setDeleted(false);
        file.setDeleteTime(null);
        file.setUpdateTime(LocalDateTime.now());
        fileRepository.save(file);
    }

    // 用户回收站：彻底删除文件（包含物理删除）
    public void permanentDeleteFile(Long fileId, Long userId) {
        File file = fileRepository.findByIdAndUserIdAndDeletedTrue(fileId, userId)
                .orElseThrow(() -> new RuntimeException("回收站中不存在该文件"));

        // 删除物理文件
        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
            if (file.getThumbnailPath() != null) {
                Files.deleteIfExists(Paths.get(file.getThumbnailPath()));
            }
        } catch (IOException e) {
            System.err.println("物理文件删除失败: " + file.getFilePath());
        }

        fileRepository.delete(file);
    }

    // 用户回收站：清空回收站
    public void emptyRecycleBin(Long userId) {
        List<File> files = fileRepository.findByUserIdAndDeletedTrueOrderByDeleteTimeDesc(userId);
        for (File file : files) {
            try {
                Files.deleteIfExists(Paths.get(file.getFilePath()));
                if (file.getThumbnailPath() != null) {
                    Files.deleteIfExists(Paths.get(file.getThumbnailPath()));
                }
            } catch (IOException e) {
                System.err.println("物理文件删除失败: " + file.getFilePath());
            }
        }
        fileRepository.deleteAll(files);
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

        // 读取原图
        Path source = Paths.get(file.getFilePath());
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) {
            throw new IOException("无法读取原始图片");
        }

        // 计算缩放尺寸（最长边 256）
        int maxSize = 256;
        int ow = original.getWidth();
        int oh = original.getHeight();
        double scale = Math.min(1.0 * maxSize / Math.max(ow, oh), 1.0);
        int nw = Math.max(1, (int) Math.round(ow * scale));
        int nh = Math.max(1, (int) Math.round(oh * scale));

        Image scaled = original.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        BufferedImage thumbnail = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = thumbnail.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();

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
