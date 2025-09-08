package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(name = "display_name", length = 100)
    private String displayName;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Column(nullable = false)
    private Boolean enabled = true;
    
    @Column(nullable = false)
    private Boolean locked = false;
    
    @Column(name = "login_attempts")
    private Integer loginAttempts = 0;
    
    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;
    
    @Column(name = "last_login_ip")
    private String lastLoginIp;
    
    @Column(name = "quota_limit", nullable = false)
    private Long quotaLimit = 1073741824L; // 1GB
    
    @Column(name = "quota_used")
    private Long quotaUsed = 0L;
    
    @Column(name = "email_verified")
    private Boolean emailVerified = false;
    
    @Column(name = "verification_token")
    private String verificationToken;
    
    @Column(name = "password_reset_token")
    private String passwordResetToken;
    
    @Column(name = "password_reset_expiry")
    private LocalDateTime passwordResetExpiry;
    
    @CreationTimestamp
    private LocalDateTime createTime;
    
    @UpdateTimestamp
    private LocalDateTime updateTime;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FileEntity> files;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Folder> folders;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserLog> logs;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FileChunk> fileChunks;
    
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;
    
    public enum Role {
        USER, ADMIN
    }
    
    // 计算剩余配额
    public Long getRemainingQuota() {
        return quotaLimit - quotaUsed;
    }
    
    // 检查是否有足够配额
    public boolean hasEnoughQuota(long fileSize) {
        return getRemainingQuota() >= fileSize;
    }
    
    // 使用配额
    public void useQuota(long fileSize) {
        this.quotaUsed += fileSize;
    }
    
    // 释放配额
    public void releaseQuota(long fileSize) {
        this.quotaUsed = Math.max(0, this.quotaUsed - fileSize);
    }
}