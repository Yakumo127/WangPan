package com.filemanager.repository;

import com.filemanager.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    
    @Query("SELECT f FROM Folder f WHERE f.user.id = :userId AND f.parent IS NULL AND f.deleted = false ORDER BY f.createTime DESC")
    List<Folder> findByUserIdAndParentIsNullAndDeletedFalseOrderByCreateTimeDesc(@Param("userId") Long userId);
    
    @Query("SELECT f FROM Folder f WHERE f.user.id = :userId AND f.parent.id = :parentId AND f.deleted = false ORDER BY f.createTime DESC")
    List<Folder> findByUserIdAndParentIdAndDeletedFalseOrderByCreateTimeDesc(@Param("userId") Long userId, @Param("parentId") Long parentId);
    
    @Query("SELECT COUNT(f) > 0 FROM Folder f WHERE f.name = :name AND f.user.id = :userId AND f.parent.id = :parentId AND f.deleted = false")
    boolean existsByNameAndUserIdAndParentIdAndDeletedFalse(@Param("name") String name, @Param("userId") Long userId, @Param("parentId") Long parentId);
    
    @Query("SELECT f FROM Folder f WHERE f.id = :folderId AND f.user.id = :userId AND f.deleted = false")
    Folder findByIdAndUserIdAndDeletedFalse(Long folderId, Long userId);
    
    @Query("SELECT f FROM Folder f WHERE f.parent.id = :parentId AND f.deleted = false ORDER BY f.createTime DESC")
    List<Folder> findByParentIdAndDeletedFalseOrderByCreateTimeDesc(Long parentId);
    
    @Query("SELECT f FROM Folder f WHERE f.id = :folderId")
    Folder findFolderById(Long folderId);
    
    // 简化的路径查询方法
    @Query("SELECT f FROM Folder f WHERE f.id = :folderId AND f.deleted = false")
    Folder findPathToRoot(Long folderId);
}