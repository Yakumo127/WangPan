package com.filemanager.repository;

import com.filemanager.entity.Share;
import com.filemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {

    @Query("SELECT s FROM Share s WHERE s.owner = :owner ORDER BY s.createdAt DESC")
    List<Share> findByOwner(@Param("owner") User owner);

    @Query("SELECT s FROM Share s WHERE s.owner = :owner")
    Page<Share> findByOwner(@Param("owner") User owner, Pageable pageable);

    @Query("SELECT s FROM Share s WHERE s.id = :id AND s.owner = :owner")
    Optional<Share> findByIdAndOwner(@Param("id") Long id, @Param("owner") User owner);

    @Query("SELECT s FROM Share s WHERE s.id = :id AND s.status = com.filemanager.entity.Share.Status.ACTIVE")
    Optional<Share> findActiveById(@Param("id") Long id);

    @Query("SELECT s FROM Share s WHERE s.id = :id AND s.status = com.filemanager.entity.Share.Status.ACTIVE AND (s.expireTime IS NULL OR s.expireTime > :now)")
    Optional<Share> findActiveAndNotExpired(@Param("id") Long id, @Param("now") LocalDateTime now);

    long countByOwner(@Param("owner") User owner);

    long countByOwnerAndStatus(@Param("owner") User owner, @Param("status") Share.Status status);

    @Query("SELECT COALESCE(SUM(s.viewCount),0) FROM Share s WHERE s.owner = :owner")
    Long sumViewCountByOwner(@Param("owner") User owner);

    @Query("SELECT COALESCE(SUM(s.downloadCount),0) FROM Share s WHERE s.owner = :owner")
    Long sumDownloadCountByOwner(@Param("owner") User owner);
}
