package com.filemanager.controller;

import com.filemanager.entity.User;
import com.filemanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    
    private final UserService userService;
    
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/users/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String keyword) {
        List<User> users = userService.searchUsers(keyword);
        return ResponseEntity.ok(users);
    }
    
    @PostMapping("/users")
    public ResponseEntity<Map<String, String>> createUser(@RequestBody User user) {
        userService.createUser(user);
        return ResponseEntity.ok(Map.of("message", "用户创建成功"));
    }
    
    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userService.updateUser(user);
        return ResponseEntity.ok(Map.of("message", "用户更新成功"));
    }
    
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "用户删除成功"));
    }
    
    // 导入导出功能暂时禁用
    // @PostMapping("/users/import")
    // public ResponseEntity<Map<String, String>> importUsers(@RequestParam("file") MultipartFile file) {
    //     userService.importUsers(file);
    //     return ResponseEntity.ok(Map.of("message", "用户导入成功"));
    // }
    // 
    // @GetMapping("/users/export")
    // public ResponseEntity<byte[]> exportUsers() {
    //     byte[] excelData = userService.exportUsersToExcel();
    //     return ResponseEntity.ok()
    //             .header("Content-Disposition", "attachment; filename=users.xlsx")
    //             .body(excelData);
    // }
}
