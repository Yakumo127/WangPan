#!/bin/bash

set -e

echo "=== 启动后端服务 ==="

# 等待数据库初始化完成
echo "等待数据库初始化完成..."
sleep 30

# 检查数据库连接
echo "检查数据库连接..."
while ! mysql -u filemanager -pfilemanager_password -h localhost -e "USE enterprise_file_manager; SELECT 1;" &>/dev/null; do
    echo "等待数据库就绪..."
    sleep 2
done

echo "数据库已就绪，开始启动后端服务..."

# 复制配置文件
cp /app/scripts/application-dev.yml /app/backend/src/main/resources/application.yml

# 进入后端目录
cd /app/backend

# 构建项目
echo "构建后端项目..."
mvn clean compile -DskipTests

# 启动后端服务
echo "启动后端服务..."
mvn spring-boot:run &

# 等待服务启动
echo "等待后端服务启动..."
sleep 60

# 检查服务状态
echo "检查后端服务状态..."
if curl -f http://localhost:8080/actuator/health &>/dev/null; then
    echo "✅ 后端服务启动成功"
else
    echo "❌ 后端服务启动失败"
fi

echo "=== 后端服务启动完成 ==="