package com.filemanager.controller;

import com.filemanager.service.SystemSettingService;
import com.filemanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminSystemController {

    private final SystemSettingService systemSettingService;
    private final UserService userService;
    private final com.filemanager.service.AuditLogService auditLogService;
    private final SystemSettingService settingService;
    private final com.filemanager.service.FileService fileService;

    // 获取回收站相关设置
    @GetMapping("/recycle")
    public Map<String, Object> getRecycleSettings() {
        boolean manualEnabled = systemSettingService.isManualPurgeEnabled();
        int retentionDays = systemSettingService.getRetentionDaysOrDefault(15);
        return Map.of(
                "manualPurgeEnabled", manualEnabled,
                "retentionDays", retentionDays
        );
    }

    // 维护/只读模式开关
    @PostMapping("/maintenance")
    public Map<String, Object> maintenance(@RequestBody Map<String, Object> body) {
        boolean enabled = body.get("enabled") != null && Boolean.TRUE.equals(body.get("enabled"));
        settingService.setMaintenanceEnabled(enabled, null);
        return Map.of("enabled", settingService.isMaintenanceEnabled());
    }

    // 更新回收站相关设置（目前支持：手动清理到期开关）
    @PutMapping("/recycle")
    public Map<String, Object> updateRecycleSettings(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long adminId = userService.getUserIdByUsername(auth.getName());
        boolean manualEnabled = body.get("manualPurgeEnabled") != null && Boolean.TRUE.equals(body.get("manualPurgeEnabled"));
        Integer retentionDays = null;
        try { Object v = body.get("retentionDays"); if (v != null) retentionDays = Integer.valueOf(v.toString()); } catch (Exception ignore) {}

        if (body.containsKey("manualPurgeEnabled")) {
            systemSettingService.setBoolean(SystemSettingService.KEY_RECYCLE_MANUAL_PURGE_ENABLED, manualEnabled,
                    userService.getUserById(adminId));
        }
        if (retentionDays != null && retentionDays > 0) {
            int oldDays = systemSettingService.getRetentionDaysOrDefault(15);
            systemSettingService.setInt(SystemSettingService.KEY_RECYCLE_RETENTION_DAYS, retentionDays,
                    "回收站保留期（天）", userService.getUserById(adminId));
        }
        return Map.of("message", "设置已更新", "manualPurgeEnabled", manualEnabled, "retentionDays", retentionDays);
    }

    // 上传策略：获取
    @GetMapping("/upload-policy")
    public Map<String, Object> getUploadPolicy() {
        boolean allowAll = systemSettingService.isUploadAllowAll();
        java.util.List<String> suffixes = systemSettingService.getAllowedSuffixes();
        return Map.of("allowAll", allowAll, "allowedSuffixes", suffixes);
    }

    // 上传策略：更新
    @PutMapping("/upload-policy")
    public Map<String, Object> updateUploadPolicy(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long adminId = userService.getUserIdByUsername(auth.getName());
        com.filemanager.entity.User admin = userService.getUserById(adminId);

        boolean allowAll = body.get("allowAll") != null && Boolean.TRUE.equals(body.get("allowAll"));
        java.util.List<String> suffixes = null;
        try {
            Object v = body.get("allowedSuffixes");
            if (v instanceof java.util.List<?> list) {
                suffixes = new java.util.ArrayList<>();
                for (Object o : list) if (o != null) suffixes.add(o.toString());
            }
        } catch (Exception ignore) {}

        systemSettingService.setUploadAllowAll(allowAll, admin);
        if (suffixes != null) systemSettingService.setAllowedSuffixes(suffixes, admin);

        // 审计由 SystemSettingService 上的 AOP 负责

        return Map.of("message", "上传策略已更新", "allowAll", allowAll, "allowedSuffixes", suffixes);
    }

    // 上传超时策略：获取
    @GetMapping("/upload-timeout")
    public Map<String, Object> getUploadTimeoutSetting() {
        String mode = systemSettingService.getUploadTimeoutModeOrDefault("auto");
        int seconds = systemSettingService.getUploadTimeoutSecondsOrDefault(150);
        return Map.of("mode", mode, "timeoutSeconds", seconds);
    }

    // 上传超时策略：更新（mode: auto | manual）
    @PutMapping("/upload-timeout")
    public Map<String, Object> updateUploadTimeoutSetting(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long adminId = userService.getUserIdByUsername(auth.getName());
        com.filemanager.entity.User admin = userService.getUserById(adminId);

        String mode = body.get("mode") == null ? "auto" : body.get("mode").toString().trim().toLowerCase();
        if (!"auto".equals(mode) && !"manual".equals(mode)) {
            throw new IllegalArgumentException("mode 仅支持 auto 或 manual");
        }
        Integer seconds = null;
        try {
            Object v = body.get("timeoutSeconds");
            if (v != null) seconds = Integer.parseInt(v.toString());
        } catch (Exception ignore) {}
        if ("manual".equals(mode)) {
            if (seconds == null) {
                throw new IllegalArgumentException("手动模式需要提供超时时间（秒）");
            }
            if (seconds < 1 || seconds > 7200) {
                throw new IllegalArgumentException("超时时间需介于 1~7200 秒");
            }
        }

        systemSettingService.setUploadTimeoutMode(mode, admin);
        if ("manual".equals(mode)) {
            systemSettingService.setUploadTimeoutSeconds(seconds, admin);
        }

        return Map.of(
                "message", "上传超时策略已更新",
                "mode", mode,
                "timeoutSeconds", systemSettingService.getUploadTimeoutSecondsOrDefault(150)
        );
    }

    // 预览配置：获取允许预览的文件后缀列表
    @GetMapping("/preview-config")
    public Map<String, Object> getPreviewConfig() {
        java.util.List<String> suffixes = systemSettingService.getPreviewAllowedSuffixes();
        return Map.of("allowedSuffixes", suffixes);
    }

    // 预览配置：更新允许预览的文件后缀列表
    @PutMapping("/preview-config")
    public Map<String, Object> updatePreviewConfig(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long adminId = userService.getUserIdByUsername(auth.getName());
        com.filemanager.entity.User admin = userService.getUserById(adminId);

        java.util.List<String> suffixes = null;
        try {
            Object v = body.get("allowedSuffixes");
            if (v instanceof java.util.List<?> list) {
                suffixes = new java.util.ArrayList<>();
                for (Object o : list) {
                    if (o != null) suffixes.add(o.toString());
                }
            }
        } catch (Exception ignore) {}

        if (suffixes == null || suffixes.isEmpty()) {
            throw new IllegalArgumentException("请至少配置一个允许预览的后缀");
        }
        systemSettingService.setPreviewAllowedSuffixes(suffixes, admin);

        return Map.of("message", "预览配置已更新", "allowedSuffixes", systemSettingService.getPreviewAllowedSuffixes());
    }

    // 系统信息：运行时间与内存使用等
    @GetMapping("/system-info")
    public Map<String, Object> getSystemInfo() {
        java.lang.management.RuntimeMXBean rb = java.lang.management.ManagementFactory.getRuntimeMXBean();
        long uptimeMillis = rb.getUptime();
        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapMax = rt.maxMemory();
        String javaVersion = System.getProperty("java.version");

        // 数据库版本可选，若获取失败则返回空字符串
        String dbVersion = "";
        try {
            var root = java.nio.file.Paths.get(fileService.getStorageRoot()).toAbsolutePath().normalize();
            // 此处仅保留接口字段，具体数据库版本可在后续通过 DataSource 获取并补充
        } catch (Exception ignore) {}

        return Map.of(
                "version", "2.0.0",
                "buildTime", "",
                "javaVersion", javaVersion != null ? javaVersion : "",
                "databaseVersion", dbVersion,
                "uptimeMillis", uptimeMillis,
                "heapUsedBytes", heapUsed,
                "heapMaxBytes", heapMax
        );
    }
}
