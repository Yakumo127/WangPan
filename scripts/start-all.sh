#!/bin/bash

set -e

echo "=== 启动完整应用环境 ==="

# 等待数据库服务启动
echo "等待数据库服务启动..."
sleep 20

# 检查MySQL连接
echo "检查MySQL连接..."
while ! mysql -h mysql -u filemanager -pfilemanager_password -e "USE enterprise_file_manager; SELECT 1;" &>/dev/null; do
    echo "等待MySQL就绪..."
    sleep 2
done

echo "✅ MySQL已就绪"

# 检查Redis连接
echo "检查Redis连接..."
while ! redis-cli -h redis -a redispassword ping &>/dev/null; do
    echo "等待Redis就绪..."
    sleep 2
done

echo "✅ Redis已就绪"

# 配置应用
echo "配置应用..."
cp /app/scripts/application-docker.yml /app/backend/src/main/resources/application.yml

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
nginx -g "daemon off;" &

# 构建并启动前端
echo "构建前端服务..."
cd /app/frontend
npm install
npm run build

# 启动前端静态服务器
echo "启动前端服务..."
npm install -g serve
serve -s dist -l 3000 &

# 构建并启动后端
echo "构建后端服务..."
cd /app/backend
mvn clean compile -DskipTests

# 启动后端服务
echo "启动后端服务..."
mvn spring-boot:run &

# 等待服务启动
echo "等待服务启动..."
sleep 60

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

echo "=== 应用环境启动完成 ==="
echo "前端访问地址: http://localhost:3000"
echo "后端API地址: http://localhost:8080"
echo "MySQL地址: mysql:3306"
echo "Redis地址: redis:6379"

# 保持容器运行
tail -f /dev/null