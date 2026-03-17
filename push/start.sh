#!/bin/bash

# CheeseIM Push Service 启动脚本

echo "🚀 Starting CheeseIM Push Service..."

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

# 检查Nacos
if ! nc -z localhost 8848 2>/dev/null; then
    echo "⚠️  Nacos is not running on localhost:8848"
    echo "   Please start Nacos: docker run -d --name nacos -p 8848:8848 nacos/nacos-server:latest"
fi

echo "✅ Dependency check completed"

# 构建项目
echo "🔨 Building project..."
if ! ./gradlew :push:build -x test; then
    echo "❌ Build failed"
    exit 1
fi

# 启动服务
echo "🚀 Starting Push Service..."
echo "   Runtime status is exposed through application logs"
echo ""
echo "🔗 Push Boundary:"
echo "   - postman 决定是否需要离线推送"
echo "   - push 模块负责去重、取消和第三方投递"
echo ""
echo "📱 Supported Push Providers:"
echo "   - APNs (Apple Push Notification)"
echo "   - FCM (Firebase Cloud Messaging)"
echo "   - JPush (极光推送)"
echo "   - Huawei Push Kit"
echo "   - Xiaomi Push"
echo ""
echo "⚙️  Push Flow:"
echo "   1. postman 触发离线推送决策"
echo "   2. push 模块做去重并创建 PushAttempt"
echo "   3. 第三方 provider 执行投递"
echo "   4. 收到已送达/已读后取消无效待发 push"
echo ""
echo "🎮 Run verification:"
echo "   ./gradlew :push:test"
echo "   ./gradlew :postoffice:test --tests \"com.cheeseocean.im.postoffice.ImFlowSmokeTest\""
echo ""

# 启动应用
exec ./gradlew :push:bootRun
