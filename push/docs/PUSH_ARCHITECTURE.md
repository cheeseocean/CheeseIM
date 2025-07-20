# CheeseIM 推送架构与 OpenIM 对应关系

本文档说明 CheeseIM 推送服务的架构设计如何对应 OpenIM Server 的 `push_handler.go` 和 `onlinepusher.go` 实现。

## 📋 OpenIM SessionType 理解

### OpenIM 的 SessionType 定义：

```go
const (
    SingleChatType      = 1  // 单聊
    WriteGroupChatType  = 2  // 写群聊（发送群消息）
    ReadGroupChatType   = 3  // 读群聊（接收群消息，用于推送控制）
    NotificationChatType = 4  // 通知类型
)
```

### OpenIM 推送流程：

1. **根据 sessionType 判断**：不同的 sessionType 有不同的推送策略
2. **Push2Group 中根据 contentType 特殊处理**：在群聊推送中根据消息内容类型做特殊处理
3. **在线推送优先**：先进行 onlinePush（通过 RPC 调用）
4. **成功跳过机制**：当 onlinePush 成功则直接跳过该用户的离线推送
5. **离线推送降级**：最后对失败的用户进行 offlinePush

## 🏗️ CheeseIM 对应实现

### 1. PushMessageListener - 对应 push_handler.go

```java
/**
 * 推送消息监听器 - 对应OpenIM的push_handler.go
 * 实现Push2User方法的核心逻辑
 */
@Component
public class PushMessageListener {
    
    /**
     * Push2User方法 - 参照OpenIM Server的push_handler.go实现
     * 先判断群消息类型，然后默认类型，先进行onlinePush，判断是否需要offlinePush
     * 当onlinePush成功则跳过，最后进行offlinePush
     */
    private void push2Users(Message message, List<String> targetUsers) {
        // 1. 判断消息是否需要推送
        if (!shouldPushMessage(message)) return;
        
        // 2. 判断群消息类型和默认类型
        PushStrategy pushStrategy = determinePushStrategy(message);
        
        // 3. 先进行在线推送（onlinePush）
        OnlinePushResult onlinePushResult = performOnlinePush(message, targetUsers, pushStrategy);
        
        // 4. 判断是否需要离线推送
        List<String> needOfflinePushUsers = determineOfflinePushUsers(onlinePushResult, targetUsers, pushStrategy);
        
        // 5. 如果在线推送完全成功，则跳过离线推送
        if (needOfflinePushUsers.isEmpty()) {
            logger.info("在线推送完全成功，跳过离线推送");
            return;
        }
        
        // 6. 进行离线推送（offlinePush）
        performOfflinePush(message, needOfflinePushUsers, pushStrategy);
    }
}
```

### 2. OnlinePushService - 对应 onlinepusher.go

```java
/**
 * 在线推送服务 - 对应OpenIM的onlinepusher.go
 * 通过Dubbo RPC调用postoffice进行在线推送
 */
@Service
public class OnlinePushServiceImpl implements OnlinePushService {
    
    @Override
    public OnlinePushResult pushMessageToUsers(Message message, List<String> targetUsers) {
        // 通过Dubbo调用postoffice服务进行在线推送
        // 返回在线推送结果，包含成功用户、失败用户、离线用户
    }
}
```

### 3. OfflinePushListener - 对应离线推送处理

```java
/**
 * 离线推送监听器 - 处理离线推送逻辑
 * 对应OpenIM的离线推送处理部分
 */
@Component
public class OfflinePushListener {
    
    @KafkaListener(topics = KafkaTopics.TO_OFFLINE_PUSH_TOPIC)
    public void handleOfflinePush(String messageJson) {
        // 处理离线推送，调用各种推送提供商
        // APNs, FCM, JPush, Huawei Push等
    }
}
```

## 🔄 推送流程对比

### OpenIM 流程：
```
Message -> push_handler.go -> Push2User() -> onlinepusher.go -> offlinePush
```

### CheeseIM 流程：
```
Message -> PushMessageListener -> push2Users() -> OnlinePushService -> OfflinePushListener
```

## 📊 详细对应关系

| OpenIM 组件 | CheeseIM 组件 | 功能说明 |
|------------|--------------|----------|
| push_handler.go | PushMessageListener | 推送消息处理入口 |
| Push2User() | push2Users() | 核心推送逻辑方法 |
| onlinepusher.go | OnlinePushService | 在线推送服务 |
| offlinePush | OfflinePushListener | 离线推送处理 |
| 消息类型判断 | determinePushStrategy() | 推送策略确定 |
| 在线推送成功跳过 | determineOfflinePushUsers() | 离线推送用户确定 |

