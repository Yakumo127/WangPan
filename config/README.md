# 企业文件管理系统 - 完整单容器部署

## 概述

这是一个完整的单容器解决方案，将整个企业文件管理系统（包括所有项目文件、MySQL、Redis、Spring Boot后端、Node.js前端、Nginx）打包在一个Docker镜像中。只需要一个Dockerfile和一个命令即可构建和运行整个系统。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Single Docker Container                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │   Nginx     │  │ Spring Boot │  │   Node.js   │        │
│  │  (Proxy)    │  │  (Backend)  │  │  (Frontend) │        │
│  │     :80     │  │    :8080    │  │             │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐                          │
│  │    MySQL    │  │    Redis    │                          │
│  │ (Database)  │  │   (Cache)   │                          │
│  │    :3306    │  │    :6379    │                          │
│  └─────────────┘  └─────────────┘                          │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              Supervisor (Process Manager)               │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 文件结构

```
test03/
├── backend/                 # Spring Boot 后端项目
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                # Node.js 前端项目
│   ├── src/
│   ├── package.json
│   └── Dockerfile
├── docker/                  # Docker 相关文件
│   ├── Dockerfile          # 单容器镜像构建文件
│   ├── .dockerignore       # Docker 构建忽略文件
│   └── README.md           # 本文档
├── docker-compose.yml      # 原始多容器配置（可选）
├── .env                    # 环境变量（可选）
└── README.md               # 项目说明
```

## 快速开始

### 1. 构建镜像

```bash
# 进入项目根目录
cd /work/test03

# 构建完整的单容器镜像
docker build -f docker/Dockerfile -t enterprise-file-manager:latest .
```

### 2. 运行容器

```bash
# 运行容器
docker run -d \
  --name enterprise-file-manager \
  -p 80:80 \
  -p 3306:3306 \
  -p 6379:6379 \
  -p 8080:8080 \
  -v $(pwd)/data/mysql:/var/lib/mysql \
  -v $(pwd)/data/redis:/var/lib/redis \
  -v $(pwd)/storage:/app/storage \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/cunchu:/cunchu \
  --restart unless-stopped \
  enterprise-file-manager:latest
```

### 3. 访问应用

启动成功后，可以通过以下地址访问：

- **前端应用**: http://localhost
- **后端API**: http://localhost:8080/api
- **MySQL数据库**: localhost:3306
- **Redis缓存**: localhost:6379

## 镜像构建过程

### 第一阶段：环境准备
1. **基础镜像**: Ubuntu 22.04
2. **运行时安装**: 
   - OpenJDK 17 (Java运行时)
   - Node.js 18 (前端构建)
   - MySQL 8.0 (数据库)
   - Redis 7 (缓存)
   - Nginx (反向代理)
   - Supervisor (进程管理)

### 第二阶段：项目构建
1. **复制项目文件**: 复制整个项目源代码
2. **构建后端**: 使用Maven构建Spring Boot应用
3. **构建前端**: 使用npm构建Node.js前端

### 第三阶段：服务配置
1. **MySQL配置**: 设置数据库配置文件
2. **Redis配置**: 设置缓存配置文件
3. **Nginx配置**: 设置反向代理和静态文件服务
4. **Supervisor配置**: 设置进程管理

### 第四阶段：初始化
1. **数据库初始化**: 创建数据库和用户
2. **健康检查**: 设置服务健康检查
3. **启动脚本**: 创建容器启动脚本

## 系统配置

### 数据库配置
- **数据库类型**: MySQL 8.0
- **数据库名**: `enterprise_file_manager`
- **用户名**: `filemanager`
- **密码**: `filemanager_password`
- **Root密码**: `rootpassword`
- **字符集**: utf8mb4
- **时区**: Asia/Shanghai

### Redis配置
- **版本**: Redis 7
- **端口**: 6379
- **密码**: `redispassword`
- **最大内存**: 512MB
- **持久化**: AOF + RDB

### 应用配置
- **Java版本**: OpenJDK 17
- **Spring Boot**: 3.2.0
- **Node.js**: 18
- **Java内存**: 1024MB最大，512MB最小
- **文件存储**: /app/storage

