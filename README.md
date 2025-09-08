# 企业级文件管理系统

## 项目概述

这是一个基于 Spring Boot + Vue.js 开发的企业级文件管理系统，提供完整的文件存储、管理和分享功能。系统采用前后端分离架构，支持 Docker 容器化部署，具备企业级的安全性和可扩展性。

## 🚀 核心特性

- **用户管理**: 完整的用户注册、登录、权限管理
- **文件管理**: 文件上传、下载、删除、重命名、移动、复制
- **文件夹管理**: 创建、删除、重命名、移动文件夹
- **回收站**: 软删除机制，支持文件恢复和永久删除
- **管理员后台**: 用户管理、系统配置、回收站管理
- **文件分享**: 生成分享链接，支持密码保护
- **存储配额**: 用户存储空间管理
- **操作日志**: 完整的用户操作记录

## 📋 目录结构

```
test11/
├── backend/                 # 后端 Spring Boot 项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/filemanager/
│   │   │   │   ├── entity/      # 实体类
│   │   │   │   ├── dto/         # 数据传输对象
│   │   │   │   ├── controller/  # 控制器
│   │   │   │   ├── service/     # 服务层
│   │   │   │   ├── repository/  # 数据访问层
│   │   │   │   ├── security/    # 安全配置
│   │   │   │   └── config/      # 配置类
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── application-docker.yml
│   └── pom.xml
├── frontend/                # 前端 Vue.js 项目
│   ├── src/
│   │   ├── components/      # 通用组件
│   │   ├── views/          # 页面组件
│   │   ├── layout/         # 布局组件
│   │   ├── api/           # API 接口
│   │   ├── store/         # 状态管理
│   │   ├── router/        # 路由配置
│   │   └── utils/         # 工具函数
│   ├── dist/             # 构建产物
│   └── package.json
├── config/                # 配置文件
│   ├── backend/          # 后端配置
│   ├── frontend/         # 前端配置
│   ├── nginx/            # Nginx 配置
│   ├── mysql.conf        # MySQL 配置
│   ├── redis.conf        # Redis 配置
│   └── init.sql          # 数据库初始化脚本
├── database/             # 数据库相关
│   ├── init.sql          # 数据库初始化
│   └── backup.sql        # 数据库备份
├── docs/                 # 文档
├── scripts/              # 脚本文件
├── docker-compose.yml    # Docker Compose 配置
├── .env                  # 环境变量
└── README.md            # 项目说明
```

## 🏗️ 项目架构

### 技术栈

#### 后端技术
- **框架**: Spring Boot 3.2.0
- **数据库**: MySQL 8.0.33
- **缓存**: Redis 7
- **安全**: Spring Security + JWT
- **持久层**: Spring Data JPA + Hibernate
- **工具库**: Lombok, Hutool, Apache POI, jjwt

#### 前端技术
- **框架**: Vue 3.3.0
- **UI组件**: Element Plus 2.4.0
- **状态管理**: Pinia 2.1.0
- **路由**: Vue Router 4.2.0
- **HTTP客户端**: Axios 1.6.0
- **构建工具**: Vue CLI 5.0.8

#### 基础设施
- **容器化**: Docker + Docker Compose
- **Web服务器**: Nginx
- **Java版本**: Java 17

### 架构设计

采用经典的三层架构模式：

```
前端 (Vue.js) → 后端 (Spring Boot) → 数据库 (MySQL) → 缓存 (Redis)
```

- **表现层**: Vue.js 前端应用，负责用户界面交互
- **控制层**: Spring Boot REST API，处理 HTTP 请求
- **业务层**: Service 层，实现业务逻辑
- **数据层**: JPA Repository，负责数据持久化
- **缓存层**: Redis，提供缓存支持

## 🗄️ 数据库架构

### 核心数据表

