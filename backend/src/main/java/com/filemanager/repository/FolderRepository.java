package com.filemanager.repository;

import com.filemanager.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {

    @Query("SELECT f FROM Folder f WHERE f.user.id = :userId AND f.parent IS NULL AND f.deleted = false ORDER BY f.createTime DESC")
    List<Folder> findByUserIdAndParentIsNullAndDeletedFalseOrderByCreateTimeDesc(@Param("userId") Long userId);

    @Query("SELECT f FROM Folder f WHERE f.user.id = :userId AND f.parent.id = :parentId AND f.deleted = false ORDER BY f.createTime DESC")
    List<Folder> findByUserIdAndParentIdAndDeletedFalseOrderByCreateTimeDesc(@Param("userId") Long userId, @Param("parentId") Long parentId);

    // 支持 parentId = null 的重名校验
    @Query("SELECT COUNT(f) > 0 FROM Folder f " +
           "WHERE f.name = :name AND f.user.id = :userId AND f.deleted = false " +
           "AND ((:parentId IS NULL AND f.parent IS NULL) OR (f.parent.id = :parentId))")
    boolean existsByNameAndUserIdAndParentIdAndDeletedFalse(@Param("name") String name,
                                                            @Param("userId") Long userId,
                                                            @Param("parentId") Long parentId);

    @Query("SELECT f FROM Folder f WHERE f.id = :folderId AND f.user.id = :userId AND f.deleted = false")
    Folder findByIdAndUserIdAndDeletedFalse(@Param("folderId") Long folderId, @Param("userId") Long userId);

    @Query("SELECT f FROM Folder f WHERE f.parent.id = :parentId AND f.deleted = false ORDER BY f.createTime DESC")
    List<Folder> findByParentIdAndDeletedFalseOrderByCreateTimeDesc(@Param("parentId") Long parentId);

    @Query("SELECT f FROM Folder f WHERE f.id = :folderId")
    Folder findFolderById(@Param("folderId") Long folderId);

    // 简化的路径查询方法
    @Query("SELECT f FROM Folder f WHERE f.id = :folderId AND f.deleted = false")
    Folder findPathToRoot(@Param("folderId") Long folderId);

    @Query("SELECT COUNT(f) > 0 FROM Folder f WHERE f.name = :name AND f.user.id = :userId AND f.deleted = false " +
           "AND ((:parentId IS NULL AND f.parent IS NULL) OR (f.parent.id = :parentId)) " +
           "AND (:excludeId IS NULL OR f.id <> :excludeId)")
    boolean existsByNameAndUserIdAndParentIdAndDeletedFalseExceptId(@Param("name") String name,
                                                                    @Param("userId") Long userId,
                                                                    @Param("parentId") Long parentId,
                                                                    @Param("excludeId") Long excludeId);

    long countByUser_IdAndDeletedFalse(Long userId);
}
