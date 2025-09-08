package com.filemanager.controller;

import com.filemanager.entity.Folder;
import com.filemanager.service.FolderService;
import com.filemanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {
    
    private final FolderService folderService;
    private final UserService userService;
    
    @PostMapping("/create")
    public Map<String, Object> createFolder(@RequestBody Map<String, Object> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            String name = (String) request.get("name");
            Long parentId = request.get("parentId") != null ? 
                Long.parseLong(request.get("parentId").toString()) : null;
            
            Folder folder = folderService.createFolder(name, parentId, userId);
            
            return Map.of(
                "message", "文件夹创建成功",
                "folderId", folder.getId(),
                "name", folder.getName(),
                "parentId", folder.getParent() != null ? folder.getParent().getId() : null,
                "createTime", folder.getCreateTime()
            );
        } catch (Exception e) {
            return Map.of("message", e.getMessage());
        }
    }
    
    @GetMapping("/list")
    public ResponseEntity<List<Folder>> getUserFolders(
            @RequestParam(value = "parentId", required = false) Long parentId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            List<Folder> folders = folderService.getUserFolders(userId, parentId);
            return ResponseEntity.ok(folders);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/{folderId}")
    public Map<String, String> deleteFolder(@PathVariable Long folderId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            folderService.deleteFolder(folderId, userId);
            return Map.of("message", "文件夹删除成功");
        } catch (Exception e) {
            return Map.of("message", e.getMessage());
        }
    }
    
    @PutMapping("/{folderId}/rename")
    public Map<String, Object> renameFolder(
            @PathVariable Long folderId,
            @RequestBody Map<String, String> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            String newName = request.get("name");
            Folder folder = folderService.renameFolder(folderId, userId, newName);
            
            return Map.of(
                "message", "文件夹重命名成功",
                "folderId", folder.getId(),
                "name", folder.getName()
            );
        } catch (Exception e) {
            return Map.of("message", e.getMessage());
        }
    }
    
    @PostMapping("/{folderId}/move")
    public Map<String, Object> moveFolder(
            @PathVariable Long folderId,
            @RequestBody Map<String, Object> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            Long targetParentId = request.get("targetParentId") != null ? 
                Long.parseLong(request.get("targetParentId").toString()) : null;
            
            Folder folder = folderService.moveFolder(folderId, userId, targetParentId);
            
            return Map.of(
                "message", "文件夹移动成功",
                "folderId", folder.getId(),
                "parentId", folder.getParent() != null ? folder.getParent().getId() : null
            );
        } catch (Exception e) {
            return Map.of("message", e.getMessage());
        }
    }
    
    @GetMapping("/{folderId}/path")
    public ResponseEntity<List<Folder>> getFolderPath(@PathVariable Long folderId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userService.getUserIdByUsername(username);
            
            List<Folder> path = folderService.getFolderPath(folderId, userId);
            return ResponseEntity.ok(path);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}