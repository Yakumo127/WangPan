package com.filemanager.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blobs")
public class Blob {
    @Id
    @Column(name = "hash", length = 64)
    private String hash;

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "path", nullable = false, length = 1024)
    private String path;

    @Column(name = "thumbnail_path", length = 1024)
    private String thumbnailPath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastAccessAt() { return lastAccessAt; }
    public void setLastAccessAt(LocalDateTime lastAccessAt) { this.lastAccessAt = lastAccessAt; }
}

