package com.filemanager;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String storedHash = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVYITi";
        
        System.out.println("Testing password 'secret': " + encoder.matches("secret", storedHash));
        System.out.println("Testing password 'admin123': " + encoder.matches("admin123", storedHash));
        
        // Generate hash for 'admin123'
        System.out.println("Hash for 'admin123': " + encoder.encode("admin123"));
    }
}