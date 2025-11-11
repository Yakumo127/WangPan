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

    private final SystemConfigRepository systemConfigRepository;

    @Value("${recycle.manual-purge.enabled:false}")
    private boolean manualPurgeDefault;

    public boolean isManualPurgeEnabled() {
        return getBoolean(KEY_RECYCLE_MANUAL_PURGE_ENABLED, manualPurgeDefault);
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

    private static boolean toBoolean(SystemConfig cfg) {
        if (cfg == null || cfg.getConfigValue() == null) return false;
        return Boolean.parseBoolean(cfg.getConfigValue());
    }
}

