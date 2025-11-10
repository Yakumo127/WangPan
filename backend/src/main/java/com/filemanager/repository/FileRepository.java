package com.filemanager.repository;

import com.filemanager.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {

    @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.folder IS NULL AND f.deleted = false ORDER BY f.createTime DESC")
    List<File> findByUserIdAndFolderIsNullAndDeletedFalseOrderByCreateTimeDesc(@Param("userId") Long userId);

    @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.folder.id = :folderId AND f.deleted = false ORDER BY f.createTime DESC")
    List<File> findByUserIdAndFolderIdAndDeletedFalseOrderByCreateTimeDesc(@Param("userId") Long userId, @Param("folderId") Long folderId);

    @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.deleted = false AND f.originalFilename LIKE %:keyword% ORDER BY f.createTime DESC")
    List<File> findByUserIdAndOriginalFilenameContainingAndDeletedFalseOrderByCreateTimeDesc(@Param("userId") Long userId, @Param("keyword") String keyword);

    @Query("SELECT f FROM File f WHERE f.id = :fileId AND f.user.id = :userId AND f.deleted = false")
    Optional<File> findByIdAndUserIdAndDeletedFalse(@Param("fileId") Long fileId, @Param("userId") Long userId);

    @Query("SELECT f FROM File f WHERE f.user.id = :userId AND f.deleted = true ORDER BY f.deleteTime DESC")
    List<File> findByUserIdAndDeletedTrueOrderByDeleteTimeDesc(@Param("userId") Long userId);

    @Query("SELECT f FROM File f WHERE f.id = :fileId AND f.user.id = :userId AND f.deleted = true")
    Optional<File> findByIdAndUserIdAndDeletedTrue(@Param("fileId") Long fileId, @Param("userId") Long userId);

    List<File> findByDeletedTrue();
    List<File> findByDeletedTrueOrderByDeleteTimeDesc();

    Optional<File> findByIdAndDeletedTrue(Long fileId);

    // 按ID查询非删除文件（用于公开缩略图等场景）
    Optional<File> findByIdAndDeletedFalse(Long fileId);

    // 管理员分页查询（全量）
    Page<File> findByDeletedFalse(Pageable pageable);
    Page<File> findByDeletedTrue(Pageable pageable);
    Page<File> findByOriginalFilenameContainingAndDeletedFalse(String keyword, Pageable pageable);
    Page<File> findByOriginalFilenameContainingAndDeletedTrue(String keyword, Pageable pageable);
    Page<File> findByOriginalFilenameContaining(String keyword, Pageable pageable);
}
