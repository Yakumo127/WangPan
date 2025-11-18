package com.filemanager.controller;

import com.filemanager.service.BackupService;
import com.filemanager.service.SystemSettingService;
import com.filemanager.service.BackupJobService;
import com.filemanager.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/admin/settings/backup")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminBackupController {

    private final BackupService backupService;
    private final SystemSettingService systemSettingService;
    private final ImportService importService;
    private final BackupJobService jobService;

    @GetMapping(value = "/export")
    public void export(HttpServletResponse response,
                       @RequestParam(value = "format", required = false, defaultValue = "json") String format,
                       @RequestParam(value = "includeThumbnails", required = false, defaultValue = "true") boolean includeThumbnails,
                       @RequestParam(value = "mode", required = false, defaultValue = "online") String mode) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String name = "backup-" + ts + ".zip";
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"; filename*=UTF-8''" + encoded);
            backupService.exportToStream(format, includeThumbnails, mode, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            throw new RuntimeException("导出失败", e);
        }
    }

    @PostMapping(value = "/export-to-server", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> exportToServer(@RequestBody Map<String, Object> body) {
        String path = Objects.toString(body.getOrDefault("path", ""), "");
        String format = Objects.toString(body.getOrDefault("format", "json"), "json");
        boolean includeThumbnails = Boolean.TRUE.equals(body.get("includeThumbnails"));
        String mode = Objects.toString(body.getOrDefault("mode", "online"), "online");
        long bytes = backupService.exportToServer(path, format, includeThumbnails, mode);
        return Map.of("message", "导出完成", "path", path, "bytes", bytes);
    }

    // 备份配置：白名单、定时与保留策略
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return Map.of(
                "scheduleEnabled", systemSettingService.isBackupScheduleEnabled(),
                "cron", systemSettingService.getBackupScheduleCronOrDefault("0 0 2 * * ?"),
                "dest", systemSettingService.getBackupScheduleDestDirs(),
                "retentionDays", systemSettingService.getBackupRetentionDaysOrDefault(14),
                "whitelist", systemSettingService.getWhitelistDirs()
        );
    }

    @PutMapping(value = "/config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateConfig(@RequestBody Map<String, Object> body) {
        try {
            Object sch = body.get("scheduleEnabled");
            if (sch != null) systemSettingService.setBoolean(SystemSettingService.KEY_BACKUP_SCHEDULE_ENABLED, Boolean.TRUE.equals(sch), null);
            Object cron = body.get("cron");
            if (cron != null) systemSettingService.setString(SystemSettingService.KEY_BACKUP_SCHEDULE_CRON, Objects.toString(cron, "0 0 2 * * ?"), "备份计划 CRON", null);
            Object dest = body.get("dest");
            if (dest instanceof java.util.List<?> list) {
                String joined = String.join(",", list.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).toList());
                systemSettingService.setString(SystemSettingService.KEY_BACKUP_SCHEDULE_DEST, joined, "备份计划目标目录", null);
            } else if (dest != null) {
                systemSettingService.setString(SystemSettingService.KEY_BACKUP_SCHEDULE_DEST, Objects.toString(dest, ""), "备份计划目标目录", null);
            }
            Object ret = body.get("retentionDays");
            if (ret != null) {
                try { int days = Integer.parseInt(ret.toString()); systemSettingService.setInt(SystemSettingService.KEY_BACKUP_RETENTION_DAYS, days, "备份保留天数", null); } catch (Exception ignore) {}
            }
            Object wl = body.get("whitelist");
            if (wl instanceof java.util.List<?> list2) {
                String joined = String.join(",", list2.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).toList());
                systemSettingService.setString(SystemSettingService.KEY_BACKUP_WHITELIST_DIRS, joined, "服务器导出白名单", null);
            } else if (wl != null) {
                systemSettingService.setString(SystemSettingService.KEY_BACKUP_WHITELIST_DIRS, Objects.toString(wl, ""), "服务器导出白名单", null);
            }
            Object fmt = body.get("format");
            if (fmt != null) systemSettingService.setString(SystemSettingService.KEY_BACKUP_SCHEDULE_FORMAT, Objects.toString(fmt, "json"), "计划导出格式", null);
            Object md = body.get("mode");
            if (md != null) systemSettingService.setString(SystemSettingService.KEY_BACKUP_SCHEDULE_MODE, Objects.toString(md, "online"), "计划导出模式", null);
            Object incT = body.get("includeThumbnails");
            if (incT != null) systemSettingService.setBoolean(SystemSettingService.KEY_BACKUP_SCHEDULE_INCLUDE_THUMBNAILS, Boolean.TRUE.equals(incT), null);
        } catch (Exception ignore) {}
        return Map.of("message", "已更新", "config", getConfig());
    }

    // 预检（导入前）
    @PostMapping(value = "/precheck", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> precheck(@RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return importService.precheck(file);
    }

    // 导入（全量替换）
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importBackup(@RequestPart("file") org.springframework.web.multipart.MultipartFile file,
                                            @RequestParam(value = "mode", required = false, defaultValue = "full") String mode,
                                            @RequestParam(value = "confirm", required = false, defaultValue = "false") boolean confirm,
                                            @RequestParam(value = "rebuildThumbnails", required = false, defaultValue = "false") boolean rebuildThumbnails) {
        if (!confirm) throw new RuntimeException("导入需要确认：confirm=true");
        return importService.importBackup(file, rebuildThumbnails);
    }

    // ====== 作业与进度 ======
    @PostMapping(value = "/jobs/export-to-server", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createExportJob(@RequestBody Map<String, Object> body) {
        String path = Objects.toString(body.getOrDefault("path", ""), "");
        String format = Objects.toString(body.getOrDefault("format", "json"), "json");
        boolean includeThumbnails = Boolean.TRUE.equals(body.get("includeThumbnails"));
        String mode = Objects.toString(body.getOrDefault("mode", "online"), "online");
        var job = jobService.createJob("EXPORT", Map.of("path", path, "format", format, "includeThumbnails", includeThumbnails, "mode", mode), null);
        jobService.runExportJob(job.getId(), path, format, includeThumbnails, mode);
        return Map.of("jobId", job.getId());
    }

    @PostMapping(value = "/jobs/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> createImportJob(@RequestPart("file") org.springframework.web.multipart.MultipartFile file,
                                               @RequestParam(value = "rebuildThumbnails", required = false, defaultValue = "false") boolean rebuildThumbnails) {
        var job = jobService.createJob("IMPORT", Map.of("rebuildThumbnails", rebuildThumbnails), null);
        jobService.runImportJob(job.getId(), file, rebuildThumbnails);
        return Map.of("jobId", job.getId());
    }

    @GetMapping("/jobs")
    public Map<String, Object> listJobs(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
                                        @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        var p = jobService.list(page, size);
        return Map.of("content", p.getContent(), "total", p.getTotalElements());
    }

    @GetMapping("/jobs/{id}")
    public Object getJob(@PathVariable("id") Long id) {
        return jobService.get(id).orElseThrow(() -> new RuntimeException("Job 不存在"));
    }

    @PostMapping("/jobs/{id}/cancel")
    public Map<String, Object> cancelJob(@PathVariable("id") Long id) {
        jobService.requestCancel(id);
        return Map.of("message", "已请求取消");
    }
}
