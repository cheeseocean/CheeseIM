# SessionType 理解修正说明

本文档说明对 OpenIM SessionType 的理解修正和相应的代码调整。

## 🔄 理解修正

### ❌ 之前的错误理解

之前错误地认为：
- `sessionType` 只是简单的会话类型（单聊、群聊、系统）
- `ReadGroupChatType` 是用户配置，不是 `sessionType` 的一个值

### ✅ 正确的理解

根据 OpenIM 的实际定义：

```go
const (
    SingleChatType      = 1  // 单聊
    WriteGroupChatType  = 2  // 写群聊（发送群消息）
    ReadGroupChatType   = 3  // 读群聊（接收群消息，用于推送控制）
    NotificationChatType = 4  // 通知类型
)
```

- `ReadGroupChatType = 3` 是一个特殊的 **sessionType** 值
- 在 `Push2Group` 中根据 `contentType` 再做特殊处理
- 不同的 `sessionType` 有不同的推送策略

## 📋 修正后的实现

### 1. MessageConstants.java 修正

```java
public static class SessionType {
    /** 单聊 */
    public static final int SINGLE_CHAT_TYPE = 1;
    /** 写群聊（发送群消息） */
    public static final int WRITE_GROUP_CHAT_TYPE = 2;
    /** 读群聊（接收群消息，用于推送控制） */
    public static final int READ_GROUP_CHAT_TYPE = 3;
    /** 通知类型 */
    public static final int NOTIFICATION_CHAT_TYPE = 4;
}
```

### 2. PushMessageListener.java 推送策略修正

```java
private PushStrategy determinePushStrategy(Message message) {
    Integer sessionType = message.getSessionType();
    Integer contentType = message.getContentType();
    
    switch (sessionType) {
        case MessageConstants.SessionType.SINGLE_CHAT_TYPE:
            // 单聊消息 - Push2User逻辑
            return createSingleChatStrategy();
            
        case MessageConstants.SessionType.WRITE_GROUP_CHAT_TYPE:
            // 写群聊消息 - Push2Group逻辑，根据contentType做特殊处理
            return createWriteGroupChatStrategy(message);
            
        case MessageConstants.SessionType.READ_GROUP_CHAT_TYPE:
            // 读群聊消息 - 特殊的推送控制类型
            return createReadGroupChatStrategy(message);
            
        case MessageConstants.SessionType.NOTIFICATION_CHAT_TYPE:
            // 通知消息
            return createNotificationChatStrategy(message);
    }
}
```

### 3. 根据 contentType 的特殊处理

```java
private PushStrategy applyGroupContentTypeStrategy(PushStrategy strategy, Message message) {
    Integer contentType = message.getContentType();
    
    switch (contentType) {
        // 系统通知类消息 - 通常需要推送
        case MessageConstants.ContentType.SYSTEM_NOTIFICATION:
        case MessageConstants.ContentType.GROUP_ANNOUNCEMENT:
            strategy.setNeedOfflinePush(true);
            break;
            
        // 控制消息 - 不需要离线推送
        case MessageConstants.ContentType.REVOKE:
        case MessageConstants.ContentType.READ_RECEIPT:
            strategy.setNeedOfflinePush(false);
            break;
            
        // 特殊消息 - 高优先级推送
        case MessageConstants.ContentType.RED_PACKET:
        case MessageConstants.ContentType.TRANSFER:
            strategy.setNeedOfflinePush(true);
            strategy.setHighPriority(true);
            break;
    }
    
    return strategy;
}
```

## 🎯 不同 SessionType 的推送策略

### SingleChatType (1) - 单聊
- **在线推送**: ✅ 需要
- **离线推送**: ✅ 需要
- **特殊处理**: 无

### WriteGroupChatType (2) - 写群聊
- **在线推送**: ✅ 需要
- **离线推送**: ✅ 需要（根据 contentType 调整）
- **特殊处理**: 根据 contentType 做特殊处理
  - 系统消息：强制推送
  - 控制消息：不推送
  - 特殊消息：高优先级推送
  - 普通消息：根据用户配置

### ReadGroupChatType (3) - 读群聊
- **在线推送**: ✅ 需要
- **离线推送**: ❌ 默认不需要
- **特殊处理**: 只有重要系统消息才推送
  - `SYSTEM_NOTIFICATION`
  - `GROUP_ANNOUNCEMENT`
  - `GROUP_MEMBER_CHANGE`

### NotificationChatType (4) - 通知
- **在线推送**: ✅ 需要
- **离线推送**: 根据 contentType 判断
- **特殊处理**: 重要通知推送，状态通知不推送

## 🔧 用户级别的推送控制

### 不同 SessionType 的用户控制

```java
private List<String> determineOfflinePushUsers(Message message, ...) {
    Integer sessionType = message.getSessionType();
    
    switch (sessionType) {
        case SINGLE_CHAT_TYPE:
            // 单聊：直接推送
            return candidateUsers;
            
        case WRITE_GROUP_CHAT_TYPE:
            // 写群聊：根据用户群聊配置
            return filterByGroupChatConfig(candidateUsers, message);
            
        case READ_GROUP_CHAT_TYPE:
            // 读群聊：只推送重要消息
            return filterByReadGroupChatRules(candidateUsers, message);
            
        case NOTIFICATION_CHAT_TYPE:
            // 通知：根据通知配置
            return filterByNotificationConfig(candidateUsers, message);
    }
}
```

## ✅ 修正完成的功能

1. **✅ 正确理解 SessionType**：4种不同的会话类型
2. **✅ ReadGroupChatType 特殊处理**：作为 sessionType=3 的特殊类型
3. **✅ Push2Group contentType 处理**：根据消息内容类型做特殊处理
4. **✅ 用户级别推送控制**：不同 sessionType 的不同控制策略
5. **✅ 高优先级消息支持**：红包、转账等特殊消息
6. **✅ 控制消息过滤**：撤回、已读等消息不推送

## 📝 使用示例

```java
// 单聊消息
Message singleMessage = new Message();
singleMessage.setSessionType(MessageConstants.SessionType.SINGLE_CHAT_TYPE);
singleMessage.setContentType(MessageConstants.ContentType.TEXT);
// -> 正常推送

// 写群聊消息
Message groupMessage = new Message();
groupMessage.setSessionType(MessageConstants.SessionType.WRITE_GROUP_CHAT_TYPE);
groupMessage.setContentType(MessageConstants.ContentType.RED_PACKET);
// -> 高优先级推送

// 读群聊消息
Message readGroupMessage = new Message();
readGroupMessage.setSessionType(MessageConstants.SessionType.READ_GROUP_CHAT_TYPE);
readGroupMessage.setContentType(MessageConstants.ContentType.TEXT);
// -> 只在线推送，不离线推送

// 读群聊系统消息
Message readGroupSystemMessage = new Message();
readGroupSystemMessage.setSessionType(MessageConstants.SessionType.READ_GROUP_CHAT_TYPE);
readGroupSystemMessage.setContentType(MessageConstants.ContentType.GROUP_ANNOUNCEMENT);
// -> 在线+离线推送（重要系统消息）
```

现在 CheeseIM 的推送系统完全正确地理解和实现了 OpenIM 的 SessionType 逻辑！