## 🎯 核心逻辑实现

### 1. 根据 sessionType 的推送策略判断

```java
private PushStrategy determinePushStrategy(Message message) {
    PushStrategy strategy = new PushStrategy();
    Integer sessionType = message.getSessionType();
    Integer contentType = message.getContentType();

    // 根据sessionType判断推送策略
    switch (sessionType) {
        case MessageConstants.SessionType.SINGLE_CHAT_TYPE:
            // 单聊消息 - Push2User逻辑
            strategy.setSessionType("single");
            strategy.setNeedOnlinePush(true);
            strategy.setNeedOfflinePush(true);
            break;

        case MessageConstants.SessionType.WRITE_GROUP_CHAT_TYPE:
            // 写群聊消息 - Push2Group逻辑，根据contentType做特殊处理
            strategy.setSessionType("write_group");
            strategy.setNeedOnlinePush(true);
            strategy.setNeedOfflinePush(true);

            // 根据contentType做特殊处理
            strategy = applyGroupContentTypeStrategy(strategy, message);
            break;

        case MessageConstants.SessionType.READ_GROUP_CHAT_TYPE:
            // 读群聊消息 - 特殊的推送控制类型
            strategy.setSessionType("read_group");
            strategy.setNeedOnlinePush(true);
            strategy.setNeedOfflinePush(false); // 默认不推送，由用户配置决定
            break;

        case MessageConstants.SessionType.NOTIFICATION_CHAT_TYPE:
            // 通知消息
            strategy.setSessionType("notification");
            strategy.setNeedOnlinePush(true);
            strategy.setNeedOfflinePush(shouldPushNotification(contentType));
            break;
    }

    return strategy;
}
```

### 2. Push2Group 中根据 contentType 的特殊处理

```java
private PushStrategy applyGroupContentTypeStrategy(PushStrategy strategy, Message message) {
    Integer contentType = message.getContentType();

    switch (contentType) {
        // 系统通知类消息 - 通常需要推送
        case MessageConstants.ContentType.SYSTEM_NOTIFICATION:
        case MessageConstants.ContentType.GROUP_ANNOUNCEMENT:
        case MessageConstants.ContentType.GROUP_MEMBER_CHANGE:
            strategy.setNeedOfflinePush(true);
            break;

        // 控制消息 - 不需要离线推送
        case MessageConstants.ContentType.REVOKE:
        case MessageConstants.ContentType.READ_RECEIPT:
        case MessageConstants.ContentType.TYPING:
            strategy.setNeedOfflinePush(false);
            break;

        // 特殊消息 - 高优先级推送
        case MessageConstants.ContentType.RED_PACKET:
        case MessageConstants.ContentType.TRANSFER:
            strategy.setNeedOfflinePush(true);
            strategy.setHighPriority(true);
            break;

        // 普通消息类型 - 根据用户配置决定
        default:
            strategy.setNeedOfflinePush(true);
            break;
    }

    return strategy;
}
```

### 3. 用户级别的推送控制

```java
private boolean shouldOfflinePushForGroupUser(Message message, String userID) {
    // 检查用户是否启用了群组推送
    if (!pushConfigService.isGroupPushEnabled(userID, message.getGroupID())) {
        return false;
    }

    // 检查用户是否启用了离线推送
    if (!pushConfigService.isOfflinePushEnabled(userID)) {
        return false;
    }

    // 获取用户的群聊读取类型
    PushConfigService.GroupChatReadType readType = determineGroupChatReadTypeForUser(message, userID);

    switch (readType) {
        case READ_ALL:
            return true; // 读取所有群消息
        case READ_MENTION_ONLY:
            return isAtMessage(message); // 只读取@消息
        case READ_NONE:
            return false; // 不读取群消息
        default:
            return true;
    }
}
```

### 3. 在线推送成功跳过逻辑

