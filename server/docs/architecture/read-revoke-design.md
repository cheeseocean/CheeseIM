# 已读与撤回控制事件设计草案

> 状态：第一阶段已实现（2026-07-14）：typed Protobuf、TCP/WS/HTTP 统一核心入口、readSeq 单调推进、撤回 overlay、历史/gap repair 合并、mutation 增量同步及跨节点在线控制通知均已具备。群管理员撤回、隐私开关与群 read count 仍未实现。
> 目标：为 `CHAT_READ` / `CHAT_REVOKE` 协议补全和服务端实现提供统一设计，避免后续把已读、撤回误建模为普通聊天消息。
> 适用范围：CheeseIM 服务端 `postoffice` / `postbox` / `postmaster` / `postman` / `common-api`。

## 1. 设计结论

已读与撤回都属于会话级控制事件，不属于普通聊天消息。

- **已读是 cursor**：维护 `(userId, conversationId) -> readSeq`，默认单聊向对方公开已读状态，群聊第一阶段只清自己的 unread，不做成员已读列表。
- **撤回是 mutation**：写 `message_mutation(REVOKED)` overlay，不物理删除原消息；历史查询和增量同步都必须 merge mutation。
- **入口可以多，核心只能一条**：WS/TCP 与 HTTP 均可作为入口，但必须收敛到同一个 `ReadStateService` / `MessageMutationService`。
- **控制事件不进普通 message history**：它们不作为用户消息占用普通消息 seq；已读仍写 `ConversationVersionLog.READ_STATE_UPDATED` 以刷新会话快照，已读/撤回/输入中的可靠通知和补拉统一由 control-event outbox cursor 承担。

实现补充（2026-07-14）：上述控制面统一追加 `conversation_control_event` outbox；服务端用全局递增 `cursor` 提供客户端补拉，用 claim lease + `markDelivered` 驱动 postman 重试。已读、撤回和输入中均复用该事件流；输入中仍使用短 TTL，过期后不再返回或投递。

参考模型：

- OpenIM：HTTP 已读入口只是 API facade，内部转 msg RPC，更新 `HasReadSeq` 后发 `MarkAsReadTips` 通知。
- Telegram：`messages.readHistory(peer, max_id)` 按 peer 推进已读高水位，并通过 `updateReadHistoryInbox/Outbox` 同步更新；`messages.deleteMessages(revoke=true)` 表达撤回给所有人，并通过 delete update 同步。
- WhatsApp：产品层暴露 sent / delivered / read 三态，单聊默认显示对方已读；群聊 read receipt 语义与单聊不同，不能简单逐人 fanout。

## 2. 已读设计

### 2.1 产品规则

| 场景 | 第一阶段规则 |
| --- | --- |
| 单聊 | 默认像 WhatsApp 一样显示"对方已读" |
| 群聊 | 不做成员已读列表，不向全群广播已读 |
| 多端 | 任一设备读到 `readSeq` 后，同步清理本人其他设备 unread |
| 隐私开关 | 第一阶段不做，默认开启 |
| read count | 第一阶段不做 |

### 2.2 状态模型

建议新增会话已读状态文档：

```text
conversation_read_state
- id = ownerUserId + ":" + conversationId
- ownerUserId
- conversationId
- readSeq
- updatedAt
```

更新语义必须是单调推进：

```text
readSeq = max(currentReadSeq, command.readSeq)
```

实现可以使用 Mongo `$max`，或 Redis Lua + 异步刷 Mongo。第一阶段若沿用现有 `UserConversationSyncPoint`，也必须保留这个单调语义。

### 2.3 协议草案

```proto
message ChatReadCommand {
  string conversation_id = 1;
  int64 read_seq = 2;
  string device_id = 3;
  string op_id = 4;
}

message ChatReadNotify {
  string conversation_id = 1;
  string reader_id = 2;
  int64 read_seq = 3;
  int64 updated_at = 4;
}
```

### 2.4 流转

```text
Client WS/TCP CHAT_READ 或 HTTP mark_read
  -> postoffice / api-server
  -> ReadStateService
  -> 校验 reader 是 conversation 成员
  -> 校验 readSeq <= maxSeq
  -> 单调推进 reader readSeq
  -> 写 ConversationVersionLog: READ_STATE_UPDATED
  -> 发 ChatReadNotify
```

通知目标：

| 会话类型 | 通知目标 |
| --- | --- |
| 单聊 | reader 自己其他端 + peer 在线端 |
| 普通群 | reader 自己其他端；第一阶段不通知全群 |
| 超级群 | reader 自己其他端；不广播 |

发送方展示规则：

```text
if message.senderId == currentUser
and peerReadSeq >= message.seq:
    show READ
```

## 3. 撤回设计

### 3.1 产品规则

| 规则 | 第一阶段决策 |
| --- | --- |
| 撤回范围 | 撤回给所有人 |
| 展示文案 | 所有人看到"某某撤回了一条消息" |
| 默认窗口 | 2 分钟，可配置 |
| 时间依据 | 服务端消息 `sendTime`，不信客户端时间 |
| 权限 | 发送者只能撤回自己的消息 |
| 后续扩展 | 群主/管理员可撤回成员消息，可不受 2 分钟限制 |
| 群聊 | 支持撤回通知；不做群已读成员列表 |

### 3.2 状态模型

建议新增 message mutation overlay：

