package com.filemanager.controller;

import com.filemanager.dto.UserLogDTO;
import com.filemanager.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminLogController {

    private final AuditLogQueryService queryService;

    @GetMapping
    public Page<UserLogDTO> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actions,
            @RequestParam(required = false) String resourceTypes,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "createTime") String sort,
            @RequestParam(defaultValue = "desc") String order
    ) {
        Set<String> actionSet = split(actions);
        Set<String> rtypeSet = split(resourceTypes);
        Sort.Direction dir = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort s = Sort.by(dir, sort == null || sort.isBlank() ? "createTime" : sort);
        return queryService.query(actionSet, rtypeSet, status, keyword, from, to, page, size, s);
    }

    private Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return new HashSet<>(Arrays.asList(csv.split(",")));
    }
}

