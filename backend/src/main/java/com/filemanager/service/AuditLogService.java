package com.filemanager.service;

import com.filemanager.entity.User;
import com.filemanager.entity.UserLog;
import com.filemanager.repository.UserLogRepository;
import com.filemanager.repository.UserRepository;
import com.filemanager.util.RequestInfoProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final UserLogRepository userLogRepository;
    private final UserRepository userRepository;
    private final RequestInfoProvider requestInfoProvider;

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
            User user = null;
            if (userId != null) {
                user = userRepository.findById(userId).orElse(null);
            }
            if (user == null) {
                // 无法归属用户时，不写入（当前表 user_id 非空约束）
                return;
            }
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
        } catch (Exception ignore) {
            // 审计日志失败不影响主流程
        }
    }
}

