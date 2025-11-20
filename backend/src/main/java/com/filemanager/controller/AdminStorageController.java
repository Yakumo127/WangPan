package com.filemanager.controller;

import com.filemanager.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/storage")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminStorageController {

    private final FileService fileService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getStorageSummary() {
        Map<String, Object> summary = fileService.computeStorageSummary(1L);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/cleanup/garbage-chunks")
    public ResponseEntity<Map<String, Object>> cleanupGarbageChunks() {
        Map<String, Object> res = fileService.cleanupGarbageChunks(1L, null);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/cleanup/garbage-chunks/by-user")
    public ResponseEntity<Map<String, Object>> cleanupGarbageChunksByUser(@RequestBody Map<String, Object> body) {
        Long userId = null;
        Object v = body.get("userId");
        if (v instanceof Number n) {
            userId = n.longValue();
        } else if (v != null) {
            try { userId = Long.parseLong(v.toString()); } catch (Exception ignore) {}
        }
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "缺少 userId 参数"));
        }
        Map<String, Object> res = fileService.cleanupGarbageChunks(1L, userId);
        return ResponseEntity.ok(res);
    }
}

