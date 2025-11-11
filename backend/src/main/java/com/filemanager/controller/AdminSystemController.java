package com.filemanager.controller;

import com.filemanager.service.SystemSettingService;
import com.filemanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminSystemController {

    private final SystemSettingService systemSettingService;
    private final UserService userService;

    // 获取回收站相关设置
    @GetMapping("/recycle")
    public Map<String, Object> getRecycleSettings() {
        boolean manualEnabled = systemSettingService.isManualPurgeEnabled();
        return Map.of(
                "manualPurgeEnabled", manualEnabled
        );
    }

    // 更新回收站相关设置（目前支持：手动清理到期开关）
    @PutMapping("/recycle")
    public Map<String, Object> updateRecycleSettings(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long adminId = userService.getUserIdByUsername(auth.getName());
        boolean manualEnabled = Boolean.TRUE.equals(body.get("manualPurgeEnabled"));
        systemSettingService.setBoolean(SystemSettingService.KEY_RECYCLE_MANUAL_PURGE_ENABLED, manualEnabled,
                userService.getUserById(adminId));
        return Map.of("message", "设置已更新", "manualPurgeEnabled", manualEnabled);
    }
}

