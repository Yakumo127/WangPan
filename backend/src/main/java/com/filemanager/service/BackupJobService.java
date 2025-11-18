package com.filemanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemanager.entity.BackupJob;
import com.filemanager.repository.BackupJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class BackupJobService {

    private final BackupJobRepository repo;
    private final BackupService backupService;
    private final ImportService importService;

    private final ConcurrentHashMap<Long, Boolean> cancelMap = new ConcurrentHashMap<>();
    private final ObjectMapper om = new ObjectMapper();

    public BackupJob createJob(String type, Map<String, Object> params, com.filemanager.entity.User createdBy) {
        BackupJob j = new BackupJob();
        j.setJobType(type);
        j.setStatus("PENDING");
        j.setProgress(0);
        try { j.setParams(om.writeValueAsString(params)); } catch (Exception ignore) {}
        j.setCreatedAt(LocalDateTime.now());
        j.setCreatedBy(createdBy);
        return repo.save(j);
    }

    public Page<BackupJob> list(int page, int size) { return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0,page), Math.max(1,size))); }
    public Optional<BackupJob> get(Long id) { return repo.findById(id); }

    public void requestCancel(Long id) { cancelMap.put(id, true); }
    public boolean isCancelled(Long id) { return cancelMap.getOrDefault(id, false); }

    public void markRunning(Long id, String stage) {
        repo.findById(id).ifPresent(j -> { j.setStatus("RUNNING"); j.setStartedAt(LocalDateTime.now()); j.setStage(stage); j.setProgress(1); repo.save(j); });
    }
    public void updateProgress(Long id, int progress, String stage) {
        repo.findById(id).ifPresent(j -> { j.setProgress(Math.max(0, Math.min(100, progress))); if (stage != null) j.setStage(stage); repo.save(j); });
    }
    public void markSucceeded(Long id, Map<String, Object> stats) {
        repo.findById(id).ifPresent(j -> {
            j.setStatus("SUCCEEDED"); j.setProgress(100); j.setEndedAt(LocalDateTime.now());
            try { if (stats != null) j.setStats(om.writeValueAsString(stats)); } catch (Exception ignore) {}
            repo.save(j);
        });
        cancelMap.remove(id);
    }
    public void markFailed(Long id, String error) {
        repo.findById(id).ifPresent(j -> {
            j.setStatus("FAILED"); j.setEndedAt(LocalDateTime.now()); j.setError(error);
            repo.save(j);
        });
        cancelMap.remove(id);
    }
    public void markCanceled(Long id) {
        repo.findById(id).ifPresent(j -> {
            j.setStatus("CANCELED"); j.setEndedAt(LocalDateTime.now());
            repo.save(j);
        });
        cancelMap.remove(id);
    }

    public interface CancelChecker { boolean cancelled(); }
    public interface ProgressCallback { void onStage(String stage); void onProgress(int percent); }

    @Async
    public void runExportJob(Long jobId, String path, String format, boolean includeThumbnails, String mode) {
        try {
            markRunning(jobId, "export:init");
            // 进入导出主流程前先推进一个阶段，便于定位“是否进入方法体”
            updateProgress(jobId, 1, "export:begin");
            BackupService.ProgressCallback cb = new BackupService.ProgressCallback() {
                private int total = 0; private int copied = 0;
                @Override public void onStart(int totalBlobs, String stage) { this.total = Math.max(1, totalBlobs); updateProgress(jobId, 5, stage); }
                @Override public void onBlobCopied(int copiedCount) {
                    this.copied = copiedCount; int p = 5 + (int) Math.round((copied * 90.0) / total); updateProgress(jobId, Math.min(95, p), "export:copy");
                    if (isCancelled(jobId)) throw new RuntimeException("CANCELLED");
                }
                @Override public void onStage(String stage) { updateProgress(jobId, nullProgressFallback(), stage); }
                private int nullProgressFallback() { return 5 + (int) Math.round((copied * 90.0) / Math.max(1,total)); }
            };
            long bytes = backupService.exportToServerWithProgress(path, format, includeThumbnails, mode, cb, () -> isCancelled(jobId));
            markSucceeded(jobId, Map.of("bytes", bytes, "path", path));
        } catch (Exception e) {
            if ("CANCELLED".equals(e.getMessage())) { markCanceled(jobId); }
            else {
                String msg = buildErrorWithStack(e);
                markFailed(jobId, msg);
            }
        }
    }

    private String buildErrorWithStack(Exception e) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            String trace = sw.toString();
            String head = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
            String combined = head + "\n" + trace;
            // 避免字段过长（数据库 TEXT 足够，保险起见截断到 16KB）
            if (combined.length() > 16384) combined = combined.substring(0, 16384);
            return combined;
        } catch (Exception ignore) {
            String msg = e.getMessage();
            return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
        }
    }

    @Async
    public void runImportJob(Long jobId, org.springframework.web.multipart.MultipartFile file, boolean rebuildThumbnails) {
        try {
            markRunning(jobId, "import:init");
            ImportService.ProgressCallback cb = new ImportService.ProgressCallback() {
                @Override public void onStage(String stage, int percent) { updateProgress(jobId, percent, stage); if (isCancelled(jobId)) throw new RuntimeException("CANCELLED"); }
            };
            Map<String, Object> stats = importService.importBackupAsJob(file, rebuildThumbnails, cb, () -> isCancelled(jobId));
            markSucceeded(jobId, stats);
        } catch (Exception e) {
            if ("CANCELLED".equals(e.getMessage())) { markCanceled(jobId); } else { markFailed(jobId, e.getMessage()); }
        }
    }
}
