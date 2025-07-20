# CheeseIM Conversation Service

会话服务模块，参照OpenIM的conversation服务实现，负责会话的创建、管理、查询和更新。

## 🚀 功能特性

### 核心功能
- **会话管理** - 支持单聊、群聊、通知会话
- **会话查询** - 获取用户所有会话、指定会话信息
- **会话设置** - 置顶、免打扰、私聊等属性设置
- **未读管理** - 未读消息数量统计和标记已读
- **草稿功能** - 会话草稿文本保存和管理
- **序列号管理** - 会话级别的消息序列号管理

### 技术特性
- **MongoDB存储** - 高性能文档数据库，支持复合索引
- **Redis缓存** - 会话信息缓存，提升性能
- **Dubbo RPC** - 服务间通信，支持负载均衡
- **Spring Boot** - 现代化的Java应用框架
- **RESTful API** - 标准的HTTP接口

## 📋 API接口

### RPC接口 (Dubbo)
```java
// 获取用户所有会话
GetAllConversationsResp getAllConversations(GetAllConversationsReq request)

// 获取指定会话信息
Conversation getConversation(String userID, String conversationID)

// 设置会话属性
SetConversationResp setConversation(SetConversationReq request)

// 批量设置会话属性
List<SetConversationResp> batchSetConversations(List<SetConversationReq> requests)

// 标记会话已读
Boolean markConversationAsRead(String userID, String conversationID, Long msgSeq)

// 获取用户未读消息总数
Integer getTotalUnreadMsgCount(String userID)

// 创建单聊会话
Conversation createSingleConversation(String userID, String friendUserID)

// 创建群聊会话
Conversation createGroupConversation(String userID, String groupID)

// 删除会话
Boolean deleteConversation(String userID, String conversationID)

// 设置会话草稿
Boolean setConversationDraft(String userID, String conversationID, String draftText)

// 重置会话群@类型
Boolean resetConversationGroupAtType(String userID, String conversationID)

// 获取会话ID列表
List<String> getConversationIDs(String userID)

// 设置会话最大序列号
Boolean setConversationMaxSeq(String userID, String conversationID, Long maxSeq)

// 获取会话最大序列号
Long getConversationMaxSeq(String userID, String conversationID)

// 更新会话信息
Boolean updateConversation(String userID, String conversationID, String latestMsg, Long latestMsgSendTime)
```

### REST API
```bash
# 获取用户所有会话
POST /api/conversation/get_all_conversations

# 获取指定会话信息
GET /api/conversation/get_conversation?userID={userID}&conversationID={conversationID}

# 设置会话属性
POST /api/conversation/set_conversation

# 批量设置会话属性
POST /api/conversation/batch_set_conversations

# 标记会话已读
POST /api/conversation/mark_conversation_as_read

# 获取用户未读消息总数
GET /api/conversation/get_total_unread_msg_count?userID={userID}

# 创建单聊会话
POST /api/conversation/create_single_conversation

# 创建群聊会话
POST /api/conversation/create_group_conversation

# 删除会话
POST /api/conversation/delete_conversation

# 设置会话草稿
POST /api/conversation/set_conversation_draft

# 重置会话群@类型
POST /api/conversation/reset_conversation_group_at_type

# 获取会话ID列表
GET /api/conversation/get_conversation_ids?userID={userID}

# 设置会话最大序列号
POST /api/conversation/set_conversation_max_seq

# 获取会话最大序列号
GET /api/conversation/get_conversation_max_seq

# 更新会话信息
POST /api/conversation/update_conversation
```

## 🗄️ 数据模型

### Conversation 实体
```java
public class Conversation {
    private String conversationID;      // 会话ID
    private Integer conversationType;   // 会话类型 (1:单聊 2:群聊 3:通知)
    private String userID;             // 用户ID
    private String groupID;            // 群组ID (群聊时使用)
    private String showName;           // 会话显示名称
    private String faceURL;            // 会话头像URL
    private Integer recvMsgOpt;        // 接收消息选项 (0:正常接收 1:不接收消息 2:接收但不提醒)
    private Integer unreadCount;       // 未读消息数量
    private Integer groupAtType;       // 群@类型 (0:没有@消息 1:@所有人 2:@我)
    private Long latestMsgSendTime;    // 最新消息发送时间
    private String draftText;          // 草稿文本
    private Long draftTextTime;        // 草稿文本时间
    private Boolean isPinned;          // 是否置顶
    private Boolean isPrivateChat;     // 是否为私聊
    private Integer burnDuration;      // 燃烧后阅读
    private Boolean isNotInGroup;      // 是否开启消息免打扰
    private Long updateUnreadCountTime; // 更新未读数时间
    private String attachedInfo;       // 附加信息
    private String ex;                 // 扩展字段
    private Long maxSeq;               // 最大序列号
    private Long minSeq;               // 最小序列号
    private Long createTime;           // 创建时间
    private Boolean isMsgDestruct;     // 是否开启消息免打扰 (兼容字段)
    private Long msgDestructTime;      // 消息销毁时间
    private String latestMsg;          // 最新消息
}
```

