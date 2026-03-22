#!/bin/bash

# CheeseIM Postbox 启动脚本

echo "🚀 Starting CheeseIM Postbox..."

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
export JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
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

echo "✅ Dependency check completed"

# 构建项目
echo "🔨 Building project..."
if ! ./gradlew :postbox:build -x test; then
    echo "❌ Build failed"
    exit 1
fi

# 启动服务
echo "🚀 Starting Postbox..."
echo "   Storage boundary logs will show Mongo/Kafka readiness"
echo ""
echo "📋 Module Role:"
echo "   - 持久化 message_block / message_id_mapping 历史真相"
echo "   - 支持离线拉取与 ack/read/recall 收敛"
echo ""
echo "🎮 Run verification:"
echo "   ./gradlew :postbox:test"
echo ""
echo "📊 Kafka Topics:"
echo "   - delivery-related events are produced and consumed through the rebuilt IM flow"
echo ""

# 启动应用
exec ./gradlew :postbox:bootRun
