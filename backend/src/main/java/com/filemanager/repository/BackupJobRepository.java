package com.filemanager.repository;

import com.filemanager.entity.BackupJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BackupJobRepository extends JpaRepository<BackupJob, Long> {
    Page<BackupJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

