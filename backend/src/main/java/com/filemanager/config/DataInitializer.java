package com.filemanager.config;

import com.filemanager.entity.User;
import com.filemanager.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    @Transactional
    public void init() {
        System.out.println("=== Initializing Admin User ===");
        
        // 检查是否已存在管理员用户
        if (!userRepository.findByUsername("admin").isPresent()) {
            System.out.println("Creating admin user...");
            
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123")); // 设置管理员密码
            admin.setEmail("admin@example.com");
            admin.setDisplayName("系统管理员");
            admin.setRole(User.Role.ROLE_ADMIN);
            admin.setEnabled(true);
            admin.setLocked(false);
            admin.setLoginAttempts(0);
            admin.setQuotaLimit(10737418240L); // 10GB
            
            userRepository.save(admin);
            System.out.println("Admin user created successfully!");
        } else {
            System.out.println("Admin user already exists");
            
            // 检查现有管理员用户的密码
            User existingAdmin = userRepository.findByUsername("admin").get();
            System.out.println("Current admin password hash: " + existingAdmin.getPassword());
            
            // 如果需要，重置密码
            if (!passwordEncoder.matches("admin123", existingAdmin.getPassword())) {
                System.out.println("Resetting admin password...");
                existingAdmin.setPassword(passwordEncoder.encode("admin123"));
                userRepository.save(existingAdmin);
                System.out.println("Admin password reset successfully!");
            }
        }
        
        // 创建测试用户
        if (!userRepository.findByUsername("demo").isPresent()) {
            System.out.println("Creating demo user...");
            
            User demo = new User();
            demo.setUsername("demo");
            demo.setPassword(passwordEncoder.encode("demo123"));
            demo.setEmail("demo@example.com");
            demo.setDisplayName("测试用户");
            demo.setRole(User.Role.USER);
            demo.setEnabled(true);
            demo.setLocked(false);
            demo.setLoginAttempts(0);
            demo.setQuotaLimit(1073741824L); // 1GB
            
            userRepository.save(demo);
            System.out.println("Demo user created successfully!");
        }
        
        System.out.println("=== Initialization Complete ===");
    }
}