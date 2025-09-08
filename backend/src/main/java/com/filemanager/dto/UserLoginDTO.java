package com.filemanager.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class UserLoginDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    private String password;
    
    private String captcha;
    
    // 手动添加getter和setter方法以确保编译通过
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getCaptcha() { return captcha; }
    public void setCaptcha(String captcha) { this.captcha = captcha; }
}