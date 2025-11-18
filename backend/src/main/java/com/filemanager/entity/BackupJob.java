package com.filemanager.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "backup_jobs")
public class BackupJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_type", length = 32, nullable = false)
    private String jobType; // EXPORT | IMPORT | PRECHECK | SCHEDULED

    @Column(name = "status", length = 32, nullable = false)
    private String status; // PENDING | RUNNING | SUCCEEDED | FAILED | CANCELED

    @Column(name = "progress", nullable = false)
    private Integer progress = 0; // 0-100

    @Column(name = "stage", length = 255)
    private String stage;

    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    @Column(name = "stats", columnDefinition = "TEXT")
    private String stats;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @JsonIgnore
    private User createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public String getStats() { return stats; }
    public void setStats(String stats) { this.stats = stats; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
}
