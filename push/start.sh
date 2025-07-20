#!/bin/bash

# CheeseIM Push Service 启动脚本
# 参照OpenIM Server的push模块实现

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
echo "   REST API: http://localhost:8083/api/v1/push"
echo "   Health Check: http://localhost:8083/api/v1/push/health"
echo "   Push Statistics: http://localhost:8083/api/v1/push/stats/push"
echo ""
echo "📋 Available REST APIs:"
echo "   GET  /api/v1/push/health                    - 健康检查"
echo "   GET  /api/v1/push/status                    - 服务状态"
echo "   GET  /api/v1/push/stats/push                - 推送统计"
echo "   GET  /api/v1/push/stats/realtime            - 实时统计"
echo "   GET  /api/v1/push/stats/tokens              - 设备Token统计"
echo "   GET  /api/v1/push/users/{userID}/online     - 检查用户在线状态"
echo "   GET  /api/v1/push/users/{userID}/tokens     - 获取用户设备Token"
echo "   POST /api/v1/push/users/{userID}/tokens     - 保存用户设备Token"
echo "   DELETE /api/v1/push/users/{userID}/tokens   - 删除用户设备Token"
echo "   POST /api/v1/push/stats/reset               - 重置统计"
echo ""
echo "💡 Test with curl:"
echo "   curl http://localhost:8083/api/v1/push/health"
echo "   curl http://localhost:8083/api/v1/push/status"
echo "   curl http://localhost:8083/api/v1/push/stats/push"
echo ""
echo "📊 Kafka Topics:"
echo "   - cheese_im_to_push        (消费) - 接收推送消息请求"
echo "   - cheese_im_offline_push   (消费/生产) - 离线推送消息"
echo ""
echo "🔗 Dubbo Services:"
echo "   - PostofficeOnlinePushService (消费) - 调用postoffice进行在线推送"
echo ""
echo "📱 Supported Push Providers:"
echo "   - APNs (Apple Push Notification)"
echo "   - FCM (Firebase Cloud Messaging)"
echo "   - JPush (极光推送)"
echo "   - Huawei Push Kit"
echo "   - Xiaomi Push"
echo ""
echo "⚙️  Push Flow:"
echo "   1. 监听 toPushTopic"
echo "   2. 通过Dubbo调用postoffice进行在线推送"
echo "   3. 在线推送失败的用户发送到 offlinePushTopic"
echo "   4. 监听 offlinePushTopic 进行第三方离线推送"
echo ""

# 启动应用
exec ./gradlew :push:bootRun
