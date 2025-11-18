package com.filemanager.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_versions",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_file_version_no", columnNames = {"file_id", "version_no"})
       })
public class FileVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    @JsonIgnore
    private File file;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "blob_hash", length = 64, nullable = false)
    private String blobHash;

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "comment", length = 255)
    private String comment;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getBlobHash() { return blobHash; }
    public void setBlobHash(String blobHash) { this.blobHash = blobHash; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
