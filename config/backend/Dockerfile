# 多阶段构建 - 构建阶段
FROM maven:3.8.3-openjdk-17 AS builder

WORKDIR /app

# 复制 Maven 配置文件
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# 安装 Maven Wrapper (如果存在)
RUN chmod +x mvnw

# 下载依赖
RUN ./mvnw dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN ./mvnw clean package -DskipTests -B

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

# 复制配置文件
COPY --chown=appuser:appuser docker/app/application-docker.yml /app/src/main/resources/application-docker.yml

# 创建存储目录
RUN mkdir -p /cunchu && chown appuser:appuser /cunchu

# 设置环境变量
ENV JAVA_OPTS="-Xmx1024m -Xms512m"
ENV SPRING_PROFILES_ACTIVE=docker

# 健康检查
HEALTHCHECK --interval=30s --timeout=30s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/actuator/health || exit 1

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]