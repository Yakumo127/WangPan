package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class File {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(nullable = false)
    private String originalFilename;
    
    @Column(name = "content_type")
    private String contentType;
    
    @Column(nullable = false)
    private Long size;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "thumbnail_path")
    private String thumbnailPath;
    
    @Column(name = "file_hash", nullable = false)
    private String fileHash;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    @JsonIgnore
    private Folder folder;
    
    @CreationTimestamp
    private LocalDateTime createTime;
    
    @UpdateTimestamp
    private LocalDateTime updateTime;
    
    @Column(name = "delete_time")
    private LocalDateTime deleteTime;
    
    @Column(nullable = false)
    private Boolean deleted = false;
    
    private Integer downloadCount = 0;
    
    private String description;

    // 当前激活版本（版本记录表：file_versions）
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_version_id")
    @JsonIgnore
    private FileVersion activeVersion;
    
    // 用户侧：是否对文件所有者隐藏（用户在回收站“彻底删除/清空”后置为true，仅管理员可见）
    @Column(name = "owner_hidden")
    private Boolean ownerHidden = false;

    // 管理员侧：是否已排期删除（进入15天冷静期）
    @Column(name = "admin_delete_scheduled")
    private Boolean adminDeleteScheduled = false;

    // 管理员侧：排期删除请求时间、执行时间与理由
    @Column(name = "admin_delete_request_time")
    private LocalDateTime adminDeleteRequestTime;

    @Column(name = "admin_delete_execute_time")
    private LocalDateTime adminDeleteExecuteTime;

    @Column(name = "admin_delete_reason", length = 500)
    private String adminDeleteReason;

    // 配额标记：当用户执行“删除（软删）”后立即释放配额，避免二次释放
    @Column(name = "quota_released")
    private Boolean quotaReleased = false;
    
      
    public enum FileType {
        IMAGE, DOCUMENT, VIDEO, AUDIO, ARCHIVE, OTHER
    }

    // Manual getters/setters to avoid Lombok dependency at build time
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Folder getFolder() { return folder; }
    public void setFolder(Folder folder) { this.folder = folder; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public LocalDateTime getDeleteTime() { return deleteTime; }
    public void setDeleteTime(LocalDateTime deleteTime) { this.deleteTime = deleteTime; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public Integer getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Integer downloadCount) { this.downloadCount = downloadCount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getOwnerHidden() { return ownerHidden; }
    public void setOwnerHidden(Boolean ownerHidden) { this.ownerHidden = ownerHidden; }
    public Boolean getAdminDeleteScheduled() { return adminDeleteScheduled; }
    public void setAdminDeleteScheduled(Boolean adminDeleteScheduled) { this.adminDeleteScheduled = adminDeleteScheduled; }
    public LocalDateTime getAdminDeleteRequestTime() { return adminDeleteRequestTime; }
    public void setAdminDeleteRequestTime(LocalDateTime adminDeleteRequestTime) { this.adminDeleteRequestTime = adminDeleteRequestTime; }
    public LocalDateTime getAdminDeleteExecuteTime() { return adminDeleteExecuteTime; }
    public void setAdminDeleteExecuteTime(LocalDateTime adminDeleteExecuteTime) { this.adminDeleteExecuteTime = adminDeleteExecuteTime; }
    public String getAdminDeleteReason() { return adminDeleteReason; }
    public void setAdminDeleteReason(String adminDeleteReason) { this.adminDeleteReason = adminDeleteReason; }
    public Boolean getQuotaReleased() { return quotaReleased; }
    public void setQuotaReleased(Boolean quotaReleased) { this.quotaReleased = quotaReleased; }
    public FileVersion getActiveVersion() { return activeVersion; }
    public void setActiveVersion(FileVersion activeVersion) { this.activeVersion = activeVersion; }
}
