package com.filemanager.repository;

import com.filemanager.entity.DownloadTokenUsed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloadTokenUsedRepository extends JpaRepository<DownloadTokenUsed, String> {
}