#### 1. users (用户表)
| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(50) | 用户名（唯一） |
| password | VARCHAR(255) | 密码（加密存储） |
| email | VARCHAR(100) | 邮箱（唯一） |
| display_name | VARCHAR(100) | 显示名称 |
| avatar_url | VARCHAR(255) | 头像URL |
| phone_number | VARCHAR(20) | 电话号码 |
| role | VARCHAR(20) | 角色（USER/ADMIN） |
| quota_limit | BIGINT | 存储配额限制 |
| quota_used | BIGINT | 已用存储空间 |
| enabled | BOOLEAN | 是否启用 |
| locked | BOOLEAN | 是否锁定 |
| login_attempts | INT | 登录尝试次数 |
| last_login_time | DATETIME | 最后登录时间 |
| last_login_ip | VARCHAR(45) | 最后登录IP |
| create_time | DATETIME | 创建时间 |

#### 2. files (文件表)
| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键 |
| filename | VARCHAR(255) | 文件名 |
| original_filename | VARCHAR(255) | 原始文件名 |
| content_type | VARCHAR(100) | 文件类型 |
| size | BIGINT | 文件大小 |
| file_path | VARCHAR(500) | 文件存储路径 |
| thumbnail_path | VARCHAR(500) | 缩略图路径 |
| file_hash | VARCHAR(64) | 文件哈希值 |
| user_id | BIGINT | 所属用户ID |
| folder_id | BIGINT | 所属文件夹ID |
| download_count | INT | 下载次数 |
| deleted | BOOLEAN | 是否删除 |
| delete_time | DATETIME | 删除时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### 3. folders (文件夹表)
| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(255) | 文件夹名称 |
| parent_id | BIGINT | 父文件夹ID |
| user_id | BIGINT | 所属用户ID |
| is_root | BOOLEAN | 是否根目录 |
| deleted | BOOLEAN | 是否删除 |
| delete_time | DATETIME | 删除时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### 4. user_logs (用户日志表)
| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户ID |
| action_type | VARCHAR(50) | 操作类型 |
| action_description | VARCHAR(500) | 操作描述 |
| resource_type | VARCHAR(50) | 资源类型 |
| resource_id | BIGINT | 资源ID |
| ip_address | VARCHAR(45) | IP地址 |
| status | VARCHAR(20) | 操作状态 |
| create_time | DATETIME | 操作时间 |

#### 5. system_config (系统配置表)
| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键 |
| config_key | VARCHAR(100) | 配置键 |
| config_value | TEXT | 配置值 |
| config_type | VARCHAR(20) | 配置类型 |
| description | VARCHAR(255) | 描述 |
| is_system | BOOLEAN | 是否系统配置 |

#### 6. file_chunks (文件分块表)
| 字段名 | 类型 | 描述 |
|--------|------|------|
| id | BIGINT | 主键 |
| file_id | BIGINT | 文件ID |
| user_id | BIGINT | 用户ID |
| chunk_number | INT | 分块编号 |
| chunk_path | VARCHAR(500) | 分块路径 |
| chunk_size | BIGINT | 分块大小 |
| upload_status | VARCHAR(20) | 上传状态 |
| create_time | DATETIME | 创建时间 |

### 数据库关系图

```
users (1) → (N) files
users (1) → (N) folders
folders (1) → (N) files
users (1) → (N) user_logs
files (1) → (N) file_chunks
```

## 🔌 API 接口文档

### 认证相关接口

#### 1. 用户注册
- **URL**: `POST /api/auth/register`
- **功能**: 用户注册
- **请求参数**:
  ```json
  {
    "username": "string (3-20字符)",
    "password": "string (6-20字符)",
    "confirmPassword": "string",
    "email": "string (邮箱格式)",
    "phone": "string (可选)"
  }
  ```
- **返回数据**:
  ```json
  {
    "message": "注册成功"
  }
  ```

#### 2. 用户登录
- **URL**: `POST /api/auth/login`
- **功能**: 用户登录
- **请求参数**:
  ```json
  {
    "username": "string",
    "password": "string",
    "captcha": "string (可选)"
  }
  ```
