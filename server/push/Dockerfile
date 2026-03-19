# CheeseIM Push Service Dockerfile
FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    curl \
    netcat \
    && rm -rf /var/lib/apt/lists/*

# 创建应用用户
RUN groupadd -r cheeseuser && useradd -r -g cheeseuser cheeseuser

# 复制应用JAR文件
COPY build/libs/push-*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && chown -R cheeseuser:cheeseuser /app

# 切换到应用用户
USER cheeseuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8083/api/v1/push/health || exit 1

# 暴露端口
EXPOSE 8083

# JVM参数
ENV JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
