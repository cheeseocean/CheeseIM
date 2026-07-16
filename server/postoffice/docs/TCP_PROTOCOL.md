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
| command `HEARTBEAT` | 心跳无 payload，不属于 `ProtoClientEnvelope.oneof`。 |
| `chat_message` | 客户端发送的 `ProtoMessage`。 |
| `chat_read` | 已读高水位控制命令。服务端校验会话成员与 `maxSeq` 后单调推进 readSeq。 |
| `chat_revoke` | 消息撤回控制命令。服务端校验发送者与两分钟窗口后写入 mutation overlay。 |
| `chat_typing` | 输入中瞬时控制命令。仅通知当前在线目标，服务端 TTL 固定在 3-5 秒，不写入普通消息链路。 |
| `chat_delivery_ack` | 设备按会话批量确认 `max_delivered_seq`；必须携带当前连接 device_id 与幂等 op_id。 |

服务端下行使用 `ProtoServerEnvelope`：

| oneof | 说明 |
| --- | --- |
| `connect` | 建连响应。 |
| `auth` | 认证结果。 |
| `heartbeat` | 心跳响应。 |
| `chat_send_ack` | broker 已可靠接收消息的 ACK；`accepted_state=BROKER_ACCEPTED`，不代表历史已持久化或接收设备已送达。 |
| `chat_message` | 服务端投递给客户端的消息。 |
| `error` | 协议或业务错误。 |
| `chat_read_notify` | 已读高水位变更通知。通过 control-event outbox 可靠补偿，客户端可按 cursor 补齐。 |
| `chat_revoke_notify` | 撤回 mutation 通知。通过 control-event outbox 可靠补偿，历史查询仍以 mutation overlay 为准。 |
| `chat_typing_notify` | 输入中通知。短 TTL 瞬时状态，不触发离线推送；到期即失效。 |
| `chat_delivery_notify` | 接收设备送达高水位变化；单聊发送方可据此派生 `seq <= delivered_seq` 已送达。 |
| `force_logout` | 强制下线通知，含 reason/session/device/occurred_at。 |

## Message

`ProtoMessage` 是长连接与历史同步共享的消息结构，核心字段包括：

| 字段 | 说明 |
| --- | --- |
| `client_msg_id` | 客户端生成的幂等 ID。 |
| `server_msg_id` | 服务端生成的消息 ID。 |
| `sender_id` / `receiver_id` | 发送方与接收方。 |
| `group_id` | 群聊群 ID；单聊为空。会话 ID 由 `chat_type` 与发送/接收/群标识派生，不是 `ProtoMessage` 字段。 |
| `chat_type` | 聊天类型，例如单聊、群聊。 |
| `content_type` | 内容类型。 |
| `seq` | 会话内消息序列，由服务端分配。 |
| `send_time` / `create_time` | 发送与创建时间。 |
| `options` | 消息选项，例如是否计未读、是否持久化、是否离线推送。 |
| `offline_push_info` | 离线推送展示信息。 |
| `content` | 业务内容 bytes。 |

## 约束

- 客户端不能自增或信任本地 seq，最终 seq 由 `postmaster` 分配。
- 客户端发送消息后应以 `chat_send_ack.accepted_state` 判断 broker 接入结果；该 ACK 不能冒充设备送达。
- 客户端成功解析并写入本地消息后，按 `(userId, deviceId, conversationId)` 合并最大 seq，再发送 `chat_delivery_ack`；服务端用 Redis Lua 单调推进并批量异步持久化，禁止逐消息 ACK/逐消息 Mongo 写。
- 网关 `Channel.write` 成功仅表示传输层接受写请求，不更新 deliveredSeq；只有客户端显式 `chat_delivery_ack` 才能推进设备送达状态。
- 历史同步通过 HTTP 会话同步接口按 seq range 拉取，不通过长连接补偿全部历史。
- `chat_read` / `chat_revoke` / `chat_typing` 均为已上线的类型化控制命令；客户端必须使用对应 oneof，不得伪装成 `chat_message` 或 JSON 命令体。旧 `ContentType.READ_RECEIPT(2004)` 只保留历史数据解析兼容，上行 `chat_message` 使用该类型会被拒绝，`chat_read` 是唯一已读入口。
- 已读、撤回追加 `conversation_control_event`，离线端可经 HTTP control-events cursor 补齐；输入中仅使用 Redis 短 TTL 状态并尽力通知在线端，不进入可靠补偿。
- TCP 与 WebSocket frame 默认最大 64 KiB；`CHAT_SEND` envelope body 同样默认 64 KiB。文本默认最大 16 KiB，自定义内容 64 KiB，富媒体 metadata 与其他内容 32 KiB，均可通过 `CHEESEIM_POSTOFFICE_*_MAX_FRAME_LENGTH` 和 `CHEESEIM_MESSAGE_MAX_*` 环境变量调整。
- 已废弃 JSON 命令体和旧回执枚举等历史协议概念。