- **返回数据**:
  ```json
  {
    "token": "JWT token",
    "message": "登录成功"
  }
  ```

#### 3. 获取用户信息
- **URL**: `GET /api/auth/userinfo`
- **功能**: 获取当前登录用户信息
- **请求头**: `Authorization: Bearer {token}`
- **返回数据**:
  ```json
  {
    "id": 1,
    "username": "string",
    "email": "string",
    "displayName": "string",
    "role": "ROLE_ADMIN",
    "quotaLimit": 1073741824,
    "quotaUsed": 0,
    "createTime": "2024-01-01T00:00:00",
    "lastLoginTime": "2024-01-01T00:00:00"
  }
  ```

### 文件管理接口

#### 1. 文件上传
- **URL**: `POST /api/files/upload`
- **功能**: 上传文件
- **请求参数**: 
  - `file`: MultipartFile (必须)
  - `folderId`: Long (可选，指定文件夹)
- **返回数据**:
  ```json
  {
    "message": "文件上传成功",
    "fileId": 1,
    "filename": "example.txt",
    "size": 1024,
    "uploadTime": "2024-01-01T00:00:00"
  }
  ```

#### 2. 获取文件列表
- **URL**: `GET /api/files/list`
- **功能**: 获取用户的文件列表
- **请求参数**:
  - `folderId`: Long (可选，指定文件夹)
- **返回数据**: File对象数组

#### 3. 下载文件
- **URL**: `GET /api/files/download/{fileId}`
- **功能**: 下载文件
- **返回数据**: 文件二进制流

#### 4. 删除文件
- **URL**: `DELETE /api/files/{fileId}`
- **功能**: 删除文件（移至回收站）
- **返回数据**:
  ```json
  {
    "message": "文件删除成功"
  }
  ```

#### 5. 重命名文件
- **URL**: `PUT /api/files/{fileId}/rename`
- **功能**: 重命名文件
- **请求参数**:
  ```json
  {
    "name": "新文件名"
  }
  ```
- **返回数据**:
  ```json
  {
    "message": "文件重命名成功",
    "fileId": 1,
    "filename": "新文件名"
  }
  ```

### 文件夹管理接口

#### 1. 创建文件夹
- **URL**: `POST /api/folders/create`
- **功能**: 创建文件夹
- **请求参数**:
  ```json
  {
    "name": "文件夹名称",
    "parentId": 1
  }
  ```
- **返回数据**:
  ```json
  {
    "message": "文件夹创建成功",
    "folderId": 1,
    "name": "文件夹名称",
    "parentId": null,
    "createTime": "2024-01-01T00:00:00"
  }
  ```

#### 2. 获取文件夹列表
- **URL**: `GET /api/folders/list`
- **功能**: 获取用户的文件夹列表
- **请求参数**:
  - `parentId`: Long (可选，指定父文件夹)
- **返回数据**: Folder对象数组

### 管理员接口

#### 1. 获取所有用户
- **URL**: `GET /api/admin/users`
- **功能**: 获取系统所有用户列表
- **权限**: 需要管理员权限
- **返回数据**: User对象数组

#### 2. 管理员恢复文件
- **URL**: `PUT /api/files/admin/{fileId}/restore`
- **功能**: 管理员恢复文件
- **权限**: 需要管理员权限
- **返回数据**:
  ```json
  {
    "message": "文件恢复成功"
  }
  ```

### 回收站接口

#### 1. 获取回收站文件
- **URL**: `GET /api/files/recycle/bin`
- **功能**: 获取用户的回收站文件
- **返回数据**: 回收站文件列表

#### 2. 管理员清空所有回收站
- **URL**: `DELETE /api/files/admin/recycle/bin/empty`
- **功能**: 清空所有回收站
- **权限**: 需要管理员权限
- **返回数据**:
  ```json
  {
    "message": "所有回收站清空成功"
  }
  ```

## 🚀 部署指南

