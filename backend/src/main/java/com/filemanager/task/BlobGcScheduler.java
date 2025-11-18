package com.filemanager.task;

import com.filemanager.service.BlobService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlobGcScheduler {

    private final BlobService blobService;

    public BlobGcScheduler(BlobService blobService) {
        this.blobService = blobService;
    }

    // 每小时执行一次，清理未引用的 Blobs
    @Scheduled(fixedDelayString = "${file.blob.gc.fixed-delay-ms:3600000}")
    public void gc() {
        try { blobService.gcUnreferenced(); } catch (Exception ignore) {}
    }
}

