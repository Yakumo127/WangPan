# 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app

# 复制 package.json 和 package-lock.json
COPY package*.json ./

# 设置 npm 源为淘宝源
RUN npm config set registry https://registry.npmmirror.com

# 安装依赖
RUN npm ci --only=production

# 复制源代码
COPY . .

# 构建应用
RUN npm run build

# 生产阶段
FROM nginx:alpine

# 安装必要的工具
RUN apk add --no-cache curl

# 创建非root用户
RUN addgroup -g 1001 -S appuser && \
    adduser -S appuser -u 1001 -G appuser

# 从构建阶段复制构建结果
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf

# 创建必要的目录
RUN mkdir -p /var/cache/nginx /var/log/nginx /var/lib/nginx/tmp && \
    chown -R appuser:appuser /var/cache/nginx /var/log/nginx /var/lib/nginx

# 设置正确的权限
RUN chown -R appuser:appuser /usr/share/nginx/html && \
    chown -R appuser:appuser /var/cache/nginx && \
    chown -R appuser:appuser /var/log/nginx

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:80/ || exit 1

# 暴露端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]