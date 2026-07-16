# common-api/ARCH.md — 跨模块契约事实快照

> 服务端所有模块的共享契约层。任何字段变更需评估对 8 个消费模块的影响。
> 详细评估见 `server/docs/architecture/ASSESSMENT.md`。

## 1. 子包

| 包 | 内容 | 是否可变 |
| --- | --- | --- |
| `business/domain/` | 领域 POJO：User/Friendship/FriendRequest/Blacklist/Group/GroupMember/GroupRequest/Conversation 系列，以及 `ConversationControlEvent` | 频繁 |
| `dto/message/` | `Message` + `MessageOptions` + `OfflinePushInfo`（1:1 映射 `ProtoMessage`） | 与 proto 同步 |
| `dto/dispatch/` | `DispatchPayload`（聊天消息或 typed `ServerEnvelope` 控制通知，含统一 deliveryId） | 稳定 |
| `event/` | `DeliveryEvent` / `OfflinePushEvent` / `HistoryEvent` / `ConversationSettingsEvent` / `UserSettingsEvent` / `FriendRelationEvent` | 稳定 |
| `enums/` | `CommandType` `ChatType` `ContentType` `MessageStatus` `MessageSource` `PlatformType` `ConversationKind` `ConversationAction` `ConversationVersionOperation` `ReceiveOption` `DeliveryState` `SessionStatus` `ConnectionState` `GroupStatusEnum` `GroupTypeEnum` `GroupMemberRoleEnum` `GroupAtTypeEnum` `NeedVerificationEnum` `HandleResultEnum` `MessagePreviewType` `TypingActionEnum` `ControlEventTypeEnum` `ControlEventDeliveryStateEnum` `ErrorCode` | 稳定但会扩 |
| `protocol/proto/` | `protoc` 生成的 Java 代码，**不要手改** | 不可手改 |
| `proto/` | `message_protocol.proto` 源文件 | 改需评估 + 重生成 |

## 2. Protobuf 协议（权威）

- 文件：`src/main/proto/message_protocol.proto`，包 `cheeseim.protocol`。
- 两个顶层 envelope：`ProtoClientEnvelope`（C→S）、`ProtoServerEnvelope`（S→C），均用 `oneof payload`。
- `CHAT_READ(33)` / `CHAT_REVOKE(34)` / `FORCE_LOGOUT(35)` / `CHAT_TYPING(36)` / `CHAT_DELIVERY(37)` 已有类型化 payload。`CHAT_DELIVERY` 使用 `(userId, deviceId, conversationId) -> deliveredSeq` 高水位批量确认，禁止逐消息回执；网关 channel write 不代表客户端送达。
- 控制面（conversation sync / friend / group）当前**只走 Java Dubbo POJO**，未在 proto 中表达，多语言客户端需自行映射。

## 3. ConversationId 规范（强约束）

`ConversationIdUtil` 是 id 形态唯一权威：

| 类型 | id 形态 | queue key |
| --- | --- | --- |
| 单聊 PRIVATE | `s:{min(uidA,uidB)}:{max(uidA,uidB)}` | 同上 |
| 群聊 GROUP | `g:{groupId}` | `groupId` |
| 通知 NOTIFICATION | `n:{recvUserId}` | 同上 |
| 通知-群 | `ng:{groupId}` | `groupId` |

群会话唯一使用 `g:{groupId}`；禁止引入其它群会话前缀。

## 4. 领域对象不变量

- 领域类全部 `Serializable` + Lombok `@Data`，**禁止 import `org.springframework.data.*`**（根 AGENTS 第 3 条）。
- `Message.seq` 是 server-filled，客户端发送时留空。
- `MessageOptions` 八位 `Boolean` 与 `ProtoMessageOptions` 1:1。
- 枚举新值一律加在末尾，受 Protobuf 兼容性约束。

## 5. 事件载荷不变量

- `DeliveryEvent.targetUserIds` 空表示"群读扩散拉取"（待 `GroupFanoutPlanner` 接通）。
- `OfflinePushEvent.sessionType` / `contentType` 暂用 `Integer`，是历史遗留，新增请保持一致。
- `HistoryEvent.lastMaxSeq` 用于 sync 增量；`beginSeq`/`endSeq` 标识块范围。
- `ReadStateService` 是所有已读入口的唯一共享契约；`ConversationSyncService.ackReadSeq` 暂保留兼容，新增入口禁止绕过前者复制推进逻辑。
- `DeliveryStateService` 是设备送达 ACK 唯一共享契约；Redis Lua 单调推进热水位，Mongo write-behind 只保存聚合后的设备会话高水位。
- `ConversationPermissionService` 是会话访问权限的共享契约；接口名不暴露 Dubbo/RPC 基础设施，provider 和 consumer 均使用该名称。
- `MessageMutationService` 是撤回入口唯一共享契约；撤回不改写 `message_block`，历史读取必须 merge `message_mutation`。
- `ConversationControlEvent` 是已读、撤回的可靠控制事件载荷；业务侧只 append，`common-core` 负责 Mongo outbox 的 cursor、claim 与交付状态，客户端可按 cursor 补齐。输入中不属于可靠事件，只使用 Redis 短 TTL 状态和在线尽力通知。

## 6. 改动评估 checklist

- [ ] 改 proto 后 `./gradlew :common-api:generateProto`？
- [ ] 改领域对象字段名后所有 `Converter` / Mapper 同步？
- [ ] 持久化/wire 枚举新增值后 `fromCode` 分支覆盖 + protos int32 兼容？纯展示/本地枚举是否仍未被误用为 code？
- [ ] 改事件结构后 postmaster/postman 消费者对齐？
