package com.filemanager.service;

import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {
    // key -> answerLowerCase
    private final ConcurrentHashMap<String, String> captchaCache = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // 生成带key的验证码，返回 key 与 文本
    public java.util.Map.Entry<String, String> generateCaptchaPair() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder captcha = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            captcha.append(chars.charAt(random.nextInt(chars.length())));
        }
        String captchaText = captcha.toString();
        String answer = captchaText.toLowerCase();
        String key = UUID.randomUUID().toString();
        captchaCache.put(key, answer);

        // TTL 5分钟
        new Thread(() -> {
            try {
                Thread.sleep(5 * 60 * 1000);
                captchaCache.remove(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return new java.util.AbstractMap.SimpleEntry<>(key, captchaText);
    }

    // 兼容：旧逻辑仅生成文本（不建议新代码使用）
    public String generateCaptcha() {
        return generateCaptchaPair().getValue();
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
    
    // 新：带key校验（一次性）
    public boolean validateCaptcha(String key, String captcha) {
        if (key == null || key.isBlank() || captcha == null || captcha.trim().isEmpty()) {
            return false;
        }
        String answer = captchaCache.get(key);
        if (answer == null) {
            return false;
        }
        boolean ok = answer.equals(captcha.toLowerCase());
        if (ok) captchaCache.remove(key);
        return ok;
    }
}
