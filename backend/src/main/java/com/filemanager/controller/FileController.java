package com.filemanager.controller;

import com.filemanager.entity.File;
import com.filemanager.entity.Folder;
import com.filemanager.service.FileService;
import com.filemanager.service.FolderService;
import com.filemanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    
    private final FileService fileService;
    private final FolderService folderService;
    private final UserService userService;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            File uploadedFile = fileService.uploadFile(file, userId, folderId);
            
            return ResponseEntity.ok(Map.of(
                "message", "文件上传成功",
                "fileId", uploadedFile.getId(),
                "filename", uploadedFile.getOriginalFilename(),
                "size", uploadedFile.getSize(),
                "uploadTime", uploadedFile.getCreateTime()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    @GetMapping("/list")
    public ResponseEntity<List<File>> getUserFiles(
            @RequestParam(value = "folderId", required = false) Long folderId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            List<File> files = fileService.getUserFiles(userId, folderId);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            Path filePath = fileService.getFilePath(fileId, userId);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                File file = fileService.getFile(fileId, userId);
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.getContentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable Long fileId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            fileService.deleteFile(fileId, userId);
            return ResponseEntity.ok(Map.of("message", "文件删除成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    @PutMapping("/{fileId}/rename")
    public ResponseEntity<Map<String, Object>> renameFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            String newName = request.get("name");
            File renamedFile = fileService.renameFile(fileId, userId, newName);
            
            return ResponseEntity.ok(Map.of(
                "message", "文件重命名成功",
                "fileId", renamedFile.getId(),
                "filename", renamedFile.getOriginalFilename()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    @PostMapping("/{fileId}/move")
    public ResponseEntity<Map<String, Object>> moveFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, Object> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            Long targetFolderId = request.get("folderId") != null ? 
                Long.parseLong(request.get("folderId").toString()) : null;
            
            File movedFile = fileService.moveFile(fileId, userId, targetFolderId);
            
            return ResponseEntity.ok(Map.of(
                "message", "文件移动成功",
                "fileId", movedFile.getId(),
                "folderId", movedFile.getFolder() != null ? movedFile.getFolder().getId() : null
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    @PostMapping("/{fileId}/copy")
    public ResponseEntity<Map<String, Object>> copyFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, Object> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            Long targetFolderId = request.get("folderId") != null ? 
                Long.parseLong(request.get("folderId").toString()) : null;
            
            File copiedFile = fileService.copyFile(fileId, userId, targetFolderId);
            
            return ResponseEntity.ok(Map.of(
                "message", "文件复制成功",
                "fileId", copiedFile.getId(),
                "filename", copiedFile.getOriginalFilename()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    // 回收站相关API
    @GetMapping("/recycle/bin")
    public ResponseEntity<List<File>> getRecycleBinFiles() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            List<File> files = fileService.getUserRecycleBinFiles(userId);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<File>> searchFiles(@RequestParam String keyword) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            List<File> files = fileService.searchFiles(userId, keyword);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    // 恢复文件

    // 管理员回收站相关API
    @GetMapping("/admin/recycle/bin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<File>> getAllRecycleBinFiles() {
        try {
            List<File> files = fileService.getAllRecycleBinFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/admin/{fileId}/restore")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> adminRestoreFile(@PathVariable Long fileId) {
        try {
            fileService.adminRestoreFile(fileId);
            return ResponseEntity.ok(Map.of("message", "文件恢复成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/admin/recycle/bin/{fileId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> adminPermanentDeleteFile(@PathVariable Long fileId) {
        try {
            fileService.adminPermanentDeleteFile(fileId);
            return ResponseEntity.ok(Map.of("message", "文件彻底删除成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/admin/recycle/bin/empty")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> adminEmptyAllRecycleBin() {
        try {
            fileService.adminEmptyAllRecycleBin();
            return ResponseEntity.ok(Map.of("message", "所有回收站清空成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
