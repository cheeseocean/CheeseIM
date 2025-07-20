# CheeseIM Postman Message Transfer

消息传输服务模块，参照OpenIM Server的msgtransfer实现，负责消息的路由、分发和传输。

## 🚀 功能特性

### 核心功能
- **消息路由** - 智能消息路由，支持单聊、群聊、通知消息
- **消息分发** - 高效消息分发，支持在线用户检测
- **消息传输** - 可靠消息传输，支持推送和存储分离
- **在线检测** - 实时在线用户状态检测和管理
- **群组管理** - 群组成员管理和消息分发
- **统计监控** - 完整的消息传输统计和实时监控

### 技术特性
- **Kafka集成** - 高性能消息队列，支持消息路由和分发
- **Redis缓存** - 在线用户状态缓存，群组成员缓存
- **智能路由** - 根据消息类型和接收者智能路由
- **异步处理** - 异步消息处理，提升系统吞吐量
- **监控统计** - 实时统计和监控，支持性能分析

## 🏗️ 架构设计

### 参照OpenIM Server架构

```
postman (msgtransfer) 架构：
├── MessageTransferListener    - 消息传输监听器
├── MessageRouterService      - 消息路由服务
├── MessageTransferService    - 消息传输服务
├── OnlineUserService        - 在线用户服务
├── GroupMemberService       - 群组成员服务
├── MessageStatisticsService - 消息统计服务
└── PostmanController        - REST API控制器
```

### 消息流程

```
1. 接收消息 (from postbox via Kafka)
   ↓
2. 消息路由 (MessageRouterService)
   ├── 单聊路由 → 检查接收者在线状态
   ├── 群聊路由 → 获取群组成员列表
   └── 通知路由 → 确定推送目标
   ↓
3. 消息传输 (MessageTransferService)
   ├── 推送消息 → TO_PUSH_TOPIC (给postoffice)
   ├── 存储消息 → TO_MONGO_TOPIC (给postbox)
   └── 状态更新 → MSG_STATUS_UPDATE_TOPIC
   ↓
4. 统计记录 (MessageStatisticsService)
```

## 📋 API接口

### REST API

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/v1/postman/health` | GET | 健康检查 |
| `/api/v1/postman/status` | GET | 服务状态 |
| `/api/v1/postman/stats/transfer` | GET | 消息传输统计 |
| `/api/v1/postman/stats/realtime` | GET | 实时统计 |
| `/api/v1/postman/stats/online` | GET | 在线用户统计 |
| `/api/v1/postman/users/online` | GET | 在线用户列表 |
| `/api/v1/postman/users/{userID}/online` | GET | 用户在线状态 |
| `/api/v1/postman/stats/reset` | POST | 重置统计 |
| `/api/v1/postman/config` | GET | 服务配置 |
| `/api/v1/postman/system` | GET | 系统信息 |

### Kafka Topics

| Topic | 类型 | 描述 |
|-------|------|------|
| `cheese_im_to_redis` | 消费 | 接收消息传输请求 |
| `cheese_im_to_push` | 生产 | 发送推送消息给postoffice |
| `cheese_im_to_mongo` | 生产 | 发送存储消息给postbox |
| `cheese_im_msg_status_update` | 生产 | 发送消息状态更新 |
| `cheese_im_user_online_status` | 生产 | 发送用户状态变更 |

## ⚙️ 配置说明

### application.yml
```yaml
server:
  port: 8082

spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: postman-group
    producer:
      acks: all
      retries: 3

cheese:
  im:
    postman:
      transfer:
        batch-size: 100
        retry-times: 3
        timeout-ms: 5000
      statistics:
        enabled: true
        cache-expire-hours: 24
      online-user:
        cache-expire-minutes: 10
```

## 🚀 启动方式

### 本地开发
```bash
# 启动依赖服务
docker run -d --name redis -p 6379:6379 redis:latest
docker run -d --name kafka -p 9092:9092 confluentinc/cp-kafka:latest

# 启动消息传输服务
./start.sh
# 或者
./gradlew :postman:bootRun
```

### Docker部署
```bash
# 构建镜像
docker build -t cheese-im-postman:latest .

# 启动容器
docker run -d --name cheese-im-postman \
  -p 8082:8082 \
  -e redis.host=redis \
  -e kafka.bootstrap-servers=kafka:9092 \
  cheese-im-postman:latest
```

## 🧪 测试指南

### 1. 健康检查
```bash
curl http://localhost:8082/api/v1/postman/health
```

### 2. 查看服务状态
```bash
curl http://localhost:8082/api/v1/postman/status
```

### 3. 查看消息传输统计
```bash
curl http://localhost:8082/api/v1/postman/stats/transfer
```

### 4. 查看实时统计
```bash
curl http://localhost:8082/api/v1/postman/stats/realtime
```

### 5. 设置用户在线状态（测试）
```bash
curl -X POST "http://localhost:8082/api/v1/postman/users/test001/online?platformID=2&online=true"
```

### 6. 查看在线用户
```bash
curl http://localhost:8082/api/v1/postman/users/online
```

## 📊 监控指标

### 消息传输统计
- **总消息数** - 处理的消息总数
- **成功消息数** - 成功传输的消息数
- **失败消息数** - 传输失败的消息数
- **成功率** - 消息传输成功率
- **消息类型分布** - 各类型消息的分布统计
- **会话类型分布** - 单聊、群聊、通知消息分布
- **路由策略分布** - 各路由策略的使用统计

### 实时统计
- **每秒消息数** - 实时消息处理速率
- **每分钟消息数** - 分钟级消息处理量
- **每小时消息数** - 小时级消息处理量
- **在线用户数** - 当前在线用户数量
- **连接数** - 当前总连接数
- **平均处理时间** - 消息处理平均耗时

### 在线用户统计
- **总在线用户数** - 当前在线用户总数
- **总连接数** - 当前总连接数
- **平台分布** - 各平台的连接分布

## 🔧 运维管理

### 性能优化
- **Kafka配置优化** - 调整批处理大小和并发数
- **Redis连接池优化** - 调整连接池参数
- **消息路由优化** - 缓存群组成员信息
- **统计数据优化** - 定期清理过期统计数据

### 故障处理
- **消息传输失败** - 自动重试机制
- **Redis连接异常** - 自动重连和降级处理
- **Kafka消费异常** - 自动恢复和错误处理
- **内存泄漏监控** - JVM内存监控和告警

### 扩展性
- **水平扩展** - 支持多实例部署
- **负载均衡** - Kafka分区和消费者组
- **缓存分片** - Redis集群支持
- **监控集成** - Prometheus指标导出

## 🔗 相关模块

- **postbox** - 消息服务，提供RPC接口和消息存储
- **postoffice** - 网关服务，提供WebSocket连接和实时推送
- **common** - 公共模块，提供实体类和常量定义

## 📝 开发指南

### 添加新的路由策略
1. 在`MessageRouterService.RouteStrategy`中添加新策略
2. 在`MessageRouterServiceImpl`中实现路由逻辑
3. 更新统计服务记录新策略

### 扩展消息类型支持
1. 在`MessageConstants`中定义新消息类型
2. 在路由服务中添加对应处理逻辑
3. 更新统计服务支持新类型

### 自定义监控指标
1. 在`MessageStatisticsService`中添加新指标
2. 在实现类中添加统计逻辑
3. 在控制器中暴露新的API接口
