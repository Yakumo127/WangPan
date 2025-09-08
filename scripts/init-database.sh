#!/bin/bash

set -e

echo "=== 初始化数据库 ==="

# 等待MySQL启动
echo "等待MySQL启动..."
sleep 10

# 检查MySQL是否运行
while ! mysqladmin ping -h localhost --silent; do
    echo "等待MySQL启动..."
    sleep 2
done

echo "MySQL已启动，开始初始化数据库..."

# 创建数据库
mysql -u root -e "CREATE DATABASE IF NOT EXISTS enterprise_file_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" || echo "数据库已存在"

# 创建应用用户
mysql -u root -e "CREATE USER IF NOT EXISTS 'filemanager'@'localhost' IDENTIFIED BY 'filemanager_password';" || echo "用户已存在"

# 授权用户
mysql -u root -e "GRANT ALL PRIVILEGES ON enterprise_file_manager.* TO 'filemanager'@'localhost';" || echo "授权已存在"

# 刷新权限
mysql -u root -e "FLUSH PRIVILEGES;" || echo "权限已刷新"

# 创建Redis密码配置
echo "requirepass redispassword" >> /etc/redis/redis.conf

# 重启Redis
redis-cli SHUTDOWN NOSAVE || echo "Redis未运行"
redis-server /etc/redis/redis.conf &

echo "=== 数据库初始化完成 ==="

# 验证连接
echo "验证数据库连接..."
mysql -u filemanager -pfilemanager_password -e "USE enterprise_file_manager; SHOW TABLES;" || echo "连接验证失败"

echo "=== 初始化完成 ==="