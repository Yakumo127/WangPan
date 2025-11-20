package com.filemanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 下载一次性 Token 使用记录实体
 * 用于实现“单次使用”限制，通过 tokenHash 唯一键防止重复下载。
 */
@Entity
@Table(name = "download_tokens_used")
public class DownloadTokenUsed {

    @Id
    @Column(name = "token_hash", length = 128, nullable = false)
    private String tokenHash;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}

