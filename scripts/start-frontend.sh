#!/bin/bash

set -e

echo "=== 启动前端服务 ==="

# 等待后端服务启动
echo "等待后端服务启动..."
sleep 30

# 检查后端服务
while ! curl -f http://localhost:8080/actuator/health &>/dev/null; do
    echo "等待后端服务就绪..."
    sleep 2
done

echo "后端服务已就绪，开始启动前端服务..."

# 进入前端目录
cd /app/frontend

# 安装依赖
echo "安装前端依赖..."
npm install

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
service nginx start || nginx -g "daemon on;"

# 启动前端开发服务器
echo "启动前端开发服务器..."
npm run serve -- --host 0.0.0.0 --port 3000 &

# 等待前端服务启动
echo "等待前端服务启动..."
sleep 30

# 检查前端服务
echo "检查前端服务状态..."
if curl -f http://localhost:3000 &>/dev/null; then
    echo "✅ 前端服务启动成功"
else
    echo "❌ 前端服务启动失败"
fi

echo "=== 前端服务启动完成 ==="