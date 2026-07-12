# TCP / WebSocket Protocol

CheeseIM 的 TCP 与 WebSocket 长连接协议统一使用 Protobuf envelope。协议事实以 `server/common-api/src/main/proto/message_protocol.proto` 为准；本文只描述当前实现的交互约束。

## 连接入口

| 协议 | 默认端口 | 说明 |
| --- | --- | --- |
| TCP | `5148` | 原始 TCP 连接，使用长度帧承载 Protobuf envelope。 |
| WebSocket | `5147` | 默认 path `/ws`，二进制消息承载同一套 Protobuf envelope。 |

## 认证流程

客户端必须先通过 HTTP 登录并申请长连接 ticket：

1. `POST /api/auth/login` 获取 access token。
2. `POST /api/im/ws-ticket` 使用 access token 获取 ticket。
3. 建立 TCP/WS 连接。
4. 发送 `ProtoClientEnvelope.auth.ticket`。
5. 服务端返回 `ProtoServerEnvelope.auth`。

连接认证成功后，`postoffice` 会将连接绑定到 user/device/platform，并写入在线路由。

## Envelope

客户端上行使用 `ProtoClientEnvelope`：

| oneof | 说明 |
| --- | --- |
| `auth` | 长连接认证请求，包含 HTTP 签发的 ticket。 |
| `heartbeat` | 心跳。 |
| `chat_message` | 客户端发送的 `ProtoMessage`。 |
| `chat_read` | 已读高水位控制命令。**当前仅声明协议，服务端尚未启用。** |
| `chat_revoke` | 消息撤回控制命令。**当前仅声明协议，服务端尚未启用。** |

服务端下行使用 `ProtoServerEnvelope`：

| oneof | 说明 |
| --- | --- |
| `connect` | 建连响应。 |
| `auth` | 认证结果。 |
| `heartbeat` | 心跳响应。 |
| `chat_send_ack` | 客户端发送消息后的 ACK。 |
| `chat_message` | 服务端投递给客户端的消息。 |
| `error` | 协议或业务错误。 |
| `chat_read_notify` | 已读高水位变更通知。当前由后续已读链路启用。 |
| `chat_revoke_notify` | 撤回 mutation 通知。当前由后续撤回链路启用。 |
| `force_logout` | 强制下线通知，含 reason/session/device/occurred_at。 |

## Message

`ProtoMessage` 是长连接与历史同步共享的消息结构，核心字段包括：

| 字段 | 说明 |
| --- | --- |
| `client_msg_id` | 客户端生成的幂等 ID。 |
| `server_msg_id` | 服务端生成的消息 ID。 |
| `conversation_id` | 会话 ID。 |
| `sender_id` / `receiver_id` | 发送方与接收方。 |
| `conversation_type` | 会话类型，例如单聊、群聊。 |
| `content_type` | 内容类型。 |
| `seq` | 会话内消息序列，由服务端分配。 |
| `send_time` / `create_time` | 发送与创建时间。 |
| `options` | 消息选项，例如是否计未读、是否持久化、是否离线推送。 |
| `offline_push_info` | 离线推送展示信息。 |
| `content` | 业务内容 bytes。 |

## 约束

- 客户端不能自增或信任本地 seq，最终 seq 由 `postmaster` 分配。
- 客户端发送消息后应以 `chat_send_ack` 判断服务端接入结果。
- 历史同步通过 HTTP 会话同步接口按 seq range 拉取，不通过长连接补偿全部历史。
- `chat_read` / `chat_revoke` 已具备类型化 payload，但在 `ReadStateService` / `MessageMutationService` 实现前仍会被网关拒绝；客户端不可将其视为已上线能力。
- 已废弃 JSON 命令体和旧回执枚举等历史协议概念。
