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
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final CaptchaService captchaService;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
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
        
        // 创建用户（避免依赖 Lombok builder）
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());
        user.setPhoneNumber(registerDTO.getPhone());
        user.setEnabled(true);
        user.setLocked(false);
        user.setLoginAttempts(0);
        user.setRole(User.Role.USER);
        
        return userRepository.save(user);
    }
    
    public String login(UserLoginDTO loginDTO) {
        User user = userRepository.findByUsername(loginDTO.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        // 账户状态检查
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new RuntimeException("用户已被禁用");
        }
        if (Boolean.TRUE.equals(user.getLocked())) {
            throw new RuntimeException("用户已被锁定");
        }

        // 验证码：3次失败后强制校验；否则若提供则校验
        boolean needCaptcha = user.getLoginAttempts() != null && user.getLoginAttempts() >= 3;
        String key = loginDTO.getCaptchaKey();
        String code = loginDTO.getCaptcha();
        if (needCaptcha) {
            if (key == null || key.isBlank() || code == null || code.isBlank()) {
                throw new RuntimeException("请先输入验证码");
            }
            if (!captchaService.validateCaptcha(key, code)) {
                throw new RuntimeException("验证码错误或已过期");
            }
        } else {
            if (key != null && !key.isBlank() && code != null && !code.isBlank()) {
                if (!captchaService.validateCaptcha(key, code)) {
                    throw new RuntimeException("验证码错误或已过期");
                }
            }
        }

        // 密码认证
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword())
            );

            // 成功：重置尝试次数
            user.setLoginAttempts(0);
            user.setLastLoginTime(java.time.LocalDateTime.now());
            userRepository.save(user);

            return jwtUtils.generateToken((UserDetails) authentication.getPrincipal());
        } catch (Exception e) {
            // 失败：累加尝试次数，5次锁定
            user.setLoginAttempts((user.getLoginAttempts() == null ? 0 : user.getLoginAttempts()) + 1);
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
    
    // 管理员登录方法（与普通登录一致的验证码策略）
    public String adminLogin(UserLoginDTO loginDTO) {
        User user = userRepository.findByUsername(loginDTO.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (user.getRole() != User.Role.ADMIN) {
            throw new RuntimeException("权限不足");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new RuntimeException("用户已被禁用");
        }
        if (Boolean.TRUE.equals(user.getLocked())) {
            throw new RuntimeException("用户已被锁定");
        }

        boolean needCaptcha = user.getLoginAttempts() != null && user.getLoginAttempts() >= 3;
        String key = loginDTO.getCaptchaKey();
        String code = loginDTO.getCaptcha();
        if (needCaptcha) {
            if (key == null || key.isBlank() || code == null || code.isBlank()) {
                throw new RuntimeException("请先输入验证码");
            }
            if (!captchaService.validateCaptcha(key, code)) {
                throw new RuntimeException("验证码错误或已过期");
            }
        } else {
            if (key != null && !key.isBlank() && code != null && !code.isBlank()) {
                if (!captchaService.validateCaptcha(key, code)) {
                    throw new RuntimeException("验证码错误或已过期");
                }
            }
        }

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            // 失败：累加尝试并可能锁定
            user.setLoginAttempts((user.getLoginAttempts() == null ? 0 : user.getLoginAttempts()) + 1);
            if (user.getLoginAttempts() >= 5) {
                user.setLocked(true);
                userRepository.save(user);
                throw new RuntimeException("账户已被锁定，请联系管理员");
            }
            userRepository.save(user);
            throw new RuntimeException("用户名或密码错误");
        }

        user.setLoginAttempts(0);
        user.setLastLoginTime(java.time.LocalDateTime.now());
        userRepository.save(user);

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

    // 导入用户（支持 .xlsx/.xls）
    public String importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("导入文件不能为空");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        boolean xlsx = filename.toLowerCase().endsWith(".xlsx");
        int created = 0, skipped = 0, updated = 0, failed = 0;
        try (InputStream is = file.getInputStream(); Workbook wb = xlsx ? new XSSFWorkbook(is) : new HSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new RuntimeException("Excel 内容为空");
            }
            // 头部约定：username | email | displayName | password | role | enabled
            boolean header = true;
            for (Row row : sheet) {
                if (row == null) continue;
                if (header) { header = false; continue; }
                String username = getCellString(row, 0);
                String email = getCellString(row, 1);
                String displayName = getCellString(row, 2);
                String password = getCellString(row, 3);
                String roleStr = getCellString(row, 4);
                String enabledStr = getCellString(row, 5);

                if (username == null || username.isBlank() || email == null || email.isBlank()) {
                    failed++; continue;
                }

                User.Role role = normalizeRole(roleStr);
                boolean enabled = enabledStr == null || enabledStr.isBlank() || Boolean.parseBoolean(enabledStr.trim());

                try {
                    User existing = userRepository.findByUsername(username).orElse(null);
                    if (existing == null) {
                        if (password == null || password.isBlank()) {
                            failed++; continue; // 新用户必须提供密码
                        }
                        User u = new User();
                        u.setUsername(username);
                        u.setPassword(passwordEncoder.encode(password));
                        u.setEmail(email);
                        u.setDisplayName(displayName);
                        u.setEnabled(enabled);
                        u.setLocked(false);
                        u.setLoginAttempts(0);
                        u.setRole(role);
                        userRepository.save(u);
                        created++;
                    } else {
                        // 更新基本字段（不强制要求提供密码）
                        if (displayName != null && !displayName.isBlank()) existing.setDisplayName(displayName);
                        if (email != null && !email.isBlank()) existing.setEmail(email);
                        existing.setEnabled(enabled);
                        existing.setRole(role);
                        if (password != null && !password.isBlank()) {
                            existing.setPassword(passwordEncoder.encode(password));
                        }
                        userRepository.save(existing);
                        updated++;
                    }
                } catch (Exception ex) {
                    failed++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
        return String.format("导入完成：新增 %d，更新 %d，跳过 %d，失败 %d", created, updated, skipped, failed);
    }

    // 导出用户为 Excel (.xlsx)
    public byte[] exportUsersToExcel() {
        List<User> users = userRepository.findAll();
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Users");
            int r = 0;
            // header
            Row h = sheet.createRow(r++);
            String[] headers = new String[]{"ID","Username","Email","DisplayName","Role","Enabled","Locked","CreateTime","LastLoginTime"};
            for (int i=0; i<headers.length; i++) { h.createCell(i).setCellValue(headers[i]); }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (User u : users) {
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(u.getId() == null ? 0 : u.getId());
                row.createCell(c++).setCellValue(nullToEmpty(u.getUsername()));
                row.createCell(c++).setCellValue(nullToEmpty(u.getEmail()));
                row.createCell(c++).setCellValue(nullToEmpty(u.getDisplayName()));
                row.createCell(c++).setCellValue(u.getRole() == null ? "" : u.getRole().toString());
                row.createCell(c++).setCellValue(Boolean.TRUE.equals(u.getEnabled()));
                row.createCell(c++).setCellValue(Boolean.TRUE.equals(u.getLocked()));
                row.createCell(c++).setCellValue(u.getCreateTime() == null ? "" : fmt.format(u.getCreateTime()));
                row.createCell(c++).setCellValue(u.getLastLoginTime() == null ? "" : fmt.format(u.getLastLoginTime()));
            }
            for (int i=0; i<headers.length; i++) { sheet.autoSizeColumn(i); }
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    private String getCellString(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        String v = cell.getStringCellValue();
        return v != null ? v.trim() : null;
    }

    private User.Role normalizeRole(String roleStr) {
        if (roleStr == null) return User.Role.USER;
        String r = roleStr.trim().toUpperCase();
        if ("ADMIN".equals(r) || "ROLE_ADMIN".equals(r)) return User.Role.ADMIN;
        return User.Role.USER;
    }

    // 生成导入模板（xlsx）
    public byte[] generateUsersImportTemplate() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("ImportTemplate");
            int r = 0;
            // header
            Row h = sheet.createRow(r++);
            String[] headers = new String[]{
                    "username","email","displayName","password","role","enabled"
            };
            for (int i=0; i<headers.length; i++) { h.createCell(i).setCellValue(headers[i]); }
            // 示例行1（新建管理员）
            Row s1 = sheet.createRow(r++);
            s1.createCell(0).setCellValue("admin2");
            s1.createCell(1).setCellValue("admin2@example.com");
            s1.createCell(2).setCellValue("管理员2");
            s1.createCell(3).setCellValue("Admin@123");
            s1.createCell(4).setCellValue("ROLE_ADMIN");
            s1.createCell(5).setCellValue("true");
            // 示例行2（新建普通用户）
            Row s2 = sheet.createRow(r++);
            s2.createCell(0).setCellValue("user2");
            s2.createCell(1).setCellValue("user2@example.com");
            s2.createCell(2).setCellValue("普通用户2");
            s2.createCell(3).setCellValue("User@123");
            s2.createCell(4).setCellValue("USER");
            s2.createCell(5).setCellValue("true");

            for (int i=0; i<headers.length; i++) { sheet.autoSizeColumn(i); }
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成模板失败: " + e.getMessage(), e);
        }
    }
}
