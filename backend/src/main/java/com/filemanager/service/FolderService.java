package com.filemanager.service;

import com.filemanager.entity.Folder;
import com.filemanager.entity.File;
import com.filemanager.entity.User;
import com.filemanager.repository.FolderRepository;
import com.filemanager.repository.FileRepository;
import org.springframework.context.annotation.Lazy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FolderService {
    
    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final @Lazy FileService fileService;
    
    public Folder createFolder(String name, Long parentId, Long userId) {
        // 检查文件夹名称是否已存在
        if (folderRepository.existsByNameAndUserIdAndParentIdAndDeletedFalse(name, userId, parentId)) {
            throw new RuntimeException("文件夹名称已存在");
        }
        
        // 查找用户
        User user = new User();
        user.setId(userId);
        
        // 查找父文件夹
        Folder parentFolder = null;
        if (parentId != null) {
            parentFolder = folderRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("父文件夹不存在"));
        }
        
        // 创建文件夹
        Folder folder = new Folder();
        folder.setName(name);
        folder.setUser(user);
        folder.setParent(parentFolder);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());
        folder.setDeleted(false);
        
        return folderRepository.save(folder);
    }
    
    public Folder getFolder(Long folderId, Long userId) {
        return folderRepository.findById(folderId)
                .filter(folder -> folder.getUserId().equals(userId) && !folder.getDeleted())
                .orElseThrow(() -> new RuntimeException("文件夹不存在"));
    }
    
    public List<Folder> getUserFolders(Long userId, Long parentId) {
        if (parentId == null) {
            return folderRepository.findByUserIdAndParentIsNullAndDeletedFalseOrderByCreateTimeDesc(userId);
        } else {
            return folderRepository.findByUserIdAndParentIdAndDeletedFalseOrderByCreateTimeDesc(userId, parentId);
        }
    }
    
    public void deleteFolder(Long folderId, Long userId) {
        Folder folder = getFolder(folderId, userId);
        folder.setDeleted(true);
        folder.setDeleteTime(LocalDateTime.now());
        folderRepository.save(folder);
    }
    
    public Folder renameFolder(Long folderId, Long userId, String newName) {
        Folder folder = getFolder(folderId, userId);
        
        // 检查新名称是否已存在
        if (folderRepository.existsByNameAndUserIdAndParentIdAndDeletedFalse(newName, userId, folder.getParent() != null ? folder.getParent().getId() : null)) {
            throw new RuntimeException("文件夹名称已存在");
        }
        
        folder.setName(newName);
        folder.setUpdateTime(LocalDateTime.now());
        return folderRepository.save(folder);
    }
    
    public Folder moveFolder(Long folderId, Long userId, Long targetParentId, String newName) {
        Folder folder = getFolder(folderId, userId);

        // 检查目标父文件夹
        Folder targetParent = null;
        if (targetParentId != null) {
            targetParent = folderRepository.findByIdAndUserIdAndDeletedFalse(targetParentId, userId);
            if (targetParent == null) {
                throw new RuntimeException("目标文件夹不存在");
            }

            // 检查不能移动到自己的子文件夹
            if (isDescendant(folderId, targetParentId)) {
                throw new RuntimeException("不能移动到自己的子文件夹");
            }
        }

        String targetName = (newName == null || newName.isBlank()) ? folder.getName() : newName;
        Long currentParentId = folder.getParent() != null ? folder.getParent().getId() : null;
        boolean samePlace = (targetParentId == null ? currentParentId == null : targetParentId.equals(currentParentId))
                && targetName.equals(folder.getName());
        if (!samePlace && folderRepository.existsByNameAndUserIdAndParentIdAndDeletedFalseExceptId(targetName, userId, targetParentId, folderId)) {
            throw new RuntimeException("目标目录已存在同名文件夹");
        }

        folder.setName(targetName);
        folder.setParent(targetParent);
        folder.setUpdateTime(LocalDateTime.now());
        return folderRepository.save(folder);
    }
    
    public List<Folder> getFolderPath(Long folderId, Long userId) {
        Folder folder = getFolder(folderId, userId);
        // 简化版本，只返回当前文件夹
        return List.of(folder);
    }
    
    private boolean isDescendant(Long folderId, Long ancestorId) {
        if (folderId.equals(ancestorId)) return true;
        Folder ancestor = folderRepository.findFolderById(ancestorId);
        while (ancestor != null && ancestor.getParent() != null) {
            if (ancestor.getParent().getId().equals(folderId)) {
                return true;
            }
            ancestor = ancestor.getParent();
        }
        return false;
    }

    public Folder copyFolder(Long folderId, Long userId, Long targetParentId, String newName) {
        Folder source = getFolder(folderId, userId);

        if (targetParentId != null && isDescendant(folderId, targetParentId)) {
            throw new RuntimeException("不能复制到自己的子文件夹");
        }

        Folder targetParent = null;
        if (targetParentId != null) {
            targetParent = folderRepository.findByIdAndUserIdAndDeletedFalse(targetParentId, userId);
            if (targetParent == null) {
                throw new RuntimeException("目标文件夹不存在");
            }
        }

        String targetName = (newName == null || newName.isBlank()) ? source.getName() : newName;
        if (folderRepository.existsByNameAndUserIdAndParentIdAndDeletedFalse(targetName, userId, targetParentId)) {
            throw new RuntimeException("目标目录已存在同名文件夹");
        }

        Folder copied = new Folder();
        copied.setName(targetName);
        copied.setParent(targetParent);
        copied.setUser(source.getUser());
        copied.setDeleted(false);
        copied.setIsRoot(false);
        copied.setCreateTime(LocalDateTime.now());
        copied.setUpdateTime(LocalDateTime.now());
        Folder saved = folderRepository.save(copied);

        // 复制子文件
        List<File> files = fileRepository.findByUserIdAndFolderIdAndDeletedFalse(source.getUserId(), source.getId());
        if (files != null) {
            for (File f : files) {
                fileService.copyFile(f.getId(), userId, saved.getId(), f.getOriginalFilename());
            }
        }

        // 递归复制子文件夹
        List<Folder> children = folderRepository.findByParentIdAndDeletedFalseOrderByCreateTimeDesc(source.getId());
        if (children != null) {
            for (Folder child : children) {
                copyFolder(child.getId(), userId, saved.getId(), child.getName());
            }
        }

        return saved;
    }
}
