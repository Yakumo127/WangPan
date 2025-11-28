#!/bin/bash

set -e

echo "=== 启动 test08 项目开发环境 ==="

# 设置工作目录
cd /workspace

# 启动MySQL
echo "启动MySQL..."
mysqld --user=mysql --daemonize --pid-file=/var/run/mysqld/mysqld.pid

# 等待MySQL启动
echo "等待MySQL启动..."
sleep 10

# 创建数据库
echo "创建数据库..."
mysql -u root -e "CREATE DATABASE IF NOT EXISTS enterprise_file_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 创建用户并授权
mysql -u root -e "CREATE USER IF NOT EXISTS 'filemanager'@'localhost' IDENTIFIED BY 'filemanager_password';"
mysql -u root -e "GRANT ALL PRIVILEGES ON enterprise_file_manager.* TO 'filemanager'@'localhost';"
mysql -u root -e "FLUSH PRIVILEGES;"

# 导入数据库备份
echo "导入数据库备份..."
mysql -u root enterprise_file_manager < /workspace/database/enterprise_file_manager_backup.sql

echo "✅ 数据库初始化完成"

# 构建后端
echo "构建后端..."
cd /workspace/backend
mvn clean compile -DskipTests

# 启动后端
echo "启动后端服务..."
mvn spring-boot:run &
BACKEND_PID=$!

# 等待后端启动
echo "等待后端服务启动..."
sleep 30

# 构建前端
echo "构建前端..."
cd /workspace/frontend
npm install

# 启动前端开发服务器
echo "启动前端服务..."
npm run dev -- --host 0.0.0.0 --port 3000 &
FRONTEND_PID=$!

# 等待前端启动
echo "等待前端服务启动..."
sleep 20

# 检查服务状态
echo "检查服务状态..."
if curl -f http://localhost:8080/actuator/health &>/dev/null; then
    echo "✅ 后端服务启动成功"
else
    echo "❌ 后端服务启动失败"
fi

if curl -f http://localhost:3000 &>/dev/null; then
    echo "✅ 前端服务启动成功"
else
    echo "❌ 前端服务启动失败"
fi

echo "=== 项目启动完成 ==="
echo "前端访问地址: http://localhost:3000"
echo "后端API地址: http://localhost:8080"
echo "容器外部访问地址:"
echo "  前端: http://localhost:13000"
echo "  后端API: http://localhost:18080"
echo ""
echo "系统账户:"
echo "  管理员: admin / admin123"
echo "  普通用户: user / 123456"
echo ""
echo "按 Ctrl+C 停止所有服务"

# 等待用户中断
trap 'echo "正在停止服务..."; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0' INT

# 保持脚本运行
while true; do
    sleep 1
done