### Nginx配置
- **端口**: 80
- **静态文件**: /app/frontend/dist
- **API代理**: /api -> http://localhost:8080
- **WebSocket支持**: /ws -> http://localhost:8080

## 数据持久化

### 推荐的卷挂载
```bash
-v $(pwd)/data/mysql:/var/lib/mysql    # MySQL数据
-v $(pwd)/data/redis:/var/lib/redis    # Redis数据
-v $(pwd)/storage:/app/storage          # 文件存储
-v $(pwd)/logs:/app/logs                # 应用日志
-v $(pwd)/cunchu:/cunchu               # 外部存储目录
```

### 数据目录结构
```
data/
├── mysql/          # MySQL数据库文件
└── redis/          # Redis数据文件

storage/            # 应用文件存储
logs/               # 应用日志
cunchu/             # 外部存储目录
```

## 服务管理

### Supervisor进程管理
容器使用Supervisor管理所有服务进程：

```bash
# 查看服务状态
docker exec enterprise-file-manager supervisorctl status

# 重启特定服务
docker exec enterprise-file-manager supervisorctl restart mysql
docker exec enterprise-file-manager supervisorctl restart redis
docker exec enterprise-file-manager supervisorctl restart backend
docker exec enterprise-file-manager supervisorctl restart nginx
```

### 服务启动顺序
1. **MySQL** (优先级 100)
2. **Redis** (优先级 200)
3. **Backend** (优先级 300)
4. **Nginx** (优先级 400)

## 健康检查

容器内置健康检查，定期检查：
- MySQL服务状态 (`mysqladmin ping`)
- Redis服务状态 (`redis-cli ping`)
- 后端API健康状态 (`curl /api/actuator/health`)
- Nginx服务状态 (`curl /`)

```bash
# 查看容器健康状态
docker ps

# 查看健康检查日志
docker logs enterprise-file-manager
```

## 日志管理

### 日志位置
- **MySQL**: `/var/log/mysql/`
- **Redis**: `/var/log/redis/`
- **后端应用**: `/app/logs/`
- **Nginx**: `/var/log/nginx/`
- **Supervisor**: `/var/log/supervisor/`

### 查看日志
```bash
# 查看所有日志
docker logs enterprise-file-manager

# 查看特定服务日志
docker exec enterprise-file-manager tail -f /app/logs/backend.log
docker exec enterprise-file-manager tail -f /var/log/mysql/error.log
```

## 开发和调试

### 进入容器调试
```bash
# 进入容器
docker exec -it enterprise-file-manager bash

# 切换到应用用户
sudo su - appuser

# 查看应用状态
supervisorctl status
```

### 调试模式
构建时添加调试参数：
```bash
docker build \
  --build-arg JAVA_OPTS="-Xmx1024m -Xms512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" \
  -f docker/Dockerfile \
  -t enterprise-file-manager:debug \
  .
```

### 开发环境挂载
```bash
docker run -d \
  --name enterprise-file-manager-dev \
  -p 80:80 \
  -p 3306:3306 \
  -p 6379:6379 \
  -p 8080:8080 \
  -p 5005:5005 \
  -v $(pwd)/backend:/app/backend \
  -v $(pwd)/frontend:/app/frontend \
  -v $(pwd)/data/mysql:/var/lib/mysql \
  -v $(pwd)/data/redis:/var/lib/redis \
  -v $(pwd)/storage:/app/storage \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/cunchu:/cunchu \
  --restart unless-stopped \
  enterprise-file-manager:latest
```

## 性能优化

### 资源限制
```bash
# 限制内存使用
docker run -m 4g --memory-swap 4g ...

# 限制CPU使用
docker run --cpus 2.0 ...
```

### 数据库优化
可以根据需要调整MySQL配置：
```dockerfile
# 在Dockerfile中修改MySQL配置
innodb_buffer_pool_size=2G
max_connections=2000
```

