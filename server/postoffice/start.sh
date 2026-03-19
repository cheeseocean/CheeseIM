#!/bin/bash

# CheeseIM Postoffice Gateway 启动脚本

echo "🚀 Starting CheeseIM Postoffice Gateway..."

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 11 or higher."
    exit 1
fi

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo "❌ Java version must be 11 or higher. Current version: $JAVA_VERSION"
    exit 1
fi

# 设置环境变量
export JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
export SPRING_PROFILES_ACTIVE=prod

# 检查依赖服务
echo "🔍 Checking dependencies..."

# 检查Redis
if ! nc -z localhost 6379 2>/dev/null; then
    echo "⚠️  Redis is not running on localhost:6379"
    echo "   Please start Redis: docker run -d --name redis -p 6379:6379 redis:latest"
fi

# 检查Kafka
if ! nc -z localhost 9092 2>/dev/null; then
    echo "⚠️  Kafka is not running on localhost:9092"
    echo "   Please start Kafka cluster"
fi

# 检查Nacos
if ! nc -z localhost 8848 2>/dev/null; then
    echo "⚠️  Nacos is not running on localhost:8848"
    echo "   Please start Nacos: docker run -d --name nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:latest"
fi

# 检查postbox服务
if ! nc -z localhost 8081 2>/dev/null; then
    echo "⚠️  Postbox service is not running on localhost:8081"
    echo "   Please start postbox service first: ./gradlew :postbox:bootRun"
fi

echo "✅ Dependency check completed"

# 构建项目
echo "🔨 Building project..."
if ! ./gradlew :postoffice:build -x test; then
    echo "❌ Build failed"
    exit 1
fi

# 启动服务
echo "🚀 Starting Postoffice Gateway..."
echo "   WebSocket Server: ws://localhost:8080/ws"
echo ""
echo "📋 Available runtime surfaces:"
echo "   WebSocket gateway logs"
echo "   TCP gateway logs"
echo ""
echo "🎮 Run smoke test:"
echo "   ./gradlew :postoffice:test --tests \"com.cheeseocean.im.postoffice.ImFlowSmokeTest\""
echo ""

# 启动应用
exec ./gradlew :postoffice:bootRun
