# Message Protobuf Design

## Goal

将现有消息协议从 JSON 直接切换为 protobuf，不保留 JSON 回退路径。

## Scope

- 基于当前 `Message` 模型定义 `ProtoMessage`
- 将 `ClientEnvelope` / `ServerEnvelope` 统一切到 protobuf
- TCP 改为 protobuf 二进制帧
- WebSocket 改为 binary frame
- `postoffice` handler 改为消费 protobuf 解码后的模型

## Current Message Model

当前 `Message` 字段如下：

- `clientMsgId`
- `serverMsgId`
- `senderId`
- `receiverId`
- `groupId`
- `content`
- `contentType`
- `sessionType`
- `sendTime`
- `createTime`
- `status`
- `platformCode`
- `attributes`
- `uniqueId`
- `source`
- `options`

其中：

- `content` 为 `byte[]`
- `attributes` 为 `Map<String, String>`
- `source` 为 `MessageSource`
- `options` 为 `MessageOptions`

## Proto Design

`ProtoMessage` 按当前 `Message` 一比一映射：

- `string client_msg_id`
- `string server_msg_id`
- `string sender_id`
- `string receiver_id`
- `string group_id`
- `bytes content`
- `int32 content_type`
- `int32 session_type`
- `int64 send_time`
- `int64 create_time`
- `int32 status`
- `int32 platform_code`
- `map<string, string> attributes`
- `string unique_id`
- `int32 source`
- `ProtoMessageOptions options`

约束：

- 枚举字段先统一使用 `int32 code`
- Java 侧继续通过现有枚举 `fromCode(...)` 转换
- `options` 使用专门的 protobuf message，不再使用泛型 map

## Envelope Design

协议不再保留泛型 `body:Object` 模式，改为显式 `oneof payload`。

建议定义：

- `ProtoClientEnvelope`
- `ProtoServerEnvelope`

公共字段保留：

- `command`
- `request_id`
- `code`
- `message`

消息载荷通过 `oneof payload` 承载，例如：

- `chat_message`
- `auth_payload`
- `heartbeat_payload`

## Service Boundary

protobuf 类型只停留在协议层：

- 网络层：protobuf 编解码
- mapper 层：`ProtoMessage` / `ProtoEnvelope` 与现有 Java DTO 转换
- 业务层：继续使用 `Message`、`SendMessageReq`、`SendMessageResp`

## Migration Order

1. 新增 `.proto` 和代码生成配置
2. 新增 `ProtoMessageMapper` / `ProtoEnvelopeMapper`
3. 改 TCP codec
4. 改 WebSocket binary frame
5. 改 `ChatMessageHandler`
6. 改协议测试与客户端夹具

## Notes

- 本次迁移目标是全量切 protobuf，不保留 JSON fallback
- 不借这次迁移重塑业务模型，只做协议层统一
