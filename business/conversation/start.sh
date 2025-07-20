#!/bin/bash

# CheeseIM Conversation Service 启动脚本

# 设置环境变量
export JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
export SPRING_PROFILES_ACTIVE=dev

# 设置数据库连接
export MONGO_HOST=${MONGO_HOST:-localhost}
export MONGO_PORT=${MONGO_PORT:-27017}
export MONGO_DATABASE=${MONGO_DATABASE:-cheese_im}

# 设置Redis连接
export REDIS_HOST=${REDIS_HOST:-localhost}
export REDIS_PORT=${REDIS_PORT:-6379}
export REDIS_DATABASE=${REDIS_DATABASE:-2}

# 设置Nacos连接
export NACOS_HOST=${NACOS_HOST:-localhost}
export NACOS_PORT=${NACOS_PORT:-8848}

# 设置Dubbo端口
export DUBBO_PORT=${DUBBO_PORT:-20882}

echo "启动CheeseIM Conversation Service..."
echo "MongoDB: ${MONGO_HOST}:${MONGO_PORT}/${MONGO_DATABASE}"
echo "Redis: ${REDIS_HOST}:${REDIS_PORT}/${REDIS_DATABASE}"
echo "Nacos: ${NACOS_HOST}:${NACOS_PORT}"
echo "Dubbo Port: ${DUBBO_PORT}"

# 构建项目
echo "构建项目..."
cd "$(dirname "$0")"
../../gradlew :business:conversation:build -x test

if [ $? -ne 0 ]; then
    echo "构建失败，退出"
    exit 1
fi

# 启动服务
echo "启动服务..."
java $JAVA_OPTS -jar build/libs/conversation-1.0.0.jar

echo "服务已停止"