```java
private List<String> determineOfflinePushUsers(Message message, OnlinePushResult onlinePushResult,
                                              List<String> targetUsers, PushStrategy strategy) {
    List<String> needOfflinePushUsers = new ArrayList<>();

    if (!strategy.isNeedOfflinePush()) {
        return needOfflinePushUsers; // 策略设置无需离线推送
    }

    // 获取在线推送失败和离线的用户
    List<String> candidateUsers = new ArrayList<>();
    if (onlinePushResult.getFailedUsers() != null) {
        candidateUsers.addAll(onlinePushResult.getFailedUsers());
    }
    if (onlinePushResult.getOfflineUsers() != null) {
        candidateUsers.addAll(onlinePushResult.getOfflineUsers());
    }

    // 对于群消息，需要根据每个用户的ReadGroupChatType单独判断
    if (message.getSessionType() == MessageConstants.SessionType.GROUP ||
        message.getSessionType() == MessageConstants.SessionType.SUPER_GROUP) {

        for (String userID : candidateUsers) {
            if (shouldOfflinePushForGroupUser(message, userID)) {
                needOfflinePushUsers.add(userID);
            }
        }
    } else {
        // 非群消息，直接添加所有候选用户
        needOfflinePushUsers.addAll(candidateUsers);
    }

    // 在线推送成功的用户会被自动跳过（不在candidateUsers中）
    return needOfflinePushUsers;
}
```

### 3. 推送优先级处理

```java
// 参照OpenIM的消息优先级处理
public static int getPushPriority(Message message) {
    // 系统消息高优先级
    if (MessageConstants.isSystemMessage(message.getContentType())) {
        return MessageConstants.PushPriority.HIGH;
    }
    
    // 特殊消息类型
    switch (message.getContentType()) {
        case MessageConstants.ContentType.RED_PACKET: // 红包
        case MessageConstants.ContentType.TRANSFER:   // 转账
            return MessageConstants.PushPriority.HIGH;
        default:
            return MessageConstants.PushPriority.NORMAL;
    }
}
```

## 🚀 Kafka Topic 流转

```
1. postman -> TO_PUSH_TOPIC -> PushMessageListener.push2Users()
2. PushMessageListener -> TO_OFFLINE_PUSH_TOPIC -> OfflinePushListener
3. OfflinePushListener -> 各种推送提供商 (APNs, FCM, JPush等)
```

## 📱 ReadGroupChatType 功能

### GroupChatReadType 枚举值

```java
public enum GroupChatReadType {
    READ_ALL(0),         // 读取所有群消息 - 所有群消息都会推送
    READ_MENTION_ONLY(1), // 只读取@消息 - 只有@消息才会推送
    READ_NONE(2)         // 不读取群消息 - 群消息不会推送
}
```

### 配置层级（优先级从高到低）

1. **用户+群组特定配置**：`getUserGroupChatReadType(userID, groupID)`
2. **用户全局配置**：`getUserGlobalGroupChatReadType(userID)`
3. **系统默认配置**：`getDefaultGroupChatReadType()`

### REST API 接口

```bash
# 获取用户群聊读取类型
GET /api/v1/push/config/group-chat-read-type?userID=user123&groupID=group456

# 设置用户群聊读取类型
POST /api/v1/push/config/group-chat-read-type?userID=user123&groupID=group456&readType=READ_MENTION_ONLY

# 获取用户完整推送配置
GET /api/v1/push/config/user/user123
```

## ✅ 完全对应的特性

1. **✅ 消息类型判断**：先判断群消息类型，然后默认类型
2. **✅ ReadGroupChatType 支持**：根据用户配置判断群消息推送策略
3. **✅ 用户级别配置**：每个用户可以单独配置群聊读取类型
4. **✅ @消息识别**：支持只推送@消息的策略
5. **✅ 在线推送优先**：先进行 onlinePush
6. **✅ 成功跳过机制**：当 onlinePush 成功则跳过该用户的离线推送
7. **✅ 离线推送降级**：最后对失败用户进行 offlinePush
8. **✅ 推送策略控制**：支持消息选项控制推送行为
9. **✅ 统计和监控**：完整的推送统计和监控体系

## 📝 使用示例

```java
// 1. 消息进入推送系统
Message message = new Message();
message.setSessionType(MessageConstants.SessionType.GROUP);
message.setContentType(MessageConstants.ContentType.TEXT);
message.setContent("Hello Group!");

List<String> targetUsers = Arrays.asList("user1", "user2", "user3");

// 2. 发送到推送Topic
PushMessageData pushData = new PushMessageData();
pushData.setMessage(message);
pushData.setTargetUsers(targetUsers);

kafkaTemplate.send(KafkaTopics.TO_PUSH_TOPIC, objectMapper.writeValueAsString(pushData));

// 3. PushMessageListener自动处理
// - 判断消息类型（群消息）
// - 执行在线推送
// - 在线推送成功的用户跳过离线推送
// - 失败和离线用户进行离线推送
```

这样，CheeseIM 的推送架构完全对应了 OpenIM Server 的 `push_handler.go` 和 `onlinepusher.go` 的核心逻辑！
