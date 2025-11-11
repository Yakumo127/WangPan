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

    private static boolean toBoolean(SystemConfig cfg) {
        if (cfg == null || cfg.getConfigValue() == null) return false;
        return Boolean.parseBoolean(cfg.getConfigValue());
    }
}
