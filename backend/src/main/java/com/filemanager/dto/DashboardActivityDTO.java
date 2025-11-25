package com.filemanager.dto;

import java.time.LocalDateTime;

public class DashboardActivityDTO {
    private String actionType;
    private String resourceType;
    private Long resourceId;
    private String resourceName;
    private String status;
    private String actionDescription;
    private LocalDateTime time;

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActionDescription() { return actionDescription; }
    public void setActionDescription(String actionDescription) { this.actionDescription = actionDescription; }
    public LocalDateTime getTime() { return time; }
    public void setTime(LocalDateTime time) { this.time = time; }
}
