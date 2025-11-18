package com.filemanager.task;

import com.filemanager.service.BlobService;
import com.filemanager.service.SystemSettingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlobGcScheduler {

    private final BlobService blobService;
    private final SystemSettingService systemSettingService;

    public BlobGcScheduler(BlobService blobService, SystemSettingService systemSettingService) {
        this.blobService = blobService;
        this.systemSettingService = systemSettingService;
    }

    // 每小时执行一次，清理未引用的 Blobs
    @Scheduled(fixedDelayString = "${file.blob.gc.fixed-delay-ms:3600000}")
    public void gc() {
        try {
            // 在线备份窗口内冻结 GC（对用户无感）
            boolean frozen = false;
            try {
                frozen = systemSettingService.getBoolean("backup.gc.freeze", false) || systemSettingService.getBackupGcFreezeCount() > 0;
            } catch (Exception ignore) {}
            if (frozen) return;
            blobService.gcUnreferenced();
        } catch (Exception ignore) {}
    }
}
