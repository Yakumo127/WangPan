package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;
    
    @Column(name = "action_description", length = 1000)
    private String actionDescription;
    
    @Column(name = "resource_type", length = 50)
    private String resourceType;
    
    @Column(name = "resource_id")
    private Long resourceId;
    
    @Column(name = "resource_name", length = 255)
    private String resourceName;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "status", length = 20)
    private String status;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @Column(name = "execution_time")
    private Long executionTime; // 执行时间（毫秒）
    
    @CreationTimestamp
    private LocalDateTime createTime;
    
    // 操作类型常量
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_UPLOAD = "UPLOAD";
    public static final String ACTION_DOWNLOAD = "DOWNLOAD";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_MOVE = "MOVE";
    public static final String ACTION_COPY = "COPY";
    public static final String ACTION_RENAME = "RENAME";
    public static final String ACTION_CREATE_FOLDER = "CREATE_FOLDER";
    public static final String ACTION_DELETE_FOLDER = "DELETE_FOLDER";
    public static final String ACTION_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    public static final String ACTION_UPDATE_PROFILE = "UPDATE_PROFILE";
    
    // 资源类型常量
    public static final String RESOURCE_FILE = "FILE";
    public static final String RESOURCE_FOLDER = "FOLDER";
    public static final String RESOURCE_USER = "USER";
    
    // 状态常量
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PENDING = "PENDING";
    
    // 手动添加getter和setter方法以确保编译通过
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    
    public String getActionDescription() { return actionDescription; }
    public void setActionDescription(String actionDescription) { this.actionDescription = actionDescription; }
    
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Long getExecutionTime() { return executionTime; }
    public void setExecutionTime(Long executionTime) { this.executionTime = executionTime; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}