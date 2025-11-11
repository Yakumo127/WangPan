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
            try {
                auditLogService.logSuccess(adminId,
                        com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING,
                        "SYSTEM",
                        null,
                        SystemSettingService.KEY_RECYCLE_RETENTION_DAYS,
                        "retentionDays: " + oldDays + " -> " + retentionDays,
                        0L);
            } catch (Exception ignore) {}
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

        try {
            auditLogService.logSuccess(adminId,
                    com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING,
                    "SYSTEM",
                    null,
                    com.filemanager.service.SystemSettingService.KEY_UPLOAD_ALLOWED_SUFFIXES,
                    (allowAll ? "allowAll=true" : ("allowAll=false; suffixes=" + (suffixes == null ? "" : String.join(",", suffixes)))),
                    0L);
        } catch (Exception ignore) {}

        return Map.of("message", "上传策略已更新", "allowAll", allowAll, "allowedSuffixes", suffixes);
    }
}
