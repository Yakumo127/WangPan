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
@Table(name = "file_chunks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "chunk_number", nullable = false)
    private Integer chunkNumber;
    
    @Column(name = "chunk_size", nullable = false)
    private Long chunkSize;
    
    @Column(name = "chunk_hash", length = 64)
    private String chunkHash;
    
    @Column(name = "chunk_path", nullable = false, length = 500)
    private String chunkPath;
    
    @Column(name = "upload_status")
    @Enumerated(EnumType.STRING)
    private UploadStatus uploadStatus = UploadStatus.PENDING;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @CreationTimestamp
    private LocalDateTime createTime;
    
    @UpdateTimestamp
    private LocalDateTime updateTime;
    
    public enum UploadStatus {
        PENDING, UPLOADING, COMPLETED, FAILED
    }
    
    // 手动添加getter和setter方法以确保编译通过
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Integer getChunkNumber() { return chunkNumber; }
    public void setChunkNumber(Integer chunkNumber) { this.chunkNumber = chunkNumber; }
    
    public Long getChunkSize() { return chunkSize; }
    public void setChunkSize(Long chunkSize) { this.chunkSize = chunkSize; }
    
    public String getChunkHash() { return chunkHash; }
    public void setChunkHash(String chunkHash) { this.chunkHash = chunkHash; }
    
    public String getChunkPath() { return chunkPath; }
    public void setChunkPath(String chunkPath) { this.chunkPath = chunkPath; }
    
    public UploadStatus getUploadStatus() { return uploadStatus; }
    public void setUploadStatus(UploadStatus uploadStatus) { this.uploadStatus = uploadStatus; }
    
    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}