package com.filemanager.task;

import com.filemanager.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlobMigrationScheduler {

    private final FileService fileService;

    @Value("${file.blob.migration.enabled:false}")
    private boolean enabled;

    @Value("${file.blob.migration.batch-size:25}")
    private int batchSize;

    public BlobMigrationScheduler(FileService fileService) {
        this.fileService = fileService;
    }

    // 默认禁用；开启后每 10 分钟迁移一批
    @Scheduled(fixedDelayString = "${file.blob.migration.fixed-delay-ms:600000}")
    public void migrateBatch() {
        if (!enabled) return;
        try { fileService.migrateFilesBatch(batchSize); } catch (Exception ignore) {}
    }
}

