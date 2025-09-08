# 企业文件管理系统 - 完整单容器镜像
# 包含：MySQL、Redis、Spring Boot后端、Node.js前端、Nginx和所有项目文件

# 基础镜像
FROM ubuntu:22.04

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Shanghai
ENV HTTP_PROXY=http://192.168.10.4:10809
ENV HTTPS_PROXY=http://192.168.10.4:10809

# 安装基础工具和运行时
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    vim \
    git \
    unzip \
    zip \
    tar \
    gzip \
    supervisor \
    cron \
    logrotate \
    nginx \
    openjdk-17-jre \
    mysql-server-8.0 \
    redis-server \
    procps \
    net-tools \
    && rm -rf /var/lib/apt/lists/*

# 安装Node.js和Maven
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs && \
    apt-get update && apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# 设置时区
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建应用用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 创建必要目录
RUN mkdir -p /app /cunchu /app/logs /app/temp /app/ssl /app/backups /var/run/nginx /var/lib/mysql /var/lib/redis /var/log/supervisor && \
    chown -R appuser:appuser /app /cunchu /var/lib/mysql /var/lib/redis

# 设置工作目录
WORKDIR /app

# 复制完整的项目源代码
COPY . .

# 构建Spring Boot后端
RUN cd /app/backend && \
    chmod +x mvnw && \
    ./mvnw clean package -DskipTests -B

# 构建Node.js前端
RUN cd /app/frontend && \
    npm config set registry https://registry.npmmirror.com && \
    npm ci --only=production && \
    npm run build

# 复制构建结果到运行目录
RUN cp /app/backend/target/*.jar /app/app.jar && \
    cp -r /app/frontend/dist /app/frontend/dist

# 配置MySQL
COPY <<EOF /etc/mysql/mysql.conf.d/mysqld.cnf
[mysqld]
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
max_connections=1000
innodb_buffer_pool_size=1G
innodb_log_file_size=256M
max_allowed_packet=1024M
default-time-zone='+08:00'
bind-address = 127.0.0.1
socket = /var/run/mysqld/mysqld.sock
pid-file = /var/run/mysqld/mysqld.pid
datadir = /var/lib/mysql
log-error = /var/log/mysql/error.log

[mysql]
default-character-set=utf8mb4

[client]
default-character-set=utf8mb4
socket = /var/run/mysqld/mysqld.sock
EOF

# 配置Redis
COPY <<EOF /etc/redis/redis.conf
bind 127.0.0.1
port 6379
timeout 0
tcp-keepalive 300
databases 16
save 900 1
save 300 10
save 60 10000
stop-writes-on-bgsave-error yes
rdbcompression yes
rdbchecksum yes
dbfilename dump.rdb
dir /var/lib/redis
maxmemory 512mb
maxmemory-policy allkeys-lru
appendonly yes
appendfilename "appendonly.aof"
appendfsync everysec
no-appendfsync-on-rewrite no
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb
aof-load-truncated yes
aof-use-rdb-preamble yes
requirepass redispassword
EOF

# 配置Nginx
COPY <<EOF /etc/nginx/nginx.conf
user appuser;
worker_processes auto;
pid /run/nginx.pid;
include /etc/nginx/modules-enabled/*.conf;

events {
    worker_connections 768;
}

http {
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    access_log /var/log/nginx/access.log;
    error_log /var/log/nginx/error.log;
    gzip on;
    include /etc/nginx/conf.d/*.conf;
}
EOF

# 配置Nginx站点
COPY <<EOF /etc/nginx/conf.d/default.conf
server {
    listen 80;
    server_name localhost;
    root /app/frontend/dist;
    index index.html;

    # 静态文件缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # 前端路由
    location / {
        try_files \$uri \$uri/ /index.html;
    }

    # API代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # WebSocket支持
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

# 配置Supervisor
COPY <<EOF /etc/supervisor/conf.d/supervisord.conf
[supervisord]
nodaemon=true
user=root
logfile=/var/log/supervisor/supervisord.log
pidfile=/var/run/supervisord.pid

[program:mysql]
command=/usr/sbin/mysqld --user=mysql
autostart=true
autorestart=true
stdout_logfile=/var/log/mysql/mysql.log
stderr_logfile=/var/log/mysql/error.log
priority=100

[program:redis]
command=/usr/bin/redis-server /etc/redis/redis.conf
autostart=true
autorestart=true
stdout_logfile=/var/log/redis/redis.log
stderr_logfile=/var/log/redis/redis.log
priority=200

[program:backend]
command=/usr/bin/java -Xmx1024m -Xms512m -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar
directory=/app
user=appuser
autostart=true
autorestart=true
stdout_logfile=/app/logs/backend.log
stderr_logfile=/app/logs/backend-error.log
priority=300
startsecs=30
startretries=3

[program:nginx]
command=/usr/sbin/nginx -g "daemon off;"
autostart=true
autorestart=true
stdout_logfile=/var/log/nginx/nginx.log
stderr_logfile=/var/log/nginx/error.log
priority=400
EOF

# 创建数据库初始化脚本
COPY <<EOF /app/init-database.sh
#!/bin/bash
set -e

# 等待MySQL启动
echo "Waiting for MySQL to start..."
while ! mysqladmin ping -h 127.0.0.1 --silent; do
    sleep 1
done

# 创建数据库和用户
echo "Initializing database..."
mysql -u root -e "CREATE DATABASE IF NOT EXISTS enterprise_file_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -e "CREATE USER IF NOT EXISTS 'filemanager'@'localhost' IDENTIFIED BY 'filemanager_password';"
mysql -u root -e "GRANT ALL PRIVILEGES ON enterprise_file_manager.* TO 'filemanager'@'localhost';"
mysql -u root -e "FLUSH PRIVILEGES;"

# 运行数据库迁移脚本
if [ -f "/app/docker/mysql/init.sql" ]; then
    echo "Running database initialization script..."
    mysql -u root enterprise_file_manager < /app/docker/mysql/init.sql
fi

echo "Database initialization completed."
EOF

# 创建健康检查脚本
COPY <<EOF /app/health-check.sh
#!/bin/bash
set -e

# 检查MySQL
mysqladmin ping -h 127.0.0.1 --silent

# 检查Redis
redis-cli ping

# 检查后端应用
curl -f http://localhost:8080/api/actuator/health

# 检查Nginx
curl -f http://localhost:80

echo "All services are healthy"
EOF

# 创建启动脚本
COPY <<EOF /app/start.sh
#!/bin/bash
set -e

# 设置权限
chmod +x /app/init-database.sh /app/health-check.sh

# 创建必要的目录
mkdir -p /app/storage /app/logs /app/temp /cunchu
chown -R appuser:appuser /app/storage /app/logs /app/temp /cunchu

# 初始化数据库
echo "Initializing database..."
/app/init-database.sh

# 启动所有服务
echo "Starting all services..."
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf
EOF

# 设置权限
RUN chmod +x /app/init-database.sh /app/health-check.sh /app/start.sh && \
    chown -R appuser:appuser /app

# 创建日志目录
RUN mkdir -p /var/log/mysql /var/log/redis && \
    chown -R appuser:appuser /var/log/mysql /var/log/redis

# 设置环境变量
ENV JAVA_OPTS="-Xmx1024m -Xms512m"
ENV SPRING_PROFILES_ACTIVE=docker
ENV SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/enterprise_file_manager?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true
ENV SPRING_DATASOURCE_USERNAME=filemanager
ENV SPRING_DATASOURCE_PASSWORD=filemanager_password
ENV SPRING_REDIS_HOST=localhost
ENV SPRING_REDIS_PORT=6379
ENV SPRING_REDIS_PASSWORD=redispassword
ENV FILE_STORAGE_PATH=/app/storage
ENV NODE_ENV=production

# 暴露端口
EXPOSE 80 3306 6379 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=30s --start-period=60s --retries=3 \
    CMD /app/health-check.sh

# 设置工作目录
WORKDIR /app

# 切换到非root用户
USER appuser

# 启动应用
CMD ["/app/start.sh"]