### 环境要求

- **操作系统**: Linux/Windows/MacOS
- **Java**: OpenJDK 17+
- **Node.js**: 16+
- **Docker**: 20.10+
- **Docker Compose**: 2.0+

### 物理机部署

#### 1. 环境准备
```bash
# 安装 Java 17
sudo apt update
sudo apt install openjdk-17-jdk

# 安装 Node.js 16+
curl -fsSL https://deb.nodesource.com/setup_16.x | sudo -E bash -
sudo apt-get install -y nodejs

# 安装 MySQL 8.0
sudo apt install mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql

# 安装 Redis
sudo apt install redis-server
sudo systemctl start redis
sudo systemctl enable redis
```

#### 2. 数据库初始化
```bash
# 创建数据库和用户
mysql -u root -p
CREATE DATABASE enterprise_file_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'filemanager'@'localhost' IDENTIFIED BY 'filemanager_password';
GRANT ALL PRIVILEGES ON enterprise_file_manager.* TO 'filemanager'@'localhost';
FLUSH PRIVILEGES;
```

#### 3. 后端部署
```bash
# 进入后端目录
cd backend

# 编译项目
mvn clean package -DskipTests

# 运行应用
java -jar target/filemanager-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

#### 4. 前端部署
```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 构建生产版本
npm run build

