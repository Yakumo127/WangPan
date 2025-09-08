package com.filemanager.security;

import com.filemanager.service.UserService;
import com.filemanager.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {
    
    private final UserService userService;
    
    public SecurityUtils(UserService userService) {
        this.userService = userService;
    }
    
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("用户未登录");
        }
        
        String username = auth.getName();
        User user = userService.getCurrentUser(username);
        return user.getId();
    }
    
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("用户未登录");
        }
        return auth.getName();
    }
    
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        
        String username = auth.getName();
        User user = userService.getCurrentUser(username);
        return user.getRole() == User.Role.ROLE_ADMIN;
    }
}