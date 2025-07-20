#!/bin/bash

# CheeseIM Postman Message Transfer 启动脚本
# 参照OpenIM Server的msgtransfer实现

echo "🚀 Starting CheeseIM Postman Message Transfer Service..."

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
if ! ./gradlew :postman:build -x test; then
    echo "❌ Build failed"
    exit 1
fi

# 启动服务
echo "🚀 Starting Postman Message Transfer Service..."
echo "   REST API: http://localhost:8082/api/v1/postman"
echo "   Health Check: http://localhost:8082/api/v1/postman/health"
echo "   Statistics: http://localhost:8082/api/v1/postman/stats/transfer"
echo ""
echo "📋 Available REST APIs:"
echo "   GET  /api/v1/postman/health              - 健康检查"
echo "   GET  /api/v1/postman/status              - 服务状态"
echo "   GET  /api/v1/postman/stats/transfer      - 消息传输统计"
echo "   GET  /api/v1/postman/stats/realtime      - 实时统计"
echo "   GET  /api/v1/postman/stats/online        - 在线用户统计"
echo "   GET  /api/v1/postman/users/online        - 在线用户列表"
echo "   POST /api/v1/postman/stats/reset         - 重置统计"
echo ""
echo "💡 Test with curl:"
echo "   curl http://localhost:8082/api/v1/postman/health"
echo "   curl http://localhost:8082/api/v1/postman/status"
echo "   curl http://localhost:8082/api/v1/postman/stats/transfer"
echo ""
echo "📊 Kafka Topics:"
echo "   - cheese_im_to_redis     (消费) - 接收消息传输请求"
echo "   - cheese_im_to_push      (生产) - 发送推送消息"
echo "   - cheese_im_to_mongo     (生产) - 发送存储消息"
echo "   - cheese_im_msg_status_update (生产) - 发送状态更新"
echo ""

# 启动应用
exec ./gradlew :postman:bootRun
