package com.filemanager.service;

import com.filemanager.entity.SystemConfig;
import com.filemanager.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemSettingService {
    public static final String KEY_RECYCLE_MANUAL_PURGE_ENABLED = "recycle.manual.purge.enabled";
    public static final String KEY_RECYCLE_RETENTION_DAYS = "recycle.admin.retention.days";
    // 上传策略
    public static final String KEY_UPLOAD_ALLOW_ALL = "file.upload.allowAll";
    public static final String KEY_UPLOAD_ALLOWED_SUFFIXES = "file.upload.allowed.suffixes"; // 逗号分隔的小写后缀（不含点）
    public static final String KEY_SYSTEM_MAINTENANCE_ENABLED = "system.maintenance.enabled";
    public static final String KEY_SYSTEM_MAINTENANCE_LEVEL = "system.maintenance.level"; // write-only | all
    // 备份与迁移相关键
    public static final String KEY_BACKUP_GC_FREEZE = "backup.gc.freeze";
    public static final String KEY_BACKUP_ADMIN_PURGE_FREEZE = "backup.admin-purge.freeze";
    public static final String KEY_BACKUP_GC_FREEZE_COUNT = "backup.gc.freeze.count";
    public static final String KEY_BACKUP_ADMIN_PURGE_FREEZE_COUNT = "backup.admin-purge.freeze.count";
    public static final String KEY_BACKUP_WHITELIST_DIRS = "backup.whitelist.dirs"; // 逗号分隔目录
    public static final String KEY_BACKUP_SCHEDULE_ENABLED = "backup.schedule.enabled";
    public static final String KEY_BACKUP_SCHEDULE_CRON = "backup.schedule.cron";
    public static final String KEY_BACKUP_SCHEDULE_DEST = "backup.schedule.dest"; // 逗号分隔目录
    public static final String KEY_BACKUP_SCHEDULE_FORMAT = "backup.schedule.format"; // json|sql
    public static final String KEY_BACKUP_SCHEDULE_MODE = "backup.schedule.mode"; // online|maintenance
    public static final String KEY_BACKUP_SCHEDULE_INCLUDE_THUMBNAILS = "backup.schedule.includeThumbnails"; // true|false
    public static final String KEY_BACKUP_RETENTION_DAYS = "backup.retention.days";
    public static final String KEY_BACKUP_CONCURRENCY = "backup.concurrency.blobCopy";
    public static final String KEY_BACKUP_IO_THROTTLE_BPS = "backup.io.throttle.bytesPerSec";
    public static final String KEY_BACKUP_ZIP_LEVEL = "backup.zip.level";

    private final SystemConfigRepository systemConfigRepository;

    @Value("${recycle.manual-purge.enabled:false}")
    private boolean manualPurgeDefault;

    public boolean isManualPurgeEnabled() {
        return getBoolean(KEY_RECYCLE_MANUAL_PURGE_ENABLED, manualPurgeDefault);
    }

    public int getRetentionDaysOrDefault(int defDays) {
        return systemConfigRepository.findByConfigKey(KEY_RECYCLE_RETENTION_DAYS)
                .map(cfg -> {
                    try { return Integer.parseInt(cfg.getConfigValue()); } catch (Exception e) { return defDays; }
                })
                .orElse(defDays);
    }

    public boolean getBoolean(String key, boolean def) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemSettingService::toBoolean)
                .orElse(def);
    }

    public int getIntOrDefault(String key, int def) {
        return systemConfigRepository.findByConfigKey(key)
                .map(cfg -> {
                    try { return Integer.parseInt(cfg.getConfigValue()); } catch (Exception e) { return def; }
                })
                .orElse(def);
    }

    // 备份：冻结/解冻开关
    public boolean isBackupGcFrozen() { return getBoolean(KEY_BACKUP_GC_FREEZE, false); }
    public boolean isAdminPurgeFrozen() { return getBoolean(KEY_BACKUP_ADMIN_PURGE_FREEZE, false); }
    public int getBackupGcFreezeCount() { return getIntOrDefault(KEY_BACKUP_GC_FREEZE_COUNT, 0); }
    public int getAdminPurgeFreezeCount() { return getIntOrDefault(KEY_BACKUP_ADMIN_PURGE_FREEZE_COUNT, 0); }

    public void setBackupGcFrozen(boolean value, com.filemanager.entity.User updatedBy) {
        setBoolean(KEY_BACKUP_GC_FREEZE, value, updatedBy);
    }
    public void setAdminPurgeFrozen(boolean value, com.filemanager.entity.User updatedBy) {
        setBoolean(KEY_BACKUP_ADMIN_PURGE_FREEZE, value, updatedBy);
    }

    // 计数型冻结（并发安全性：简单读改写，足够满足本系统场景）
    public void incBackupGcFreezeCount(int delta, com.filemanager.entity.User updatedBy) {
        adjustInt(KEY_BACKUP_GC_FREEZE_COUNT, delta, "GC 冻结引用计数", updatedBy);
    }
    public void incAdminPurgeFreezeCount(int delta, com.filemanager.entity.User updatedBy) {
        adjustInt(KEY_BACKUP_ADMIN_PURGE_FREEZE_COUNT, delta, "管理员清理冻结引用计数", updatedBy);
    }
    private void adjustInt(String key, int delta, String desc, com.filemanager.entity.User updatedBy) {
        SystemConfig cfg = systemConfigRepository.findByConfigKey(key).orElseGet(SystemConfig::new);
        cfg.setConfigKey(key);
        int cur = 0;
        try { cur = Integer.parseInt(cfg.getConfigValue()); } catch (Exception ignore) {}
        int next = Math.max(0, cur + delta);
        cfg.setConfigValue(Integer.toString(next));
        cfg.setConfigType("INTEGER");
        cfg.setDescription(desc);
        cfg.setIsSystem(true);
        cfg.setIsActive(true);
        cfg.setUpdatedBy(updatedBy);
        systemConfigRepository.save(cfg);
    }

    // 备份：白名单路径与调度/保留参数
    public java.util.List<String> getWhitelistDirs() {
        String raw = getString(KEY_BACKUP_WHITELIST_DIRS, "");
        if (raw == null || raw.isBlank()) return java.util.List.of();
        String[] parts = raw.split(",");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String p : parts) {
            if (p != null) {
                String s = p.trim();
                if (!s.isEmpty()) list.add(s);
            }
        }
        return list;
    }

    public boolean isBackupScheduleEnabled() { return getBoolean(KEY_BACKUP_SCHEDULE_ENABLED, false); }
    public String getBackupScheduleCronOrDefault(String def) { return getString(KEY_BACKUP_SCHEDULE_CRON, def); }
    public String getBackupScheduleFormatOrDefault(String def) { return getString(KEY_BACKUP_SCHEDULE_FORMAT, def); }
    public String getBackupScheduleModeOrDefault(String def) { return getString(KEY_BACKUP_SCHEDULE_MODE, def); }
    public boolean getBackupScheduleIncludeThumbnails(boolean def) { return getBoolean(KEY_BACKUP_SCHEDULE_INCLUDE_THUMBNAILS, def); }
    public java.util.List<String> getBackupScheduleDestDirs() {
        String raw = getString(KEY_BACKUP_SCHEDULE_DEST, "");
        if (raw == null || raw.isBlank()) return java.util.List.of();
        String[] parts = raw.split(",");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String p : parts) { if (p != null && !p.trim().isEmpty()) list.add(p.trim()); }
        return list;
    }
    public int getBackupRetentionDaysOrDefault(int def) {
        return systemConfigRepository.findByConfigKey(KEY_BACKUP_RETENTION_DAYS)
                .map(cfg -> { try { return Integer.parseInt(cfg.getConfigValue()); } catch (Exception e) { return def; } })
                .orElse(def);
    }
    public int getBlobCopyConcurrencyOrDefault(int def) {
        return systemConfigRepository.findByConfigKey(KEY_BACKUP_CONCURRENCY)
                .map(cfg -> { try { return Integer.parseInt(cfg.getConfigValue()); } catch (Exception e) { return def; } })
                .orElse(def);
    }
    public Long getIoThrottleBpsOrNull() {
        String v = getString(KEY_BACKUP_IO_THROTTLE_BPS, "");
        if (v == null || v.isBlank()) return null;
        try { return Long.parseLong(v.trim()); } catch (Exception e) { return null; }
    }
    public int getZipLevelOrDefault(int def) {
        return systemConfigRepository.findByConfigKey(KEY_BACKUP_ZIP_LEVEL)
                .map(cfg -> { try { return Integer.parseInt(cfg.getConfigValue()); } catch (Exception e) { return def; } })
                .orElse(def);
    }

    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_SYSTEM,
            userId = "#updatedBy?.id",
            resourceName = "#key",
            description = "'setBoolean=' + #value"
    )
    public void setBoolean(String key, boolean value, com.filemanager.entity.User updatedBy) {
        SystemConfig cfg = systemConfigRepository.findByConfigKey(key).orElseGet(SystemConfig::new);
        cfg.setConfigKey(key);
        cfg.setConfigValue(Boolean.toString(value));
        cfg.setConfigType("BOOLEAN");
        cfg.setDescription("系统设置开关");
        cfg.setIsSystem(true);
        cfg.setIsActive(true);
        cfg.setUpdatedBy(updatedBy);
        systemConfigRepository.save(cfg);
    }

    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING_RETENTION,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_SYSTEM,
            userId = "#updatedBy?.id",
            resourceName = "#key",
            description = "#description + ': ' + #value"
    )
    public void setInt(String key, int value, String description, com.filemanager.entity.User updatedBy) {
        SystemConfig cfg = systemConfigRepository.findByConfigKey(key).orElseGet(SystemConfig::new);
        cfg.setConfigKey(key);
        cfg.setConfigValue(Integer.toString(value));
        cfg.setConfigType("INTEGER");
        cfg.setDescription(description);
        cfg.setIsSystem(true);
        cfg.setIsActive(true);
        cfg.setUpdatedBy(updatedBy);
        systemConfigRepository.save(cfg);
    }

    public String getString(String key, String def) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(def);
    }

    public boolean isMaintenanceEnabled() { return getBoolean(KEY_SYSTEM_MAINTENANCE_ENABLED, false); }
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_SYSTEM,
            userId = "#updatedBy?.id",
            resourceName = "T(com.filemanager.service.SystemSettingService).KEY_SYSTEM_MAINTENANCE_ENABLED",
            description = "'maintenance=' + #enabled"
    )
    public void setMaintenanceEnabled(boolean enabled, com.filemanager.entity.User updatedBy) {
        setBoolean(KEY_SYSTEM_MAINTENANCE_ENABLED, enabled, updatedBy);
    }

    public String getMaintenanceLevelOrDefault(String def) {
        return getString(KEY_SYSTEM_MAINTENANCE_LEVEL, def);
    }
    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_SYSTEM,
            userId = "#updatedBy?.id",
            resourceName = "T(com.filemanager.service.SystemSettingService).KEY_SYSTEM_MAINTENANCE_LEVEL",
            description = "'maintenance.level=' + #level"
    )
    public void setMaintenanceLevel(String level, com.filemanager.entity.User updatedBy) {
        setString(KEY_SYSTEM_MAINTENANCE_LEVEL, level, "维护锁级别（write-only|all）", updatedBy);
    }

    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_SYSTEM,
            userId = "#updatedBy?.id",
            resourceName = "#key",
            description = "#description + ': ' + #value"
    )
    public void setString(String key, String value, String description, com.filemanager.entity.User updatedBy) {
        SystemConfig cfg = systemConfigRepository.findByConfigKey(key).orElseGet(SystemConfig::new);
        cfg.setConfigKey(key);
        cfg.setConfigValue(value);
        cfg.setConfigType("STRING");
        cfg.setDescription(description);
        cfg.setIsSystem(true);
        cfg.setIsActive(true);
        cfg.setUpdatedBy(updatedBy);
        systemConfigRepository.save(cfg);
    }

    // 上传策略：是否不限制（默认关闭更安全）
    public boolean isUploadAllowAll() {
        return getBoolean(KEY_UPLOAD_ALLOW_ALL, false);
    }

    // 上传策略：获取后缀列表（小写、去空）
    public java.util.List<String> getAllowedSuffixes() {
        String raw = getString(KEY_UPLOAD_ALLOWED_SUFFIXES, "");
        if (raw == null || raw.isBlank()) return java.util.List.of();
        String[] parts = raw.split(",");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String p : parts) {
            if (p != null) {
                String s = p.trim().toLowerCase();
                if (!s.isEmpty()) list.add(s);
            }
        }
        return list;
    }

    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING_UPLOAD_POLICY,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_SYSTEM,
            userId = "#updatedBy?.id",
            resourceName = "T(com.filemanager.service.SystemSettingService).KEY_UPLOAD_ALLOW_ALL",
            description = "'allowAll=' + #allowAll"
    )
    public void setUploadAllowAll(boolean allowAll, com.filemanager.entity.User updatedBy) {
        setBoolean(KEY_UPLOAD_ALLOW_ALL, allowAll, updatedBy);
    }

    @com.filemanager.audit.AuditedOperation(
            actionType = com.filemanager.entity.UserLog.ACTION_UPDATE_SETTING_UPLOAD_POLICY,
            resourceType = com.filemanager.entity.UserLog.RESOURCE_SYSTEM,
            userId = "#updatedBy?.id",
            resourceName = "T(com.filemanager.service.SystemSettingService).KEY_UPLOAD_ALLOWED_SUFFIXES",
            description = "'suffixes=' + (#suffixes == null ? '' : #suffixes.toString())"
    )
    public void setAllowedSuffixes(java.util.List<String> suffixes, com.filemanager.entity.User updatedBy) {
        String joined = (suffixes == null || suffixes.isEmpty()) ? "" : String.join(",",
                suffixes.stream().map(s -> {
                    String v = s == null ? "" : s.trim().toLowerCase();
                    if (v.startsWith(".")) v = v.substring(1);
                    return v;
                })
                // 仅保留字母数字的简单后缀，过滤非法字符
                .filter(s -> !s.isEmpty() && s.matches("[a-z0-9]+"))
                .distinct()
                .toList());
        setString(KEY_UPLOAD_ALLOWED_SUFFIXES, joined, "允许的上传文件后缀（逗号分隔，小写，不含点）", updatedBy);
    }

    private static boolean toBoolean(SystemConfig cfg) {
        if (cfg == null || cfg.getConfigValue() == null) return false;
        return Boolean.parseBoolean(cfg.getConfigValue());
    }
}
