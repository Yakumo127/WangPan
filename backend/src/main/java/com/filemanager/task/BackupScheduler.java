package com.filemanager.task;

import com.filemanager.service.BackupService;
import com.filemanager.service.SystemSettingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduler {

    private final BackupService backupService;
    private final SystemSettingService systemSettingService;

    public BackupScheduler(BackupService backupService, SystemSettingService systemSettingService) {
        this.backupService = backupService;
        this.systemSettingService = systemSettingService;
    }

    // 使用 CRON 触发；若未启用则立即返回
    @Scheduled(cron = "${backup.schedule.cron:0 0 2 * * ?}")
    public void scheduledBackup() {
        try {
            if (!systemSettingService.isBackupScheduleEnabled()) return;
            java.util.List<String> dests = systemSettingService.getBackupScheduleDestDirs();
            if (dests == null || dests.isEmpty()) return;
            String format = systemSettingService.getBackupScheduleFormatOrDefault("json");
            String mode = systemSettingService.getBackupScheduleModeOrDefault("online");
            boolean includeThumbs = systemSettingService.getBackupScheduleIncludeThumbnails(true);
            for (String d : dests) {
                try {
                    backupService.exportToServer(d, format, includeThumbs, mode);
                } catch (Exception ignore) {}
            }
            // 保留策略（按天）
            int days = systemSettingService.getBackupRetentionDaysOrDefault(14);
            if (days > 0) {
                for (String d : dests) { try { cleanupOldBackups(d, days); } catch (Exception ignore) {} }
            }
        } catch (Exception ignore) {}
    }

    private void cleanupOldBackups(String dir, int retentionDays) throws Exception {
        java.nio.file.Path base = java.nio.file.Path.of(dir).toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(base)) return;
        long now = System.currentTimeMillis();
        long keepBefore = now - retentionDays * 24L * 3600L * 1000L;
        try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.list(base)) {
            s.filter(p -> p.getFileName().toString().startsWith("backup-") && p.getFileName().toString().endsWith(".zip"))
             .forEach(p -> {
                 try {
                     long t = java.nio.file.Files.getLastModifiedTime(p).toMillis();
                     if (t < keepBefore) {
                         java.nio.file.Files.deleteIfExists(p);
                     }
                 } catch (Exception ignore) {}
             });
        }
    }
}
