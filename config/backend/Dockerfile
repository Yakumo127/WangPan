# 多阶段构建 - 构建阶段
FROM maven:3.8.3-openjdk-17 AS builder

WORKDIR /app

# 复制 Maven 配置文件与源码（从仓库根中的 backend/）
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src

# 下载依赖并构建应用
RUN mvn -B -e -DskipTests dependency:go-offline \
 && mvn -B -DskipTests clean package

# 运行阶段
FROM openjdk:17-jre-slim

WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    curl \
    procps \
    && rm -rf /var/lib/apt/lists/*

# 创建非root用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 创建必要的目录
RUN mkdir -p /app/storage /app/logs /app/temp && \
    chown -R appuser:appuser /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 配置文件使用打包进 JAR 的 application-docker.yml（源于 backend/src/main/resources）

# 创建存储目录
RUN mkdir -p /cunchu && chown appuser:appuser /cunchu

# 设置环境变量
ENV JAVA_OPTS="-Xmx1024m -Xms512m"
ENV SPRING_PROFILES_ACTIVE=docker

# 健康检查
HEALTHCHECK --interval=30s --timeout=30s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
