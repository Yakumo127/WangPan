package com.filemanager.service;

import com.filemanager.entity.User;
import com.filemanager.entity.UserLog;
import com.filemanager.repository.UserLogRepository;
import com.filemanager.repository.UserRepository;
import com.filemanager.util.RequestInfoProvider;
import lombok.RequiredArgsConstructor;
import com.filemanager.metrics.AuditMetricsService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final UserLogRepository userLogRepository;
    private final UserRepository userRepository;
    private final RequestInfoProvider requestInfoProvider;
    private final AuditMetricsService metrics;

    @Async
    public void logSuccess(Long userId,
                           String actionType,
                           String resourceType,
                           Long resourceId,
                           String resourceName,
                           String description,
                           Long execMs) {
        save(userId, actionType, resourceType, resourceId, resourceName, description,
                UserLog.STATUS_SUCCESS, null, execMs);
    }

    @Async
    public void logFailure(Long userId,
                           String actionType,
                           String resourceType,
                           Long resourceId,
                           String resourceName,
                           String description,
                           String errorMessage,
                           Long execMs) {
        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        save(userId, actionType, resourceType, resourceId, resourceName, description,
                UserLog.STATUS_FAILED, errorMessage, execMs);
    }

    @Async
    public void logSystemSuccess(String actionType,
                                 String resourceType,
                                 Long resourceId,
                                 String resourceName,
                                 String description,
                                 Long execMs) {
        save(null, actionType, resourceType, resourceId, resourceName, description, UserLog.STATUS_SUCCESS, null, execMs);
    }

    @Async
    public void logSystemFailure(String actionType,
                                 String resourceType,
                                 Long resourceId,
                                 String resourceName,
                                 String description,
                                 String errorMessage,
                                 Long execMs) {
        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        save(null, actionType, resourceType, resourceId, resourceName, description, UserLog.STATUS_FAILED, errorMessage, execMs);
    }

    private void save(Long userId,
                      String actionType,
                      String resourceType,
                      Long resourceId,
                      String resourceName,
                      String description,
                      String status,
                      String errorMessage,
                      Long execMs) {
        try {
            User user = resolveUserForAudit(userId);
            if (user == null) return;
            UserLog log = new UserLog();
            log.setUser(user);
            log.setActionType(actionType);
            log.setActionDescription(description);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setResourceName(resourceName);
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            log.setExecutionTime(execMs);
            log.setIpAddress(requestInfoProvider.getClientIp());
            log.setUserAgent(requestInfoProvider.getUserAgent());
            userLogRepository.save(log);
            if (UserLog.STATUS_SUCCESS.equals(status)) metrics.incSuccess(); else metrics.incFailure();
        } catch (Exception ignore) {
            // 审计日志失败不影响主流程
        }
    }

    private User resolveUserForAudit(Long userId) {
        if (userId != null) {
            return userRepository.findById(userId).orElse(null);
        }
        // 系统级日志：尝试使用 system 用户；若不存在，降级使用 admin
        User sys = userRepository.findByUsername("system").orElse(null);
        if (sys != null) return sys;
        return userRepository.findByUsername("admin").orElse(null);
    }
}
