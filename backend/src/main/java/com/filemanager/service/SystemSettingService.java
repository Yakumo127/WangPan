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

    // 上传策略：是否不限制
    public boolean isUploadAllowAll() {
        return getBoolean(KEY_UPLOAD_ALLOW_ALL, true);
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

    public void setUploadAllowAll(boolean allowAll, com.filemanager.entity.User updatedBy) {
        setBoolean(KEY_UPLOAD_ALLOW_ALL, allowAll, updatedBy);
    }

    public void setAllowedSuffixes(java.util.List<String> suffixes, com.filemanager.entity.User updatedBy) {
        String joined = (suffixes == null || suffixes.isEmpty()) ? "" : String.join(",", suffixes.stream().map(s -> {
            String v = s == null ? "" : s.trim().toLowerCase();
            if (v.startsWith(".")) v = v.substring(1);
            return v;
        }).filter(s -> !s.isEmpty()).toList());
        setString(KEY_UPLOAD_ALLOWED_SUFFIXES, joined, "允许的上传文件后缀（逗号分隔，小写，不含点）", updatedBy);
    }

    private static boolean toBoolean(SystemConfig cfg) {
        if (cfg == null || cfg.getConfigValue() == null) return false;
        return Boolean.parseBoolean(cfg.getConfigValue());
    }
}
