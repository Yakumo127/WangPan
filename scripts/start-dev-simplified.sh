#!/bin/bash

set -e

echo "=== 启动开发环境 ==="

# 启动MySQL
echo "启动MySQL..."
service mysql start || echo "MySQL启动失败，尝试其他方式"

# 等待MySQL启动
echo "等待MySQL启动..."
sleep 10

# 创建数据库
echo "创建数据库..."
mysql -u root -e "CREATE DATABASE IF NOT EXISTS filemanager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" || echo "数据库创建失败"

# 创建用户并授权
mysql -u root -e "CREATE USER IF NOT EXISTS 'appuser'@'localhost' IDENTIFIED BY 'apppassword';" || echo "用户创建失败"
mysql -u root -e "GRANT ALL PRIVILEGES ON filemanager.* TO 'appuser'@'localhost';" || echo "用户授权失败"
mysql -u root -e "FLUSH PRIVILEGES;" || echo "权限刷新失败"

# 配置Nginx
echo "配置Nginx..."
cat > /etc/nginx/sites-available/default << 'EOF'
server {
    listen 80;
    server_name localhost;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

# 启动Nginx
echo "启动Nginx..."
service nginx start || echo "Nginx启动失败"

# 构建后端
echo "构建后端..."
cd /app/backend
mvn clean compile -DskipTests || echo "后端编译失败"

# 构建前端
echo "构建前端..."
cd /app/frontend
npm install || echo "前端依赖安装失败"
npm run serve -- --host 0.0.0.0 --port 3000 &

# 启动后端
echo "启动后端..."
cd /app/backend
mvn spring-boot:run &

# 等待服务启动
echo "等待服务启动..."
sleep 60

# 检查服务状态
echo "检查服务状态..."
curl -f http://localhost:8080/actuator/health || echo "后端服务启动失败"
curl -f http://localhost:3000 || echo "前端服务启动失败"

echo "=== 开发环境启动完成 ==="
echo "前端访问地址: http://localhost:3000"
echo "后端API地址: http://localhost:8080"
echo "数据库地址: localhost:3306"

# 保持容器运行
tail -f /dev/null