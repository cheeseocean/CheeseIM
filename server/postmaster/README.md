# Postmaster

`postmaster` 是 CheeseIM 的消息编排模块。它消费 ingress event，完成消息序列分配、历史持久化、用户会话序列推进，并生成后续投递事件。

## 职责

- 消费 `postbox` 发布的 ingress event。
- 为单聊/群聊消息分配 conversation seq 与用户侧 max seq。
- 根据消息选项决定是否写历史、是否计未读、是否生成投递事件。
- 将消息写入 MongoDB 历史块。
- 维护用户会话范围、同步点和 read/max seq 相关状态。
- 生成 delivery/offline push event，交给 `postman` 投递。

## 非职责

- 不直接接收 TCP/WS 客户端连接。
- 不处理 HTTP 请求。
- 不生成登录 token 或长连接 ticket。
- 不实现 vendor push。

## 关键类

| 类 | 说明 |
| --- | --- |
| `PostMaster` | 独立模块启动入口。 |
| `IngressEventListener` | ingress event 消费入口。 |
| `HistoryEventListener` | 历史事件消费入口。 |
| `ConversationSeqService` | 会话消息序列分配。 |
| `DefaultMessagePolicyEngine` | 消息策略判断。 |
| `BlockHistoryPersistenceService` | 历史块持久化。 |
| `UserMaxSeqPersistenceWriter` | 用户会话 max seq 推进。 |
| `GroupFanoutPlanner` | 群消息扇出规划。 |

## 设计文档

- `docs/ConversationArch.md`：会话模型设计背景。
- `docs/SeqArch.md`：seq 分配与缓存/持久化设计背景。
