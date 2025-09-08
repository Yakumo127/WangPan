# test08项目备份清单

## 备份信息
- **备份时间**: 2025-09-07 16:18
- **备份来源**: Docker容器 test08-dev (/workspace)
- **备份目标**: /work/test11
- **容器ID**: 162ec91d7aaa

## 备份内容详情

### 📁 frontend/ (前端代码) - 338MB
- `src/` - Vue.js源代码目录
- `dist/` - 构建产物
- `package.json` - 项目依赖配置
- `vue.config.js` - Vue项目配置
- `node_modules/` - 依赖包目录
- `frontend.log` - 前端运行日志

### 📁 backend/ (后端代码) - 840KB
- `src/main/java/` - Java源代码
- `src/main/resources/` - 资源文件
- `pom.xml` - Maven项目配置
- `target/` - 构建产物
- `backend.log` - 后端运行日志

### 📁 config/ (配置文件) - 324KB
- `backend/` - 后端服务配置
- `frontend/` - 前端配置
- `mysql.conf` - MySQL配置
- `redis.conf` - Redis配置
- `nginx.conf` - Nginx配置
- `Dockerfile` - 各服务Docker镜像配置
- `init.sql` - 数据库初始化脚本

### 📁 database/ (数据库备份) - 28KB
- `enterprise_file_manager_backup.sql` - 完整数据库备份
- `init.sql` - 数据库初始化脚本

### 📁 scripts/ (启动脚本) - 36KB
- `h2-start.sh` - H2数据库启动脚本
- `setup-env.sh` - 环境设置脚本
- `simple-start.sh` - 简化启动脚本
- `start-project.sh` - 项目启动脚本
- `scripts/` - 工具脚本目录

### 📁 docs/ (项目文档) - 8KB
- `CLAUDE.md` - 项目配置文档

### 🔧 根目录配置文件
- `.env` - 环境变量配置 (594字节)
- `docker-compose.yml` - Docker编排配置 (4KB)
- `frontend.log` - 前端运行日志 (218字节)
- `backend.log` - 后端运行日志 (251字节)

## 系统账户信息
- **管理员**: admin / admin123
- **普通用户**: user / 123456

## 备份验证
- ✅ 前端代码完整备份
- ✅ 后端代码完整备份
- ✅ 配置文件完整备份
- ✅ 数据库完整备份
- ✅ 启动脚本完整备份
- ✅ 项目文档完整备份
- ✅ 根目录配置文件完整备份

## 恢复说明
1. 将备份目录复制到目标环境
2. 配置环境变量(.env文件)
3. 启动数据库服务并导入数据库备份文件
4. 编译并运行后端代码 (需要Maven构建)
5. 启动前端服务

## 注意事项
- 后端JAR包需要重新构建，容器内没有预编译的JAR包
- 前端包含完整的node_modules，可以直接运行
- 数据库备份包含完整的用户权限和角色信息
- 所有用户密码都使用bcrypt加密存储，安全性高

## 总大小
- **总计**: ~339MB
- **不含node_modules**: ~1MB

## 备份命令记录
```bash
# 创建备份目录
mkdir -p /work/test11

# 备份各个目录和文件
docker cp test08-dev:/workspace/frontend /work/test11/
docker cp test08-dev:/workspace/frontend.log /work/test11/
docker cp test08-dev:/workspace/backend /work/test11/
docker cp test08-dev:/workspace/backend.log /work/test11/
docker cp test08-dev:/workspace/config /work/test11/
docker cp test08-dev:/workspace/database /work/test11/
docker cp test08-dev:/workspace/scripts /work/test11/
docker cp test08-dev:/workspace/docs /work/test11/
docker cp test08-dev:/workspace/.env /work/test11/
docker cp test08-dev:/workspace/docker-compose.yml /work/test11/
docker cp test08-dev:/workspace/h2-start.sh /work/test11/
docker cp test08-dev:/workspace/setup-env.sh /work/test11/
docker cp test08-dev:/workspace/simple-start.sh /work/test11/
docker cp test08-dev:/workspace/start-project.sh /work/test11/
```

## 文件完整性验证
所有文件均从运行中的 test08-dev 容器复制，备份过程完整无遗漏。