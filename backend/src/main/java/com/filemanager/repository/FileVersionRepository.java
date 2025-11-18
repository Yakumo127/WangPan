package com.filemanager.repository;

import com.filemanager.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileVersionRepository extends JpaRepository<FileVersion, Long> {
    List<FileVersion> findByFile_IdOrderByVersionNoAsc(Long fileId);

    Optional<FileVersion> findFirstByFile_IdOrderByVersionNoDesc(Long fileId);

    @Query("SELECT v FROM FileVersion v WHERE v.file.id = :fileId AND v.versionNo = :ver")
    Optional<FileVersion> findByFileIdAndVersionNo(@Param("fileId") Long fileId, @Param("ver") Integer versionNo);
}