```text
message_mutation
- id = serverMsgId + ":REVOKED"
- serverMsgId
- conversationId
- mutationType = REVOKED
- operatorUserId
- operatorNicknameSnapshot
- targetSenderId
- targetSenderNicknameSnapshot
- reason
- mutationVersion
- createdAt
```

约束：

- `serverMsgId + mutationType` 唯一，重复撤回同一消息应幂等成功。
- 撤回不物理删除 message block。
- 历史查询返回消息时必须 merge mutation overlay。
- 附件撤回后客户端隐藏，下载 token 应失效或在后续异步清理。

### 3.3 协议草案

```proto
message ChatRevokeCommand {
  string conversation_id = 1;
  string server_msg_id = 2;
  string op_id = 3;
  string reason = 4;
}

message ChatRevokeNotify {
  string conversation_id = 1;
  string server_msg_id = 2;
  string operator_user_id = 3;
  string operator_name = 4;
  string target_sender_id = 5;
  string target_sender_name = 6;
  int64 revoked_at = 7;
  int64 mutation_version = 8;
}
```

### 3.4 流转

```text
Client WS/TCP CHAT_REVOKE 或 HTTP revoke
  -> postoffice / api-server
  -> MessageMutationService
  -> 查 serverMsgId 映射到 conversationId / senderId / seq / sendTime
  -> 校验 conversationId 一致
  -> 校验操作者是会话成员
  -> 校验权限：operator == sender
  -> 校验 now - sendTime <= 2 分钟
  -> upsert message_mutation(REVOKED)
  -> 写入 mutation 增量同步数据
  -> 追加 control-event outbox 并 fanout ChatRevokeNotify
```

通知目标：

| 会话类型 | 通知目标 |
| --- | --- |
| 单聊 | 会话双方在线端 |
| 普通群 | 群成员在线端 |
| 超级群 | 写 mutation，在线活跃订阅者/离线端通过同步收敛 |

客户端展示：

```json
{
  "serverMsgId": "m1",
  "seq": 100,
  "status": "REVOKED",
  "content": null,
  "revokeInfo": {
    "operatorUserId": "u1",
    "operatorName": "张三",
    "targetSenderId": "u1",
    "targetSenderName": "张三",
    "revokedAt": 1720000000000
  }
}
```

展示文案：

| 场景 | 文案 |
| --- | --- |
| 自己撤回 | 你撤回了一条消息 |
| 对方撤回 | 张三撤回了一条消息 |
| 未来管理员撤回 | 管理员张三撤回了李四的一条消息 |

## 4. 与现有模块的落点

| 模块 | 责任 |
| --- | --- |
| `common-api` | 补 `message_protocol.proto` typed payload、领域事件与枚举 |
| `postoffice` | 解析 WS/TCP 控制命令，做连接鉴权与 envelope ack，不承载业务判断 |
| `api-server` | 提供 HTTP fallback/controller，调用同一 service，不复制业务逻辑 |
| `postbox` | 历史查询与 gap repair 合并 mutation overlay；不承载控制命令业务判断 |
| `postmaster` | 负责撤回的消息映射查询、权限/窗口校验、mutation 写入与离线 mutation 同步 |
| `business` | 负责已读 cursor、readSeq write-behind 和 `READ_STATE_UPDATED` 版本日志 |
| `postman` | 投递 `ChatReadNotify` / `ChatRevokeNotify` / `ChatTypingNotify` 到在线端；outbox claim lease 补偿不触发离线厂商推送 |
| `business` | 会话同步接口返回 read/revoke 增量或 merge 后的会话状态 |
| SDK / CheeseBox | 展示已读状态、撤回 tombstone，并处理乱序 notify |

## 5. 百万级约束

| 风险 | 约束 |
| --- | --- |
| 已读 fanout 放大 | 单聊通知 peer；群聊第一阶段不广播已读 |
| 逐条已读存储膨胀 | 默认只存 readSeq 高水位 |
| 撤回与消息送达乱序 | notify 带 `serverMsgId` + `mutationVersion`；客户端先缓存 mutation，消息后到也要套用 |
| 大群撤回扩散压力 | 普通群实时 fanout；超级群以 mutation + sync 收敛为主 |
| 重复提交 | read 用 `$max` 幂等；revoke 用唯一键幂等 |
| 客户端时间不可信 | 撤回窗口只用服务端 sendTime 判断 |
| 历史不一致 | 所有历史查询必须 merge `message_mutation` |

## 6. 推荐实现顺序

1. 补 `CHAT_READ` / `CHAT_REVOKE` typed payload 与 notify payload。
2. 新增 `ReadStateService`，支持单聊 readSeq 推进和 notify。
3. 新增 `MessageMutationService`，支持 2 分钟内发送者撤回自己的消息。
4. 新增 `message_mutation` 持久化与唯一键。
5. 历史查询 merge mutation overlay，返回 tombstone。
6. conversation sync 带出 `READ_STATE_UPDATED` / `MESSAGE_REVOKED` 增量。
7. Go SDK / CheeseBox 接入展示与乱序 tombstone 缓存。
8. 再评估群管理员撤回、隐私开关、read count。

## 7. 当前不做

- 不做群成员已读列表。
- 不做群 read count。
- 不做隐私开关。
- 不做物理删除。
- 不做管理员撤回成员消息。
- 不把已读/撤回作为普通聊天消息写入 message block。
- 不把 `TYPING` 作为普通消息进入 ingress/history/seq；只接受 typed `CHAT_TYPING` 控制命令。
