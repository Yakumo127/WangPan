package com.filemanager.task;

import com.filemanager.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChunkCleanupScheduler {

    private final FileService fileService;

    @Value("${file.chunks.retention-hours:72}")
    private long retentionHours;

    public ChunkCleanupScheduler(FileService fileService) {
        this.fileService = fileService;
    }

    // 每12小时执行一次，清理超过保留期的分片目录
    @Scheduled(fixedDelayString = "${file.chunks.cleanup.fixed-delay-ms:43200000}")
    public void cleanup() {
        try {
            fileService.cleanupExpiredChunks(retentionHours);
        } catch (Exception ignore) {}
    }
}

