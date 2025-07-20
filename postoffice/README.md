# CheeseIM Postoffice Gateway

消息网关服务，参照OpenIM Server的msggateway实现，基于Netty自定义协议，负责客户端连接管理、消息路由和实时推送。

## 🚀 功能特性

### 核心功能
- **WebSocket服务** - 基于Netty的高性能WebSocket服务器
- **连接管理** - 支持多端登录的连接管理
- **用户认证** - JWT Token认证机制
- **消息路由** - 实时消息推送和转发
- **在线状态** - 用户在线状态管理

### 技术特性
- **高并发** - Netty异步非阻塞架构
- **多端支持** - 支持iOS、Android、Web等多平台
- **负载均衡** - 支持集群部署和负载均衡
- **故障恢复** - 连接断线重连和故障恢复
- **监控统计** - 连接数统计和性能监控

## 📋 API接口

### WebSocket连接
```javascript
// 连接WebSocket
const ws = new WebSocket('ws://localhost:8080/ws');

// 认证
ws.send(JSON.stringify({
    msgType: 1101,
    operationID: "op_123456",
    data: {
        token: "your_jwt_token",
        userID: "user123",
        platformID: 1
    }
}));

// 发送消息
ws.send(JSON.stringify({
    msgType: 2001,
    operationID: "op_123457",
    data: {
        clientMsgID: "msg_123456",
        recvID: "user456",
        content: "Hello World",
        contentType: 101,
        sessionType: 1
    }
}));

// 心跳
ws.send(JSON.stringify({
    msgType: 1201,
    operationID: "op_123458"
}));
```

### REST API
```bash
# 生成Token
POST /api/v1/message/auth/token?userID=user123&platformID=1

# 验证Token
POST /api/v1/message/auth/validate?token=your_jwt_token

# 发送消息
POST /api/v1/message/send
{
    "msgData": {
        "sendID": "user1",
        "recvID": "user2", 
        "content": "Hello",
        "contentType": 101,
        "sessionType": 1
    }
}

# 获取在线用户
GET /api/v1/message/online-users

# 检查用户在线状态
GET /api/v1/message/user/{userID}/online

# 健康检查
GET /api/v1/message/health
```

## 🏗️ 架构设计

### 消息流程
1. **客户端连接** - 建立WebSocket连接
2. **用户认证** - JWT Token验证
3. **消息接收** - 接收客户端消息
4. **消息路由** - 调用message服务处理
5. **实时推送** - 监听Kafka推送消息给在线用户

### 连接管理
```java
// 连接映射关系
Map<String, UserConnection> connectionMap;        // 连接ID -> 连接
Map<String, Set<String>> userConnectionMap;       // 用户ID -> 连接ID列表
Map<Channel, String> channelConnectionMap;        // Channel -> 连接ID

// Redis同步
cheese_im:user:online:{userID}     // 用户在线状态
cheese_im:connection:{connID}      // 连接信息
```

### 消息类型
- **1001-1004**: 连接相关 (连接、成功、失败、断开)
- **1101-1103**: 认证相关 (认证、成功、失败)
- **1201-1202**: 心跳相关 (心跳、响应)
- **2001-2005**: 消息相关 (发送、响应、接收、已读、撤回)
- **3001-3003**: 用户状态相关 (上线、下线、状态变更)
- **4001-4002**: 会话相关 (会话变更、正在输入)
- **5001-5005**: 群组相关 (创建、解散、成员变更等)
- **6001-6004**: 好友相关 (添加、删除、请求等)
- **7001-7002**: 系统通知 (通知、强制下线)

## ⚙️ 配置说明

### application.yml
```yaml
server:
  port: 8080

cheese:
  im:
    websocket:
      port: 8080          # WebSocket端口
      path: /ws           # WebSocket路径
      boss-threads: 1     # Boss线程数
      worker-threads: 0   # Worker线程数(0=CPU核数*2)
      idle-timeout: 300   # 空闲超时(秒)
    security:
      jwt-secret: "CheeseIM2024Secret!"
      token-expiration: 86400000  # Token过期时间(毫秒)

spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 1
  kafka:
    bootstrap-servers: localhost:9092

dubbo:
  registry:
    address: nacos://localhost:8848
```

## 🚀 启动方式

### 本地开发
```bash
# 启动依赖服务
docker-compose -f docker-compose.middleware.yml up -d

# 启动网关服务
./gradlew :postoffice:bootRun
```

