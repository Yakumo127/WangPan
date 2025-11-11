package com.filemanager.task;

import com.filemanager.service.FileService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecyclePurgeScheduler {

    private final FileService fileService;

    public RecyclePurgeScheduler(FileService fileService) {
        this.fileService = fileService;
    }

    // 每小时执行一次，清理已到期的排期删除文件
    @Scheduled(fixedDelayString = "${recycle.purge.fixed-delay-ms:3600000}")
    public void purgeExpired() {
        try {
            fileService.purgeExpiredScheduledDeletions();
        } catch (Exception ignore) {
            // 忽略清理过程中的异常，防止任务崩溃
        }
    }
}

