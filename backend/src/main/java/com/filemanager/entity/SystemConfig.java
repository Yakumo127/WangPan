package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "config_key", unique = true, nullable = false, length = 100)
    private String configKey;
    
    @Column(name = "config_value", length = 1000)
    private String configValue;
    
    @Column(name = "config_type", length = 50)
    private String configType;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @CreationTimestamp
    private LocalDateTime createTime;
    
    @UpdateTimestamp
    private LocalDateTime updateTime;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
    
    // 配置键常量
    public static final String KEY_STORAGE_PATH = "storage.path";
    public static final String KEY_DEFAULT_QUOTA = "user.default.quota";
    public static final String KEY_MAX_FILE_SIZE = "file.max.size";
    public static final String KEY_CHUNK_SIZE = "file.chunk.size";
    public static final String KEY_ALLOWED_FILE_TYPES = "file.allowed.types";
    public static final String KEY_ENABLE_REGISTRATION = "system.enable.registration";
    public static final String KEY_ENABLE_EMAIL_VERIFICATION = "system.enable.email.verification";
    public static final String KEY_SESSION_TIMEOUT = "system.session.timeout";
    public static final String KEY_MAX_LOGIN_ATTEMPTS = "security.max.login.attempts";
    public static final String KEY_PASSWORD_MIN_LENGTH = "security.password.min.length";
    public static final String KEY_ENABLE_BACKUP = "system.enable.backup";
    public static final String KEY_BACKUP_INTERVAL = "system.backup.interval";
    public static final String KEY_CLEANUP_INTERVAL = "system.cleanup.interval";
    
    // 手动添加getter和setter方法以确保编译通过
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    
    public String getConfigType() { return configType; }
    public void setConfigType(String configType) { this.configType = configType; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Boolean getIsSystem() { return isSystem; }
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    
    public User getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(User updatedBy) { this.updatedBy = updatedBy; }
}