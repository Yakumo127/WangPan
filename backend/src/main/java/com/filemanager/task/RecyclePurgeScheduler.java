package com.filemanager.task;

import com.filemanager.service.FileService;
import com.filemanager.service.SystemSettingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecyclePurgeScheduler {

    private final FileService fileService;
    private final SystemSettingService systemSettingService;

    public RecyclePurgeScheduler(FileService fileService, SystemSettingService systemSettingService) {
        this.fileService = fileService;
        this.systemSettingService = systemSettingService;
    }

    // 每小时执行一次，清理已到期的排期删除文件
    @Scheduled(fixedDelayString = "${recycle.purge.fixed-delay-ms:3600000}")
    public void purgeExpired() {
        try {
            // 在线备份窗口内可冻结管理员清理，避免极端竞态
            boolean frozen = false;
            try { frozen = systemSettingService.getBoolean("backup.admin-purge.freeze", false) || systemSettingService.getAdminPurgeFreezeCount() > 0; } catch (Exception ignore) {}
            if (frozen) return;
            fileService.purgeExpiredScheduledDeletions();
        } catch (Exception ignore) {
            // 忽略清理过程中的异常，防止任务崩溃
        }
    }
}
