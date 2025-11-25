package com.filemanager.controller;

import com.filemanager.dto.DashboardSummaryDTO;
import com.filemanager.service.DashboardService;
import com.filemanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDTO> getSummary(
            @RequestParam(value = "activityLimit", defaultValue = "5") int activityLimit,
            @RequestParam(value = "uploadLimit", defaultValue = "5") int uploadLimit
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long userId = userService.getUserIdByUsername(auth.getName());
        DashboardSummaryDTO dto = dashboardService.getUserSummary(userId, activityLimit, uploadLimit);
        return ResponseEntity.ok(dto);
    }
}