### Docker部署
```bash
# 构建镜像
docker build -t cheese-im-postoffice:latest .

# 启动容器
docker run -d --name cheese-im-postoffice \
  -p 8080:8080 \
  -e redis.host=redis \
  -e kafka.bootstrap-servers=kafka:9092 \
  cheese-im-postoffice:latest
```

## 📊 监控指标

- **连接数统计** - 总连接数、在线用户数、平均连接数
- **消息统计** - 消息发送量、推送量、错误率
- **性能指标** - 响应时间、吞吐量、内存使用
- **错误监控** - 连接失败、认证失败、消息处理失败

## 🔧 运维管理

### 连接管理
- 自动清理非活跃连接(5分钟超时)
- 支持强制下线用户
- 连接数限制和流控

### 故障处理
- WebSocket连接断线自动重连
- Redis连接异常自动恢复
- Kafka消费异常自动重试
- 详细的错误日志和监控

### 集群部署
- 支持多实例部署
- 通过Redis共享在线状态
- Dubbo服务注册发现
- 负载均衡和故障转移

## 🔒 安全机制

### JWT认证
- 基于JWT的无状态认证
- Token过期自动刷新
- 支持多端登录控制

### 连接安全
- IP白名单限制
- 连接频率限制
- 恶意连接检测

### 数据安全
- 敏感数据加密传输
- 消息内容过滤
- 用户权限验证

## 🚀 快速开始

### 1. 启动依赖服务

```bash
# 启动Redis
docker run -d --name redis -p 6379:6379 redis:latest

# 启动Kafka
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  confluentinc/cp-kafka:latest

# 启动Nacos
docker run -d --name nacos -p 8848:8848 \
  -e MODE=standalone \
  nacos/nacos-server:latest
```

### 2. 启动postbox服务

```bash
# 先启动消息服务（postbox）
./gradlew :postbox:bootRun
```

### 3. 启动postoffice网关

```bash
# 启动网关服务
./gradlew :postoffice:bootRun
```

### 4. 测试连接

```bash
# 使用测试客户端
./gradlew :postoffice:test --tests WebSocketTestClient

# 或者使用curl测试REST API
curl http://localhost:8080/api/v1/postoffice/health
```

## 🧪 测试指南

### WebSocket连接测试

1. **生成测试Token**：
```bash
curl -X POST "http://localhost:8080/api/v1/postoffice/auth/token?userID=test001&platformID=2"
```

2. **使用测试客户端连接**：
```bash
# 运行测试客户端
java -cp build/classes/java/test com.cheeseocean.im.postoffice.WebSocketTestClient

# 在交互模式中执行：
auth test001 2 <your_token>
heartbeat
send test002 Hello World
```

3. **验证连接状态**：
```bash
curl http://localhost:8080/api/v1/postoffice/users/test001/online
```

### 消息流程测试

1. **发送消息** → postoffice接收 → 调用postbox RPC → 发送到Kafka
2. **Kafka消息** → postman监听 → 发送到推送Topic
3. **推送消息** → postoffice监听 → 推送给在线用户

## 📋 API文档

### REST API

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/v1/postoffice/health` | GET | 健康检查 |
| `/api/v1/postoffice/status` | GET | 服务器状态 |
| `/api/v1/postoffice/connections/stats` | GET | 连接统计 |
| `/api/v1/postoffice/users/online` | GET | 在线用户列表 |
| `/api/v1/postoffice/users/{userID}/online` | GET | 用户在线状态 |
| `/api/v1/postoffice/auth/token` | POST | 生成Token |
| `/api/v1/postoffice/auth/validate` | POST | 验证Token |
| `/api/v1/postoffice/users/{userID}/kick` | POST | 强制下线 |

### WebSocket消息类型

| 消息类型 | 代码 | 描述 |
|----------|------|------|
| 连接请求 | 1001 | 客户端连接请求 |
| 认证请求 | 1101 | 用户认证 |
| 心跳请求 | 1201 | 保持连接活跃 |
| 发送消息 | 2001 | 发送聊天消息 |
| 接收消息 | 2003 | 接收消息通知 |

## 📝 开发指南

### 添加新消息类型
1. 在`WSMessageType`中定义消息类型常量
2. 创建对应的`MessageHandler`实现类
3. 在`MessageHandlerFactory`中自动注册

### 扩展认证机制
1. 实现`AuthService`接口
2. 添加自定义认证逻辑
3. 配置认证策略

### 自定义连接管理
1. 扩展`ConnectionManager`
2. 实现自定义连接策略
3. 添加连接事件监听

### 多端登录策略配置
```java
// 在配置中设置多端登录策略
connectionManager.setMultiLoginStrategy(MultiLoginStrategy.SAME_TERMINAL_KICK);
```
