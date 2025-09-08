package com.filemanager.service;

import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {
    
    private final ConcurrentHashMap<String, String> captchaCache = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    public String generateCaptcha() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder captcha = new StringBuilder();
        
        for (int i = 0; i < 6; i++) {
            captcha.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        String captchaText = captcha.toString();
        captchaCache.put(captchaText, captchaText);
        
        // 5分钟后清除验证码
        new Thread(() -> {
            try {
                Thread.sleep(5 * 60 * 1000);
                captchaCache.remove(captchaText);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        return captchaText;
    }
    
    public BufferedImage createCaptchaImage(String captchaText) {
        int width = 120;
        int height = 40;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // 设置背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // 添加干扰线
        for (int i = 0; i < 5; i++) {
            g2d.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            g2d.drawLine(random.nextInt(width), random.nextInt(height), 
                        random.nextInt(width), random.nextInt(height));
        }
        
        // 绘制验证码文字
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.setColor(Color.BLACK);
        
        for (int i = 0; i < captchaText.length(); i++) {
            String c = captchaText.substring(i, i + 1);
            int x = 15 + i * 15;
            int y = 25 + random.nextInt(10);
            double angle = (random.nextDouble() - 0.5) * 0.5;
            
            g2d.translate(x, y);
            g2d.rotate(angle);
            g2d.drawString(c, 0, 0);
            g2d.rotate(-angle);
            g2d.translate(-x, -y);
        }
        
        // 添加干扰点
        for (int i = 0; i < 50; i++) {
            g2d.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            g2d.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
        }
        
        g2d.dispose();
        return image;
    }
    
    public boolean validateCaptcha(String captcha) {
        if (captcha == null || captcha.trim().isEmpty()) {
            return false;
        }
        
        boolean isValid = captchaCache.containsKey(captcha.toLowerCase());
        if (isValid) {
            captchaCache.remove(captcha.toLowerCase());
        }
        
        return isValid;
    }
}