#!/bin/bash

echo "=== 启动 test08 项目 ==="

cd /workspace

# 1. 启动MySQL
echo "1. 启动MySQL..."
mysqld --user=mysql --daemonize --pid-file=/var/run/mysqld/mysqld.pid
sleep 10

# 2. 初始化数据库
echo "2. 初始化数据库..."
mysql -u root -e "CREATE DATABASE IF NOT EXISTS enterprise_file_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -e "CREATE USER IF NOT EXISTS 'filemanager'@'localhost' IDENTIFIED BY 'filemanager_password';"
mysql -u root -e "GRANT ALL PRIVILEGES ON enterprise_file_manager.* TO 'filemanager'@'localhost';"
mysql -u root -e "FLUSH PRIVILEGES;"
mysql -u root enterprise_file_manager < /workspace/database/enterprise_file_manager_backup.sql
echo "✅ 数据库初始化完成"

# 3. 启动后端
echo "3. 启动后端..."
cd /workspace/backend
nohup mvn spring-boot:run > /workspace/backend.log 2>&1 &
sleep 30

# 4. 启动前端
echo "4. 启动前端..."
cd /workspace/frontend
nohup npm run dev -- --host 0.0.0.0 --port 3000 > /workspace/frontend.log 2>&1 &
sleep 20

echo "=== 启动完成 ==="
echo "检查服务状态..."

# 检查后端
if curl -f http://localhost:8080/actuator/health &>/dev/null; then
    echo "✅ 后端服务正常"
else
    echo "❌ 后端服务异常"
fi

# 检查前端
if curl -f http://localhost:3000 &>/dev/null; then
    echo "✅ 前端服务正常"
else
    echo "❌ 前端服务异常"
fi

echo "访问地址:"
echo "  前端: http://localhost:13000"
echo "  后端API: http://localhost:18080"