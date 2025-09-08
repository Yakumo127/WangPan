-- MySQL 初始化脚本
-- 创建数据库和用户

CREATE DATABASE IF NOT EXISTS enterprise_file_manager 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- 创建用户并指定认证插件
CREATE USER IF NOT EXISTS 'filemanager'@'localhost' 
IDENTIFIED WITH mysql_native_password 
BY 'filemanager_password';

-- 授予权限
GRANT ALL PRIVILEGES ON enterprise_file_manager.* TO 'filemanager'@'localhost';
FLUSH PRIVILEGES;

-- 设置全局变量
SET GLOBAL default_authentication_plugin = mysql_native_password;
SET GLOBAL caching_sha2_password_public_key_retrieval_enabled = ON;

-- 使用数据库
USE enterprise_file_manager;