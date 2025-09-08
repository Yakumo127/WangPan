package com.filemanager.repository;

import com.filemanager.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {
    
    List<File> findByUserIdAndFolderIsNullAndDeletedFalseOrderByCreateTimeDesc(Long userId);
    
    List<File> findByUserIdAndFolderIdAndDeletedFalseOrderByCreateTimeDesc(Long userId, Long folderId);
    
    List<File> findByUserIdAndOriginalFilenameContainingAndDeletedFalseOrderByCreateTimeDesc(Long userId, String keyword);
    
    Optional<File> findByIdAndUserIdAndDeletedFalse(Long fileId, Long userId);
    
    List<File> findByUserIdAndDeletedTrueOrderByDeleteTimeDesc(Long userId);
    
    Optional<File> findByIdAndUserIdAndDeletedTrue(Long fileId, Long userId);
    
    List<File> findByDeletedTrue();
    List<File> findByDeletedTrueOrderByDeleteTimeDesc();
    
    Optional<File> findByIdAndDeletedTrue(Long fileId);
}
