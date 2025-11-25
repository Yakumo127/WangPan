package com.filemanager.dto;

import java.util.List;

public class DashboardSummaryDTO {
    private long fileCount;
    private long folderCount;
    private long recycleCount;
    private long quotaUsed;
    private long quotaLimit;
    private List<DashboardFileDTO> recentUploads;
    private List<DashboardActivityDTO> recentActivities;

    public long getFileCount() { return fileCount; }
    public void setFileCount(long fileCount) { this.fileCount = fileCount; }
    public long getFolderCount() { return folderCount; }
    public void setFolderCount(long folderCount) { this.folderCount = folderCount; }
    public long getRecycleCount() { return recycleCount; }
    public void setRecycleCount(long recycleCount) { this.recycleCount = recycleCount; }
    public long getQuotaUsed() { return quotaUsed; }
    public void setQuotaUsed(long quotaUsed) { this.quotaUsed = quotaUsed; }
    public long getQuotaLimit() { return quotaLimit; }
    public void setQuotaLimit(long quotaLimit) { this.quotaLimit = quotaLimit; }
    public List<DashboardFileDTO> getRecentUploads() { return recentUploads; }
    public void setRecentUploads(List<DashboardFileDTO> recentUploads) { this.recentUploads = recentUploads; }
    public List<DashboardActivityDTO> getRecentActivities() { return recentActivities; }
    public void setRecentActivities(List<DashboardActivityDTO> recentActivities) { this.recentActivities = recentActivities; }
}
