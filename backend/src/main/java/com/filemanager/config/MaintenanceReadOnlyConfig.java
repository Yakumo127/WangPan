package com.filemanager.config;

import com.filemanager.service.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MaintenanceReadOnlyConfig implements WebMvcConfigurer {

    private final SystemSettingService systemSettingService;

    public MaintenanceReadOnlyConfig(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                boolean maintenance = false;
                try { maintenance = systemSettingService.isMaintenanceEnabled(); } catch (Exception ignore) {}
                if (!maintenance) return true;

                String uri = request.getRequestURI();
                String method = request.getMethod();
                String level = "write-only";
                try { level = systemSettingService.getMaintenanceLevelOrDefault("write-only"); } catch (Exception ignore) {}

                // 允许的备份/维护接口
                boolean allow = uri.startsWith("/api/admin/settings/backup/export")
                        || uri.startsWith("/api/admin/settings/backup/export-to-server")
                        || uri.startsWith("/api/admin/settings/backup/precheck")
                        || uri.startsWith("/api/admin/settings/backup/import")
                        || uri.startsWith("/api/admin/settings/backup/config")
                        || uri.startsWith("/api/admin/settings/backup/jobs")
                        || uri.startsWith("/api/admin/settings/maintenance");
                if (allow) return true;

                if ("all".equalsIgnoreCase(level)) {
                    // 全局维护：阻断除“允许列表”外的所有请求
                    return reject(response);
                }

                // write-only：仅阻断写操作
                boolean write = !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method));
                if (!write) return true;
                return reject(response);
            }

            private boolean reject(HttpServletResponse response) throws Exception {
                response.setStatus(423);
                response.setContentType("application/json;charset=utf-8");
                String msg = "{\"code\":\"MAINTENANCE_READONLY\",\"message\":\"系统维护中（只读），请稍后再试\"}";
                response.getWriter().write(msg);
                return false;
            }
        }).addPathPatterns("/**");
    }
}