### 缓存优化
调整Redis配置：
```dockerfile
# 在Dockerfile中修改Redis配置
maxmemory 1g
maxmemory-policy allkeys-lru
```

## 备份和恢复

### 备份数据库
```bash
# 备份数据库
docker exec enterprise-file-manager mysqldump -u root -p enterprise_file_manager > backup.sql

# 备份Redis
docker exec enterprise-file-manager redis-cli --rdb backup.rdb
```

### 恢复数据库
```bash
# 恢复数据库
docker exec -i enterprise-file-manager mysql -u root -p enterprise_file_manager < backup.sql

# 恢复Redis
docker cp backup.rdb enterprise-file-manager:/var/lib/redis/dump.rdb
docker restart enterprise-file-manager
```

## 故障排除

### 常见问题

**问题**: 容器启动失败
```bash
# 查看容器日志
docker logs enterprise-file-manager

# 检查Dockerfile语法
docker build --no-cache -f docker/Dockerfile -t test .
```

**问题**: MySQL启动失败
```bash
# 检查MySQL日志
docker exec enterprise-file-manager tail -f /var/log/mysql/error.log

# 检查数据目录权限
docker exec enterprise-file-manager ls -la /var/lib/mysql
```

**问题**: Redis连接失败
```bash
# 检查Redis状态
docker exec enterprise-file-manager redis-cli ping

# 检查Redis配置
docker exec enterprise-file-manager redis-cli CONFIG GET requirepass
```

**问题**: 前端无法访问
```bash
# 检查Nginx状态
docker exec enterprise-file-manager nginx -t

# 检查前端文件
docker exec enterprise-file-manager ls -la /app/frontend/dist
```

### 重置环境
```bash
# 停止并删除容器
docker stop enterprise-file-manager
docker rm enterprise-file-manager

# 清理数据（谨慎操作）
rm -rf data/mysql/* data/redis/* storage/* logs/*

# 重新构建和运行
docker build -f docker/Dockerfile -t enterprise-file-manager:latest .
docker run -d --name enterprise-file-manager ...
```

## 安全建议

### 生产环境安全
1. **修改默认密码**: 修改所有默认密码
2. **使用HTTPS**: 配置SSL证书
3. **防火墙设置**: 只开放必要的端口
4. **定期更新**: 定期更新基础镜像和依赖
5. **监控告警**: 设置监控和告警机制

### 环境变量安全
```bash
# 使用环境变量设置密码
docker run -d \
  -e MYSQL_ROOT_PASSWORD=your_secure_password \
  -e MYSQL_PASSWORD=your_app_password \
  -e REDIS_PASSWORD=your_redis_password \
  ...
```

## 更新和维护

### 更新应用代码
```bash
# 重新构建镜像
docker build -f docker/Dockerfile -t enterprise-file-manager:latest .

# 停止旧容器
docker stop enterprise-file-manager
docker rm enterprise-file-manager

# 启动新容器
docker run -d --name enterprise-file-manager ...
```

### 清理资源
```bash
# 清理未使用的镜像
docker image prune

# 清理未使用的卷
docker volume prune

# 清理所有未使用的资源
docker system prune -a
```

## 扩展功能

### 添加SSL支持
```bash
# 准备SSL证书
mkdir -p ssl
cp your-cert.pem ssl/cert.pem
cp your-key.pem ssl/key.pem

# 运行容器时挂载证书
docker run -d \
  -v $(pwd)/ssl:/etc/nginx/ssl \
  ...
```

### 添加监控
```bash
# 安装监控工具
docker exec enterprise-file-manager apt-get update && apt-get install -y htop

# 查看资源使用
docker exec enterprise-file-manager htop
docker stats enterprise-file-manager
```

## 许可证

本项目采用MIT许可证，详见LICENSE文件。

## 支持

如有问题或建议，请：
1. 查看本文档的故障排除部分
2. 检查项目日志文件
3. 提交Issue或联系开发团队

---

**注意**: 这是一个完整的单容器解决方案，适用于开发、测试和生产环境。在生产环境中使用前，请确保进行充分的安全配置和性能测试。