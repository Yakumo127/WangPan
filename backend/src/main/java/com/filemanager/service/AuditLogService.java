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
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final UserLogRepository userLogRepository;
    private final UserRepository userRepository;
    private final RequestInfoProvider requestInfoProvider;
    private final AuditMetricsService metrics;
    private final PasswordEncoder passwordEncoder;

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
            if (user == null) {
                log.warn("审计日志未写入：未找到可用的审计用户，action={}, resource={}, desc={}", actionType, resourceName, description);
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
            if (UserLog.STATUS_SUCCESS.equals(status)) metrics.incSuccess(); else metrics.incFailure();
        } catch (Exception e) {
            log.warn("审计日志写入失败 action={}, resourceType={}, resourceName={}, status={}, err={}",
                    actionType, resourceType, resourceName, status, e.getMessage(), e);
            // 审计日志失败不影响主流程，但需要可观测
        }
    }

    private User resolveUserForAudit(Long userId) {
        if (userId != null) {
            return userRepository.findById(userId).orElseGet(() -> {
                log.warn("审计日志指定的用户不存在，userId={}", userId);
                return ensureSystemUser();
            });
        }
        // 系统级日志：尝试使用 system 用户；若不存在，降级使用 admin
        return ensureSystemUser();
    }

    private User ensureSystemUser() {
        User sys = userRepository.findByUsername("system").orElse(null);
        if (sys != null) return sys;
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin != null) return admin;
        try {
            User created = new User();
            created.setUsername("system");
            created.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            created.setEmail("system@local");
            created.setDisplayName("system");
            created.setEnabled(true);
            created.setLocked(false);
            created.setLoginAttempts(0);
            created.setRole(User.Role.ADMIN);
            created.setQuotaUsed(0L);
            created.setQuotaLimit(created.getQuotaLimit() == null ? 1073741824L : created.getQuotaLimit());
            created = userRepository.save(created);
            log.info("已自动创建 system 审计账号用于系统级审计日志");
            return created;
        } catch (Exception e) {
            log.warn("自动创建 system 审计账号失败，err={}", e.getMessage(), e);
            return null;
        }
    }
}
