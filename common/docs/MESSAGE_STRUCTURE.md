# CheeseIM Message 结构与 OpenIM 对应关系

本文档说明 CheeseIM 的 Message 实体类与 OpenIM MsgData 结构的对应关系。

## 📋 核心字段对应

### 基础消息字段
| CheeseIM 字段 | OpenIM 字段 | 类型 | 说明 |
|--------------|-------------|------|------|
| clientMsgID | ClientMsgID | String | 客户端消息ID |
| serverMsgID | ServerMsgID | String | 服务端消息ID |
| sendID | SendID | String | 发送者ID |
| recvID | RecvID | String | 接收者ID |
| groupID | GroupID | String | 群组ID |
| content | Content | String | 消息内容 |
| contentType | ContentType | Integer | 消息内容类型 |
| sessionType | SessionType | Integer | 会话类型 |
| sendTime | SendTime | Long | 发送时间 |
| createTime | CreateTime | Long | 创建时间 |
| status | Status | Integer | 消息状态 |
| seq | Seq | Long | 消息序列号 |
| isRead | IsRead | Boolean | 是否已读 |
| platformID | PlatformID | Integer | 平台ID |
| ex | Ex | String | 扩展字段 |

### 发送者和接收者信息
| CheeseIM 字段 | OpenIM 字段 | 类型 | 说明 |
|--------------|-------------|------|------|
| senderNickname | SenderNickname | String | 发送者昵称 |
| senderFaceURL | SenderFaceURL | String | 发送者头像 |
| recvNickname | RecvNickname | String | 接收者昵称 |
| recvFaceURL | RecvFaceURL | String | 接收者头像 |

### 消息配置和扩展
| CheeseIM 字段 | OpenIM 字段 | 类型 | 说明 |
|--------------|-------------|------|------|
| options | Options | MessageOptions | 消息选项配置 |
| attachedInfo | AttachedInfo | String | 附加信息 |
| offlinePushInfo | OfflinePushInfo | OfflinePushInfo | 离线推送信息 |
| uniqueID | UniqueID | String | 消息唯一序列号 |
| senderPlatformID | SenderPlatformID | Integer | 发送者平台ID |
| recvPlatformID | RecvPlatformID | Integer | 接收者平台ID |
| msgFrom | MsgFrom | Integer | 消息来源 |
| subType | SubType | Integer | 消息子类型 |

## 🔧 MessageOptions 字段对应

| CheeseIM 字段 | OpenIM 字段 | 类型 | 说明 |
|--------------|-------------|------|------|
| history | History | Boolean | 是否存储历史消息 |
| offlinePush | OfflinePush | Boolean | 是否需要离线推送 |
| senderSync | SenderSync | Boolean | 是否发送者同步 |
| unreadCount | UnreadCount | Boolean | 是否计入未读数 |
| persistent | Persistent | Boolean | 是否持久化消息 |
| onlineOnly | OnlineOnly | Boolean | 是否仅在线推送 |

## 📱 OfflinePushInfo 字段对应

| CheeseIM 字段 | OpenIM 字段 | 类型 | 说明 |
|--------------|-------------|------|------|
| title | Title | String | 推送标题 |
| desc | Desc | String | 推送内容描述 |
| ex | Ex | String | 扩展信息 |
| iOSPushSound | IOSPushSound | String | iOS推送声音 |
| iOSBadgeCount | IOSBadgeCount | Boolean | iOS角标设置 |
| signalInfo | SignalInfo | String | 信令推送信息 |
| pushExtras | PushExtras | Map | 推送扩展数据 |

## 🚫 已移除的字段

以下字段在 OpenIM 的 MsgData 中不存在，已从 CheeseIM 的 Message 类中移除：

- `isRevoked` - 是否已撤回
- `revokeTime` - 撤回时间  
- `revokerID` - 撤回者ID
- `revokerNickname` - 撤回者昵称

**说明**: 在 OpenIM 中，撤回消息是通过发送一条 `ContentType = Revoke` 的特殊消息来实现的，而不是在原消息上添加撤回标记。

## 📝 消息类型常量

### ContentType 消息内容类型
```java
// 基础消息类型
TEXT = 101          // 文本消息
IMAGE = 102         // 图片消息
VOICE = 103         // 语音消息
VIDEO = 104         // 视频消息
FILE = 105          // 文件消息
LOCATION = 106      // 位置消息
CARD = 107          // 名片消息
EMOJI = 108         // 表情消息
RED_PACKET = 109    // 红包消息
TRANSFER = 110      // 转账消息

// 系统消息类型
SYSTEM_NOTIFICATION = 1001  // 系统通知
FRIEND_REQUEST = 1002       // 好友申请
GROUP_INVITE = 1003         // 群邀请
GROUP_ANNOUNCEMENT = 1004   // 群公告
GROUP_MEMBER_CHANGE = 1005  // 群成员变更

// 控制消息类型
REVOKE = 2101              // 撤回消息
READ_RECEIPT = 2102        // 已读回执
TYPING = 2103              // 正在输入
```

### SessionType 会话类型
```java
SINGLE = 1        // 单聊
GROUP = 2         // 群聊
SYSTEM = 3        // 系统通知
SUPER_GROUP = 4   // 超级群
```

### Platform 平台类型
```java
IOS = 1           // iOS
ANDROID = 2       // Android
WEB = 3           // Web
WINDOWS = 4       // Windows
MAC = 5           // Mac
```

## 🔄 使用示例

### 创建推送消息
```java
// 从 OpenIM 消息创建推送消息
PushMessage pushMessage = PushMessageBuilder.create(openimMessage)
    .userID("user123")
    .foriOS()
    .build();

// 生成推送内容
String title = PushContentGenerator.generateTitle(openimMessage, groupName);
String content = PushContentGenerator.generateContent(openimMessage);

// 检查是否需要推送
boolean shouldPush = PushContentGenerator.shouldPush(openimMessage);
```

### 消息选项检查
```java
// 检查消息选项
if (message.getOptions() != null) {
    // 检查是否仅在线推送
    if (message.getOptions().getOnlineOnly()) {
        // 不进行离线推送
        return;
    }
    
    // 检查是否禁用离线推送
    if (!message.getOptions().getOfflinePush()) {
        // 不进行离线推送
        return;
    }
}
```

## ✅ 兼容性说明

当前的 Message 类结构完全兼容 OpenIM 的 MsgData 结构，支持：

- ✅ 所有基础消息字段
- ✅ 发送者和接收者信息
- ✅ 消息选项配置
- ✅ 离线推送信息
- ✅ 扩展数据支持
- ✅ 撤回消息处理（通过消息类型）
- ✅ 多平台支持
- ✅ 系统消息支持

这确保了 CheeseIM 的推送服务能够完美处理来自 OpenIM 的各种消息类型和配置。
