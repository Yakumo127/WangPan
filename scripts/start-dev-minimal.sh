#!/bin/bash

set -e

echo "=== 启动最小化开发环境 ==="

# 创建配置文件
echo "创建应用配置文件..."
cat > /app/scripts/application-dev.yml << 'EOF'
spring:
  application:
    name: enterprise-file-manager
  datasource:
    url: jdbc:mysql://host.docker.internal:3306/filemanager?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC
    username: appuser
    password: apppassword
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQL8Dialect
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB

server:
  port: 8080
  servlet:
    context-path: /

logging:
  level:
    com.filemanager: DEBUG
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

file:
  storage:
    default-location: /cunchu
    max-file-size: 10485760  # 10MB
    chunk-size: 1048576     # 1MB

jwt:
  secret: mySecretKey123456789
  expiration: 86400000    # 24小时

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
EOF

# 构建后端
echo "构建后端..."
cd /app/backend
mvn clean compile -DskipTests

# 构建前端
echo "构建前端..."
cd /app/frontend
npm install
npm run serve -- --host 0.0.0.0 --port 3000 &

# 启动后端
echo "启动后端..."
cd /app/backend
mvn spring-boot:run &

# 等待服务启动
echo "等待服务启动..."
sleep 30

# 检查服务状态
echo "检查服务状态..."
curl -f http://localhost:8080/actuator/health || echo "后端服务启动失败"
curl -f http://localhost:3000 || echo "前端服务启动失败"

echo "=== 开发环境启动完成 ==="
echo "前端访问地址: http://localhost:3000"
echo "后端API地址: http://localhost:8080"
echo "注意：需要外部MySQL服务支持"

# 保持容器运行
tail -f /dev/null