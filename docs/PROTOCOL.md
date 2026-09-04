# CheeseIM 协议总览

> 状态：权威。协议字段的唯一事实源是 `server/common-api/src/main/proto/message_protocol.proto`。

## 传输边界

- TCP 与 WebSocket 使用 `ProtoClientEnvelope` / `ProtoServerEnvelope`；WebSocket 只接受 Binary Frame。
- HTTP 控制面保持 JSON 编码和既有 URL，HTTP Request/Response 只存在于 api-server Controller 层。
- conversation、friend、group 的跨语言字段语义由同一 proto 中的 `ProtoConversation*`、`ProtoFriend*`、`ProtoGroupSummary` 定义。JSON 使用 Protobuf 默认 lowerCamelCase 字段名。
- Redis/Kafka/Chronicle 的内部消息不是客户端控制协议。

## 实时命令

| 能力 | 客户端 payload | 服务端 payload |
| --- | --- | --- |
| 发消息 | `ProtoMessage` | `ProtoChatSendAck` / `ProtoMessage` |
| 设备送达 | `ProtoChatDeliveryAckCommand` | `ProtoChatDeliveryNotify` |
| 已读 | `ProtoChatReadCommand` | `ProtoChatReadNotify` |
| 撤回 | `ProtoChatRevokeCommand` | `ProtoChatRevokeNotify` |
| 输入中 | `ProtoChatTypingCommand` | `ProtoChatTypingNotify` |
| 强制下线 | — | `ProtoForceLogoutNotify` |

设备送达 ACK 只能在消息成功写入客户端持久化存储后发送。客户端必须持久化待确认的 `operationId + conversationId + maxDeliveredSeq`，以同一 operationId 重试，并在收到对应服务端响应后删除。

## 控制面模型

- 好友：`ProtoFriend`、`ProtoFriendRequest`、`ProtoSendFriendRequestCommand`、`ProtoHandleFriendRequestCommand`
- 群摘要：`ProtoGroupSummary`
- 会话：`ProtoConversation`、`ProtoConversationSyncCursor`、`ProtoConversationSyncResult`、`ProtoConversationCommand`

这些类型约束跨语言字段名称、类型和可选性，但不允许下层 Service 依赖 HTTP DTO。协议演进必须保持字段号，删除字段时使用 `reserved`，修改后同时生成 Java 与 Go 代码。
