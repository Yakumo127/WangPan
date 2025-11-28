#!/bin/bash

echo "=== 启动 test08 项目 (简化版) ==="

cd /workspace

# 1. 启动 Redis
echo "1. 启动 Redis..."
redis-server --daemonize yes --port 6379
sleep 3

# 2. 修改配置使用 H2 内存数据库
echo "2. 修改配置使用 H2 数据库..."
cat > /workspace/backend/src/main/resources/application.yml << 'EOF'
spring:
  application:
    name: enterprise-file-manager
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password: 
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.H2Dialect
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
    path: /tmp/cunchu
    default-location: /tmp/cunchu
    default-path: /tmp/cunchu
    max-file-size: 10485760  # 10MB
    chunk-size: 1048576     # 1MB
    temp-dir: /tmp/temp

jwt:
  secret: enterpriseFileManagerSecretKey2024
  expiration: 86400000    # 24小时

  redis:
    host: localhost
    port: 6379
    password: 
    database: 0

  cache:
    type: redis
    redis:
      time-to-live: 600000

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env
    base-path: /actuator
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  health:
    redis:
      enabled: true
    db:
      enabled: true
EOF

# 3. 添加 H2 依赖
echo "3. 添加 H2 依赖..."
sed -i '/<\/dependencies>/i\        <dependency>\n            <groupId>com.h2database<\/groupId>\n            <artifactId>h2<\/artifactId>\n            <scope>runtime<\/scope>\n        <\/dependency>' /workspace/backend/pom.xml

# 4. 创建存储目录
echo "4. 创建存储目录..."
mkdir -p /tmp/cunchu /tmp/temp

# 5. 启动后端
echo "5. 启动后端..."
cd /workspace/backend
nohup mvn spring-boot:run > /workspace/backend.log 2>&1 &
BACKEND_PID=$!

# 6. 等待后端启动
echo "6. 等待后端启动..."
sleep 45

# 7. 启动前端
echo "7. 启动前端..."
cd /workspace/frontend
nohup npm run dev -- --host 0.0.0.0 --port 3000 > /workspace/frontend.log 2>&1 &
FRONTEND_PID=$!

# 8. 等待前端启动
echo "8. 等待前端启动..."
sleep 25

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
echo "  H2控制台: http://localhost:18080/h2-console"
echo ""
echo "注意: 使用H2内存数据库，重启容器后数据会丢失"