### MongoDB集合设计
```javascript
// conversations 集合
{
  "_id": ObjectId,
  "conversationID": "single_user001_user002",
  "conversationType": 1,
  "userID": "user001",
  "groupID": null,
  "showName": "用户002",
  "faceURL": "https://example.com/avatar.jpg",
  "recvMsgOpt": 0,
  "unreadCount": 5,
  "groupAtType": 0,
  "latestMsgSendTime": 1640995200000,
  "draftText": "这是一条草稿消息",
  "draftTextTime": 1640995200000,
  "isPinned": false,
  "isPrivateChat": false,
  "burnDuration": 0,
  "isNotInGroup": false,
  "updateUnreadCountTime": 1640995200000,
  "attachedInfo": "",
  "ex": "",
  "maxSeq": 100,
  "minSeq": 1,
  "createTime": 1640995200000,
  "updateTime": 1640995200000,
  "isMsgDestruct": false,
  "msgDestructTime": 0,
  "latestMsg": "你好，这是最新消息"
}
```

### 索引设计
- **复合索引**: userID + conversationID (唯一索引)
- **复合索引**: userID + conversationType
- **复合索引**: userID + isPinned + latestMsgSendTime (置顶和时间排序)
- **复合索引**: userID + latestMsgSendTime (时间排序)
- **单字段索引**: conversationID
- **单字段索引**: userID

## ⚙️ 配置说明

### application.yml
```yaml
server:
  port: 8082

spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: cheese_im
    redis:
      host: localhost
      port: 6379
      database: 2

dubbo:
  application:
    name: cheese-im-conversation
  registry:
    address: nacos://localhost:8848
  protocol:
    port: 20882

cheese:
  im:
    conversation:
      cache-expire-seconds: 3600
      max-conversations-per-user: 1000
      max-batch-size: 100
```

## 🚀 快速开始

### 1. 启动依赖服务
```bash
# 启动MongoDB
docker run -d --name mongo -p 27017:27017 mongo:5.0

# 启动Redis
docker run -d --name redis -p 6379:6379 redis:6.0

# 启动Nacos
docker run -d --name nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:v2.3.2
```

### 2. 编译和运行
```bash
# 编译项目
./gradlew :business:conversation:build

# 运行服务
./gradlew :business:conversation:bootRun
```

### 3. 测试接口
```bash
# 创建单聊会话
curl -X POST "http://localhost:8082/api/conversation/create_single_conversation?userID=user001&friendUserID=user002"

# 获取用户所有会话
curl -X POST "http://localhost:8082/api/conversation/get_all_conversations" \
  -H "Content-Type: application/json" \
  -d '{"userID":"user001","operationID":"test_op"}'

# 标记会话已读
curl -X POST "http://localhost:8082/api/conversation/mark_conversation_as_read?userID=user001&conversationID=single_user001_user002&msgSeq=10"
```

## 🧪 测试

```bash
# 运行单元测试
./gradlew :business:conversation:test

# 运行集成测试
./gradlew :business:conversation:integrationTest
```

## 📊 监控

服务提供以下监控端点：
- **健康检查**: http://localhost:8082/actuator/health
- **指标监控**: http://localhost:8082/actuator/metrics
- **Prometheus**: http://localhost:8082/actuator/prometheus

## 🔧 故障排查

### 常见问题
1. **MongoDB连接失败**: 检查MongoDB服务是否启动，连接配置是否正确
2. **Dubbo注册失败**: 检查Nacos服务是否启动，注册中心配置是否正确
3. **会话创建失败**: 检查用户ID和会话ID是否有效，数据库权限是否正确

### 日志查看
```bash
# 查看应用日志
tail -f logs/conversation.log

# 查看错误日志
grep ERROR logs/conversation.log
```

## 📝 开发指南

### 添加新功能
1. 在`ConversationService`接口中添加新方法
2. 在`ConversationServiceImpl`中实现新方法
3. 在`ConversationController`中添加REST接口
4. 编写单元测试
5. 更新文档

### 数据库迁移
1. 修改`ConversationMongo`实体类
2. 更新索引定义
3. 编写数据迁移脚本
4. 测试迁移过程

## 🤝 贡献

欢迎提交Issue和Pull Request来改进这个项目！
