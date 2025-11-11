package com.filemanager.audit;

import com.filemanager.service.AuditLogService;
import com.filemanager.repository.UserRepository;
import com.filemanager.util.RequestInfoProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Aspect
@Component
@RequiredArgsConstructor
public class ApiAuditAspect {

    private final AuditLogService auditLogService;
    private final RequestInfoProvider requestInfoProvider;
    private final UserRepository userRepository;

    @Value("${audit.success-fallback.enabled:false}")
    private boolean successFallbackEnabled;
    @Value("${audit.success-fallback.only-get:true}")
    private boolean successOnlyGet;

    private static final String ATTR_START = "AUDIT_API_START";

    @Before("within(com.filemanager.controller..*)")
    public void onEnter(JoinPoint jp) {
        try {
            HttpServletRequest req = requestInfoProvider.getCurrentRequest();
            if (req != null) {
                req.setAttribute(ATTR_START, System.currentTimeMillis());
            }
        } catch (Exception ignore) {}
    }

    // 控制器异常兜底：记录失败请求，避免遗漏
    @AfterThrowing(pointcut = "within(com.filemanager.controller..*)", throwing = "ex")
    public void onControllerException(JoinPoint jp, Throwable ex) {
        try {
            // 仅在已认证用户下记录，以满足 user_id 非空约束
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || "anonymousUser".equalsIgnoreCase(auth.getName())) {
                return;
            }
            Long userId = userRepository.findByUsername(auth.getName()).map(u -> u.getId()).orElse(null);
            if (userId == null) return;
            HttpServletRequest req = requestInfoProvider.getCurrentRequest();
            if (req == null) return;
            String method = req.getMethod();
            String path = req.getRequestURI();
            String action = "API_" + method;
            String desc = "API调用失败：" + path;
            Long start = (Long) req.getAttribute(ATTR_START);
            long cost = start == null ? 0L : (System.currentTimeMillis() - start);
            try { auditLogService.logFailure(userId, action, "API", null, path, desc, ex.getMessage(), cost); } catch (Exception ignore) {}
        } catch (Exception ignore) {
        }
    }

    @AfterReturning(pointcut = "within(com.filemanager.controller..*)")
    public void onControllerSuccess(JoinPoint jp) {
        if (!successFallbackEnabled) return;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || "anonymousUser".equalsIgnoreCase(auth.getName())) {
                return;
            }
            HttpServletRequest req = requestInfoProvider.getCurrentRequest();
            if (req == null) return;
            if (successOnlyGet && !"GET".equalsIgnoreCase(req.getMethod())) return;
            Long userId = userRepository.findByUsername(auth.getName()).map(u -> u.getId()).orElse(null);
            if (userId == null) return;
            String method = req.getMethod();
            String path = req.getRequestURI();
            String action = "API_" + method;
            String desc = "API调用成功：" + path;
            Long start = (Long) req.getAttribute(ATTR_START);
            long cost = start == null ? 0L : (System.currentTimeMillis() - start);
            auditLogService.logSuccess(userId, action, "API", null, path, desc, cost);
        } catch (Exception ignore) {}
    }
}
