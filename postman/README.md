# CheeseIM Message Service

消息服务模块，参照OpenIM的msg服务实现，负责消息的接收、处理、存储和查询。

## 🚀 功能特性

### 核心功能
- **消息发送** - 支持单聊、群聊、通知消息
- **消息存储** - MongoDB持久化存储，支持索引优化
- **消息查询** - 支持历史消息查询、搜索功能
- **消息管理** - 支持消息撤回、已读状态管理
- **序列号管理** - 会话级别的消息序列号生成

### 技术特性
- **Kafka集成** - 异步消息处理，发送到toRedisTopic
- **MongoDB存储** - 高性能文档数据库，支持复合索引
- **Redis缓存** - 序列号缓存，提升性能
- **Dubbo RPC** - 服务间通信，支持负载均衡
- **定时任务** - 自动清理过期消息

## 📋 API接口

### RPC接口 (Dubbo)
```java
// 发送消息
SendMsgResp sendMsg(SendMsgReq request)

// 批量发送消息  
SendMsgResp[] batchSendMsg(SendMsgReq[] requests)

// 获取会话消息历史
List<Message> getConversationHistory(String conversationID, Long startSeq, Integer count)

// 获取单聊消息历史
List<Message> getSingleChatHistory(String userID1, String userID2, Long startSeq, Integer count)

// 获取群聊消息历史
List<Message> getGroupChatHistory(String groupID, Long startSeq, Integer count)

// 搜索消息
List<Message> searchMessages(String userID, String keyword, Integer page, Integer size)

// 标记消息为已读
Boolean markMessagesAsRead(String userID, List<String> serverMsgIDs)

// 撤回消息
Boolean revokeMessage(String userID, String serverMsgID)
```

### REST API
```bash
# 发送消息
POST /api/v1/message/send

# 获取会话消息历史
GET /api/v1/message/conversation/{conversationID}/history

# 获取单聊消息历史
GET /api/v1/message/single-chat/history?userID1=xxx&userID2=xxx

# 获取群聊消息历史
GET /api/v1/message/group/{groupID}/history

# 搜索消息
GET /api/v1/message/search?userID=xxx&keyword=xxx

# 标记消息为已读
POST /api/v1/message/mark-read?userID=xxx

# 撤回消息
POST /api/v1/message/revoke?userID=xxx&serverMsgID=xxx
```

## 🏗️ 架构设计

### 消息流程
1. **接收消息** - 通过RPC接口接收postoffice发送的消息
2. **参数校验** - 验证消息格式、用户权限等
3. **生成序列号** - 为消息分配会话级别的序列号
4. **发送到Kafka** - 将消息发送到toRedisTopic
5. **异步存储** - 监听toMongoTopic，异步存储到MongoDB

### 数据模型
```javascript
// 消息文档结构
{
  "_id": "ObjectId",
  "clientMsgID": "客户端消息ID",
  "serverMsgID": "服务端消息ID", 
  "sendID": "发送者ID",
  "recvID": "接收者ID",
  "groupID": "群组ID",
  "conversationID": "会话ID",
  "content": "消息内容",
  "contentType": 101,
  "sessionType": 1,
  "sendTime": 1640995200000,
  "seq": 123,
  "isRead": false,
  "platformID": 1
}

// 会话序列号文档结构
{
  "_id": "ObjectId",
  "conversationID": "会话ID",
  "seq": 123,
  "maxSeq": 123,
  "minSeq": 1,
  "createTime": 1640995200000,
  "updateTime": 1640995200000
}
```

### 索引设计
- **复合索引**: sendID + recvID + sendTime (单聊查询)
- **复合索引**: groupID + sendTime (群聊查询)  
- **复合索引**: conversationID + seq (会话消息查询)
- **单字段索引**: serverMsgID (唯一索引)
- **文本索引**: content (全文搜索)

## ⚙️ 配置说明

### application.yml
```yaml
server:
  port: 8081

spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: cheese_im
    redis:
      host: localhost
      port: 6379
      database: 1
  kafka:
    bootstrap-servers: localhost:9092

dubbo:
  application:
    name: cheese-im-message
  registry:
    address: nacos://localhost:8848

# 消息保留天数
cheese:
  im:
    message:
      retention-days: 30
```

## 🚀 启动方式

### 本地开发
```bash
# 启动依赖服务
docker-compose -f docker-compose.middleware.yml up -d

# 启动消息服务
./gradlew :message:bootRun
```

### Docker部署
```bash
# 构建镜像
docker build -t cheese-im-message:latest .

# 启动容器
docker run -d --name cheese-im-message \
  -p 8081:8081 \
  -e mongodb.host=mongodb \
  -e kafka.bootstrap-servers=kafka:9092 \
  cheese-im-message:latest
```

## 📊 监控指标

- **消息发送量** - 每秒处理的消息数量
- **存储延迟** - 消息存储到MongoDB的延迟
- **查询性能** - 消息查询的响应时间
- **错误率** - 消息处理失败的比例
- **存储空间** - MongoDB存储空间使用情况

## 🔧 运维管理

### 消息清理
- 定时任务每天凌晨2点自动清理过期消息
- 默认保留30天，可通过配置调整
- 支持手动触发清理任务

### 性能优化
- MongoDB索引优化，提升查询性能
- Redis缓存序列号，减少数据库访问
- Kafka异步处理，提升吞吐量
- 分页查询，避免大量数据传输

### 故障处理
- 消息发送失败自动重试
- MongoDB连接异常自动重连
- Kafka消费异常自动恢复
- 详细的错误日志记录