# 使用 Nginx 部署
sudo cp -r dist/* /var/www/html/
```

#### 5. 使用启动脚本
```bash
# 使用快速启动脚本
chmod +x start-project.sh
./start-project.sh

# 或使用简单启动脚本
chmod +x simple-start.sh
./simple-start.sh
```

### Docker 部署

#### 1. 使用 Docker Compose
```bash
# 克隆项目
git clone <repository-url>
cd test11

# 设置环境变量
cp .env.example .env
# 编辑 .env 文件，配置数据库密码等信息

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

#### 2. 单独构建镜像
```bash
# 构建后端镜像
docker build -f config/backend/Dockerfile -t filemanager-backend:latest .

# 构建前端镜像
docker build -f config/frontend/Dockerfile -t filemanager-frontend:latest .

# 运行容器
docker run -d --name filemanager-backend -p 8080:8080 filemanager-backend:latest
docker run -d --name filemanager-frontend -p 8081:80 filemanager-frontend:latest
```

### 环境配置

#### 1. 后端配置 (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/enterprise_file_manager
    username: filemanager
    password: filemanager_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  redis:
    host: localhost
    port: 6379
    database: 0

  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB

file:
  upload:
    path: /opt/filemanager/uploads
    max-size: 104857600  # 100MB

jwt:
  secret: your-secret-key
  expiration: 86400000  # 24小时
```

#### 2. 前端配置
```javascript
// src/config/index.js
export const API_BASE_URL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080'
export const UPLOAD_MAX_SIZE = 100 * 1024 * 1024 // 100MB
```

#### 3. Nginx 配置
```nginx
server {
    listen 80;
    server_name localhost;

    # 前端静态资源
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 文件下载代理
    location /files/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## ✨ 已实现功能

### 用户功能

#### 1. 用户认证
- ✅ 用户注册
- ✅ 用户登录
- ✅ 管理员登录
- ✅ JWT 令牌认证
- ✅ 密码修改
- ✅ 个人资料管理
- ✅ 验证码功能
- ✅ 登录失败锁定机制

#### 2. 文件管理
- ✅ 文件上传（支持大文件）
- ✅ 文件下载
- ✅ 文件删除（软删除）
- ✅ 文件重命名
- ✅ 文件移动
- ✅ 文件复制
- ✅ 文件搜索
- ✅ 文件预览
- ✅ 文件分享
- ✅ 批量操作

#### 3. 文件夹管理
- ✅ 创建文件夹
- ✅ 删除文件夹
- ✅ 重命名文件夹
- ✅ 移动文件夹
- ✅ 文件夹层级管理
- ✅ 文件夹路径导航

#### 4. 回收站功能
- ✅ 个人回收站
- ✅ 文件恢复
- ✅ 永久删除
- ✅ 管理员回收站管理
- ✅ 批量恢复
- ✅ 批量删除

### 管理员功能

#### 1. 用户管理
- ✅ 用户列表查看
- ✅ 用户搜索
- ✅ 用户创建
- ✅ 用户编辑
- ✅ 用户删除
- ✅ 用户状态管理
- ✅ 用户权限管理

#### 2. 系统管理
- ✅ 系统配置管理
- ✅ 存储配额管理
- ✅ 用户日志查看
- ✅ 系统监控
- ✅ 数据备份

#### 3. 回收站管理
- ✅ 查看所有回收站
- ✅ 恢复任意文件
- ✅ 永久删除文件
- ✅ 清空回收站

### 系统特性

#### 1. 安全特性
- ✅ JWT 令牌认证
- ✅ 密码加密存储
- ✅ 权限分级管理
- ✅ 防止 SQL 注入
- ✅ 文件访问控制
- ✅ 操作日志记录

#### 2. 性能优化
- ✅ Redis 缓存
- ✅ 文件分块上传
- ✅ 图片压缩
- ✅ 数据库索引优化
- ✅ 前端性能优化

#### 3. 用户体验
- ✅ 响应式设计
- ✅ 拖拽上传
- ✅ 进度显示
- ✅ 实时搜索
- ✅ 多语言支持
- ✅ 深色模式

### 前端界面

#### 1. 用户界面
- ✅ 登录页面
- ✅ 注册页面
- ✅ 个人中心
- ✅ 文件管理界面
- ✅ 文件夹管理界面
- ✅ 回收站界面
- ✅ 分享管理界面

#### 2. 管理员界面
- ✅ 管理员登录
- ✅ 用户管理界面
- ✅ 系统设置界面
- ✅ 日志查看界面
- ✅ 全局回收站界面

#### 3. 通用组件
- ✅ 文件列表组件
- ✅ 文件上传组件
- ✅ 文件夹树组件
- ✅ 搜索组件
- ✅ 分页组件
- ✅ 对话框组件

## 📊 系统监控

### 健康检查
- 后端健康检查: `GET /actuator/health`
- 数据库健康检查: `mysqladmin ping`
- Redis 健康检查: `redis-cli ping`

### 日志管理
- 应用日志: `logs/` 目录
- 访问日志: Nginx 访问日志
- 错误日志: Nginx 错误日志

### 性能监控
- 文件上传进度
- 系统资源使用情况
- 数据库连接池状态
- Redis 缓存命中率

## 🔧 开发指南

### 本地开发环境

#### 1. 后端开发
```bash
cd backend
mvn spring-boot:run
```

#### 2. 前端开发
```bash
cd frontend
npm install
npm run serve
```

#### 3. 数据库开发
```bash
# 使用 H2 内存数据库（开发环境）
./h2-start.sh
```

### 代码规范

#### 后端代码规范
- 使用 Lombok 减少样板代码
- 遵循 Spring Boot 最佳实践
- 使用 JPA 进行数据访问
- 统一异常处理

#### 前端代码规范
- 使用 Vue 3 Composition API
- 遵循 ESLint 规范
- 组件化开发
- 响应式设计

### 测试

#### 单元测试
```bash
cd backend
mvn test
```

#### 集成测试
```bash
cd frontend
npm run test:unit
```

## 📈 版本历史

### v1.0.0 (当前版本)
- ✅ 完整的文件管理功能
- ✅ 用户认证和权限管理
- ✅ 管理员后台
- ✅ Docker 容器化部署
- ✅ 完整的 API 文档



## 📄 许可证

本项目采用 Apache2.0 许可证。

## 🆘 技术支持

如有问题，请自行解决或提交 Issue 或 问大模型。

---

**企业级文件管理系统** - 为企业用户提供安全、高效的文件管理解决方案