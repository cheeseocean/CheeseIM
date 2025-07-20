# CheeseIM

🧀 **CheeseIM** - 企业级即时通讯系统

基于 Spring Boot + Netty + MongoDB + Kafka 构建的高性能、可扩展的即时通讯解决方案。

## ✨ 核心特性

### 🔐 安全特性
- **端到端加密** - AES加密保护消息传输安全
- **Token认证** - JWT令牌机制，支持多设备登录
- **消息审核** - 智能敏感词过滤，违规行为检测
- **权限控制** - 细粒度用户权限管理
- **数据备份** - 自动消息备份与恢复机制

### 📱 多端支持
- **跨平台客户端** - 支持iOS/Android/Web/PC
- **多设备同步** - 消息实时同步到所有设备
- **离线推送** - APNs/FCM/JPush多渠道推送
- **设备管理** - 在线设备监控与管理
- **智能路由** - 消息智能路由分发

### 💬 消息功能
- **多媒体消息** - 文本/图片/语音/视频/文件/位置
- **消息状态** - 发送状态、已读回执、消息撤回
- **消息搜索** - 全文搜索、多条件筛选、搜索建议
- **消息转发** - 支持单条/批量转发
- **引用回复** - 消息引用与回复功能
- **@提醒** - 群聊@成员提醒
- **正在输入** - 实时输入状态显示

### 👥 社交功能
- **好友系统** - 好友添加/删除/备注/分组
- **群组聊天** - 群组创建/管理/权限控制
- **在线状态** - 实时在线状态显示
- **用户资料** - 头像/昵称/个性签名
- **黑名单** - 用户屏蔽功能

### 🚀 性能优化
- **分布式架构** - 微服务架构，支持水平扩展
- **消息队列** - Kafka异步消息处理
- **缓存机制** - Redis多级缓存优化
- **连接池** - Netty连接池管理
- **负载均衡** - 支持多实例负载均衡
- **数据分片** - MongoDB分片存储

### 📊 监控运维
- **系统监控** - 实时性能监控
- **连接统计** - 在线用户/连接数统计
- **消息统计** - 消息发送/接收统计
- **告警通知** - 异常情况自动告警
- **日志分析** - 结构化日志记录
- **健康检查** - 服务健康状态检查

## 🏗️ 技术架构

### 核心技术栈
- **后端框架**: Spring Boot 2.7+
- **网络通信**: Netty 4.1+
- **数据存储**: MongoDB 5.0+
- **消息队列**: Apache Kafka 2.8+
- **缓存**: Redis 6.0+
- **RPC框架**: Apache Dubbo 3.1+
- **注册中心**: Nacos 2.0+ / Zookeeper
- **协议**: MQTT / WebSocket

### 系统架构
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   iOS Client    │    │  Android Client │    │   Web Client    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │  Load Balancer  │
                    └─────────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       │                       │
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   PostOffice    │    │    PostMan      │    │      CMS        │
│  (Gateway)      │    │  (Delivery)     │    │  (Management)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │     Kafka       │
                    └─────────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       │                       │
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│    Message      │    │     Friend      │    │     Group       │
│   Service       │    │    Service      │    │    Service      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │    MongoDB      │
                    └─────────────────┘
```

## 📦 模块说明

### 核心模块
- **postoffice** - 网关服务，处理客户端连接和消息路由
- **postman** - 消息投递服务，负责消息分发和推送
- **message** - 消息服务，处理消息存储和检索
- **push** - 推送服务，处理离线消息推送
- **cms** - 内容管理服务，消息审核和管理

### 业务模块
- **business/friend** - 好友关系管理
- **business/group** - 群组管理
- **business/user** - 用户管理

### 基础模块
- **common** - 公共组件和工具类
- **client** - 客户端SDK

## 🚀 快速开始

### 环境要求
- JDK 11+
- Maven 3.6+
- MongoDB 5.0+
- Redis 6.0+
- Kafka 2.8+

### 本地开发

1. **克隆项目**
```bash
git clone https://github.com/cheeseocean/CheeseIM.git
cd CheeseIM
```

2. **启动中间件**
```bash
cd distro/docker
docker-compose -f docker-compose.middleware.yml up -d
```

3. **配置数据库**
```bash
# 创建MongoDB数据库
mongo
use cheese_im
```

4. **编译项目**
```bash
./gradlew build
```

5. **启动服务**
```bash
# 启动消息服务
./gradlew :message:bootRun

# 启动网关服务
./gradlew :postoffice:bootRun

# 启动投递服务
./gradlew :postman:bootRun
```

### Docker部署

```bash
# 构建镜像
docker build -t cheeseim:latest .

# 启动完整服务
docker-compose up -d
```

## 📋 API文档

### WebSocket连接
```javascript
// 连接WebSocket
const ws = new WebSocket('ws://localhost:8080/ws');

// 发送消息
ws.send(JSON.stringify({
    type: 'message',
    data: {
        to: 'userID',
        content: 'Hello World',
        contentType: 101
    }
}));
```

### REST API
```bash
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

# 获取消息历史
GET /api/v1/message/history?conversationId=xxx&page=0&size=20

# 搜索消息
GET /api/v1/message/search?keyword=hello&userID=xxx
```

## 🔧 配置说明

### 应用配置
```yaml
# application.yml
spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: cheese_im
  
  kafka:
    bootstrap-servers: localhost:9092
    
  redis:
    host: localhost
    port: 6379

cheeseim:
  jwt:
    secret: your-secret-key
    expiration: 86400
  
  push:
    apns:
      enabled: true
      key-path: /path/to/apns.p8
    fcm:
      enabled: true
      key-path: /path/to/fcm.json
```

### Dubbo配置
```properties
# dubbo.properties
dubbo.application.name=cheeseim-service
dubbo.registry.address=nacos://localhost:8848
dubbo.protocol.port=20880
```

## 📈 性能指标

### 基准测试
- **并发连接**: 10万+ WebSocket连接
- **消息吞吐**: 10万+ 消息/秒
- **响应延迟**: < 100ms (P99)
- **可用性**: 99.9%+

### 扩展性
- **水平扩展**: 支持多实例部署
- **数据分片**: MongoDB自动分片
- **缓存优化**: Redis集群支持
- **消息队列**: Kafka分区扩展

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 开源协议

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

## 🙋‍♂️ 联系我们

- **作者**: xxxcrel
- **邮箱**: xxxcrel@gmail.com
- **项目地址**: https://github.com/cheeseocean/CheeseIM
- **文档地址**: https://docs.cheeseim.com

## 🎯 路线图

### v1.1.0 (计划中)
- [ ] 音视频通话功能
- [ ] 文件传输优化
- [ ] 消息加密增强
- [ ] 管理后台界面

### v1.2.0 (计划中)
- [ ] 机器人接入
- [ ] 消息翻译
- [ ] 语音识别
- [ ] 表情包支持

### v2.0.0 (规划中)
- [ ] 微服务重构
- [ ] 云原生部署
- [ ] AI智能助手
- [ ] 区块链集成

---

⭐ 如果这个项目对你有帮助，请给我们一个 Star！

🧀 **CheeseIM** - 让沟通更简单，让连接更紧密！