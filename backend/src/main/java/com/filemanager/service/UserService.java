package com.filemanager.service;

import com.filemanager.dto.UserLoginDTO;
import com.filemanager.dto.UserRegisterDTO;
import com.filemanager.entity.User;
import com.filemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filemanager.security.JwtUtils;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().toString())))
                .accountExpired(false)
                .accountLocked(user.getLocked())
                .credentialsExpired(false)
                .disabled(!user.getEnabled())
                .build();
    }
    
    public User register(UserRegisterDTO registerDTO) {
        // 验证密码
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new RuntimeException("两次密码不一致");
        }
        
        // 检查用户名是否已存在
        if (userRepository.findByUsername(registerDTO.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (userRepository.findByEmail(registerDTO.getEmail()).isPresent()) {
            throw new RuntimeException("邮箱已被使用");
        }
        
        // 创建用户
        User user = User.builder()
                .username(registerDTO.getUsername())
                .password(passwordEncoder.encode(registerDTO.getPassword()))
                .email(registerDTO.getEmail())
                .phoneNumber(registerDTO.getPhone())
                .enabled(true)
                .locked(false)
                .loginAttempts(0)
                .role(User.Role.USER)
                .build();
        
        return userRepository.save(user);
    }
    
    public String login(UserLoginDTO loginDTO) {
        User user = userRepository.findByUsername(loginDTO.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));
        
        // 检查用户状态
        if (!user.getEnabled()) {
            throw new RuntimeException("用户已被禁用");
        }
        
        if (user.getLocked()) {
            throw new RuntimeException("用户已被锁定");
        }
        
        // 验证密码
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword())
            );
            
            // 重置登录尝试次数
            user.setLoginAttempts(0);
            user.setLastLoginTime(java.time.LocalDateTime.now());
            userRepository.save(user);
            
            // 生成JWT token
            return jwtUtils.generateToken((UserDetails) authentication.getPrincipal());
            
        } catch (Exception e) {
            // 增加登录尝试次数
            user.setLoginAttempts(user.getLoginAttempts() + 1);
            
            // 如果尝试次数超过5次，锁定账户
            if (user.getLoginAttempts() >= 5) {
                user.setLocked(true);
                userRepository.save(user);
                throw new RuntimeException("账户已被锁定，请联系管理员");
            }
            
            userRepository.save(user);
            throw new RuntimeException("用户名或密码错误");
        }
    }
    
    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
    
    public void updateUserProfile(User user) {
        userRepository.save(user);
    }
    
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
    
    // 管理员登录方法
    public String adminLogin(UserLoginDTO loginDTO) {
        User user = userRepository.findByUsername(loginDTO.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));
        
        // 检查是否为管理员
        if (user.getRole() != User.Role.ROLE_ADMIN) {
            throw new RuntimeException("权限不足");
        }
        
        // 验证密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 重置登录尝试次数
        user.setLoginAttempts(0);
        user.setLastLoginTime(java.time.LocalDateTime.now());
        userRepository.save(user);
        
        // 生成JWT token
        UserDetails userDetails = loadUserByUsername(user.getUsername());
        return jwtUtils.generateToken(userDetails);
    }
    
    // 根据用户名获取用户ID
    public Long getUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + username));
    }
    
    // 管理员方法：获取所有用户
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    // 管理员方法：搜索用户
    public List<User> searchUsers(String keyword) {
        return userRepository.findByUsernameContainingOrEmailContaining(keyword, keyword);
    }
    
    // 管理员方法：创建用户
    public User createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }
    
    // 管理员方法：更新用户
    public User updateUser(User user) {
        return userRepository.save(user);
    }
    
    // 管理员方法：删除用户
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}
