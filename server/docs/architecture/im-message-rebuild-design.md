# IM 消息架构重构设计

> 状态：已过期。
> 当前实现已按一次性重构方案收敛到
> [2026-03-22-im-full-refactor-design.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/docs/superpowers/specs/2026-03-22-im-full-refactor-design.md)。
> 本文保留仅用于历史方案追溯，其中关于 `postman` 直接承接入口、`postbox` inbox 投影、以及旧 `HistoryTask/OfflinePushTask` 等内容已不再代表当前代码。

## 1. 目标

将当前消息发送主链路从：

```text
客户端 -> Gateway -> 服务端直接写 MongoDB -> 判断接收方是否在线 -> 在线则直接推送 -> 不在线则离线处理
```

重构为基于现有模块边界的异步解耦架构：

```text
客户端 -> postoffice -> postman -> Kafka -> postbox / postman / push
```

本方案严格沿用当前仓库模块命名：

- `postoffice`: 接入层与在线路由层
- `postman`: 消息接受、编排、顺序、补偿控制层
- `postbox`: 历史消息与收件箱投影存储层
- `push`: 厂商离线推送执行层

设计目标：

- 缩短发送同步主链路
- 将 MongoDB 从发送主路径剥离
- 拆开“历史落库”和“在线投递”
- 支持单聊、群聊、离线、ACK、已读的统一事件主线
- 使重试、幂等、补偿、扩容具备工程可操作性

---

## 2. 当前架构问题分析

### 2.1 同步链路过长

当前链路中，一次发送通常串行完成以下动作：

1. 网关鉴权
2. 参数校验
3. 写 MongoDB
4. 查询在线状态
5. 查连接路由
6. 推送到目标端
7. 离线处理

这导致发送 RT 由最慢组件决定。Mongo 写入抖动、Redis 热点、群聊成员展开、某些设备推送失败，都会直接拉长客户端发消息耗时。

### 2.2 MongoDB 成为发送主链路瓶颈

MongoDB 在当前模型里承担了“消息是否算接受”的核心判断，这会产生两个问题：

- 存储层写入延迟直接影响入口吞吐
- 群聊场景中，消息事实写入和收件箱投影写入容易放大 Mongo 写压力

MongoDB 适合作为历史事实存储，不适合作为发送接受的同步门槛。

### 2.3 落库和推送耦合

当前常见实现模式是：

```text
写库成功 -> 顺手推送 -> 推送失败再补偿
```

这种模型的问题：

- 存储失败与投递失败交织
- 无法独立扩容落库能力和投递能力
- 重试难以区分“该重存储”还是“该重推送”

### 2.4 群聊放大问题

群消息的放大量来自：

- 群成员列表展开
- 每个接收者的在线判断
- 多端连接投递
- 离线索引与未读投影写入

如果这些动作仍然在 `postoffice` 同步线程或一次 RPC 中完成，群聊会迅速把接入层拖垮。

### 2.5 重试和幂等难做

没有统一消息事件主线时，常见异常场景难以收敛：

- Mongo 实际写成功，但上游超时后重发
- 在线推送成功，但发送方没有拿到确认
- 群聊部分成员已处理，部分成员失败

结果就是重复落库、重复推送、重复离线通知难以避免。

### 2.6 扩容困难

当前模式下每个节点都承担接入、存储、投递、离线处理多重职责，扩容时只能“整条链路一起复制”，无法按瓶颈拆分扩容：

- 接入压大时，本应只扩 `postoffice`
- 群聊 fanout 压大时，本应只扩 `postman`
- Mongo 写入压大时，本应独立优化 `postbox`

---

## 3. 设计原则

### 3.1 接入层不承载存储事实

`postoffice` 只负责连接、协议、身份、在线路由，不负责 Mongo 持久化。

### 3.2 发送接受与副作用执行解耦

消息一旦被 `postman` 接受并生成稳定 `messageId + seq`，后续历史落库、在线投递、离线推送都通过事件推进。

### 3.3 历史消息与在线消息分离

- 历史真相由 `postbox` 管
- 在线投递由 `postman` 协调，最终回调 `postoffice`

### 3.4 有限顺序，而不是全局顺序

只保证会话内顺序，不追求全局顺序。

### 3.5 事件可重试，副作用必须幂等

Kafka 可重复投递，消费端必须具备幂等落库、幂等投递、幂等 ACK 收敛能力。

### 3.6 优先保证“接受成功”，而非“对方已收到”

发送方同步收到的是 `ACCEPTED`，不是 `DELIVERED`。是否送达由后续回执链路表达。

---

## 4. 目标架构

### 4.1 架构图

```text
                          +----------------------+
Client                    |      postoffice      |
  |  SEND                 | ws/tcp gateway       |
  +---------------------->+ auth route ack       |
                          +----------+-----------+
                                     |
                                     | SendMessageCommand
                                     v
                          +----------------------+
                          |       postman        |
                          | accept/idempotency   |
                          | seq/fanout plan      |
                          +----------+-----------+
                                     |
                                     | publish ingress
                                     v
                                +---------+
                                |  Kafka  |
                                +----+----+
                                     |
          +--------------------------+--------------------------+
          |                                                     |
          v                                                     v
+----------------------+                           +----------------------+
|       postbox        |                           |         push         |
| history / inbox      |                           | offline vendor push  |
| Mongo persistence    |                           +----------------------+
+----------+-----------+
           |
           | publish delivery task
           v
+----------------------+
|       postman        |
| delivery orchestration|
+----------+-----------+
           |
           | callback push request
           v
+----------------------+
|      postoffice      |
| pushToConnections    |
| route by conn map    |
+----------+-----------+
           |
           v
      Target Client
```

### 4.2 核心职责划分

#### `postoffice`

- 接收长连接消息
- 认证连接上下文
- 将发送请求转成 `SendMessageCommand`
- 将 `postman` 的投递请求路由到在线连接
- 将客户端 ACK / READ 回执转发到 `postman`

#### `postman`

- 发送前鉴权与风控
- 幂等控制
- 会话 `seq` 分配
- 事件发布
- 群聊成员展开和 fanout 批次规划
- 在线投递编排
- ACK / READ 状态收敛
- 补偿与死信调度

#### `postbox`

- 历史消息落库
- 单聊/群聊 inbox 投影
- 离线拉取
- 未读和最近会话摘要

#### `push`

- 处理离线推送候选任务
- 二次确认离线状态
- 厂商推送去重
- 推送取消与统计

---

## 5. 为什么需要类似 MsgTransfer 的传输层

本仓库不建议再单独引入一个名为 `MsgTransfer` 的新服务，更适合把这层能力沉淀为：

- Kafka topic 事件主线
- `postman` 中的 transfer orchestration

它存在的必要性在于：

1. 将消息“被接受”和“副作用执行”解耦
2. 支持削峰填谷
3. 支持消费失败重试与事件重放
4. 支持群聊 fanout 异步批处理
5. 支持存储、在线投递、离线推送独立扩容

如果没有这一层，系统就会重新退化成：

```text
接入层 = 存储层 = 推送层
```

这正是当前重构要解决的问题。

---

## 6. Kafka Topic 设计

### 6.1 Topic 列表

建议第一版控制 topic 数量，不做过度细分。

```text
im.message.ingress
  发送请求被 postman 接受后的主入口事件

im.message.history
  历史落库任务

im.message.delivery
  在线投递任务

im.message.group_fanout
  群聊成员展开后的批量任务

im.message.receipt
  ACK / READ / RECALL 等回执事件

im.message.retry
  补偿重试任务

im.message.dlq
  死信事件
```

### 6.2 Topic Key 设计

- `im.message.ingress`: `conversationId`
- `im.message.history`: `conversationId`
- `im.message.group_fanout`: `conversationId`
- `im.message.delivery`: 单聊可用 `receiverId`，群聊批次可用 `conversationId`
- `im.message.receipt`: `conversationId`

原则：

- 会话内消息尽量进入同一分区，保证 seq 顺序
- 投递批次可以按接收方或会话维度有序

### 6.3 事件结构建议

#### MessageIngressEvent

```json
{
  "eventId": "evt_001",
  "messageId": "msg_001",
  "clientMsgId": "cmsg_001",
  "conversationId": "single:userA:userB",
  "conversationType": "SINGLE",
  "senderId": "userA",
  "receiverId": "userB",
  "groupId": null,
  "seq": 10001,
  "payload": {},
  "sendTime": 1710000000000,
  "traceId": "trace_001",
  "retryCount": 0
}
```

#### DeliveryTaskEvent

```json
{
  "eventId": "evt_delivery_001",
  "messageId": "msg_001",
  "conversationId": "single:userA:userB",
  "receiverId": "userB",
  "devicePolicy": "ALL_ONLINE",
  "seq": 10001,
  "traceId": "trace_001"
}
```

#### ReceiptEvent

```json
{
  "eventId": "evt_receipt_001",
  "messageId": "msg_001",
  "conversationId": "single:userA:userB",
  "userId": "userB",
  "deviceId": "ios_001",
  "receiptType": "DELIVERED",
  "seq": 10001,
  "receiptTime": 1710000001000
}
```

---

## 7. Redis / MongoDB / Kafka 分工

### 7.1 Redis 分工

Redis 只承载运行态数据，不承载历史真相：

- 在线连接索引
- 发送幂等键
- 消费去重键
- 读游标缓存
- 群成员缓存
- 会话 seq 计数器或号段
- 投递去重键

建议 key：

```text
im:route:user:{userId}
im:route:conn:{connId}
im:send:idem:{senderId}:{clientMsgId}
im:consume:dedup:{consumerGroup}:{eventId}
im:delivery:dedup:{messageId}:{receiverId}:{deviceId}
im:conversation:seq:{conversationId}
im:conversation:read:{userId}:{conversationId}
im:group:members:{conversationId}
```

### 7.2 MongoDB 分工

MongoDB 承载消息事实和投影：

- `im_message`
- `im_inbox`
- `im_conversation_read_cursor`
- 可选 `im_delivery_receipt_summary`

#### `im_message`

一条逻辑消息一条事实记录。

关键字段建议：

- `messageId`
- `clientMsgId`
- `conversationId`
- `conversationType`
- `senderId`
- `payload`
- `seq`
- `sendTime`
- `status`

唯一索引：

- `messageId`

普通索引：

- `(conversationId, seq)`
- `(senderId, sendTime)`

#### `im_inbox`

每个接收用户一条投影记录。

关键字段建议：

- `userId`
- `conversationId`
- `messageId`
- `seq`
- `read`
- `deliveredAt`
- `createdAt`

唯一索引：

- `(userId, messageId)`

查询索引：

- `(userId, conversationId, seq)`
- `(userId, read, seq)`

#### `im_conversation_read_cursor`

按会话维护已读游标，而不是 per-message read row。

关键字段：

- `userId`
- `conversationId`
- `readSeq`
- `readAt`

唯一索引：

- `(userId, conversationId)`

### 7.3 Kafka 分工

Kafka 只做：

- 消息传输
- 削峰
- 重试
- 补偿
- 解耦

Kafka 不做：

- 历史查询
- 在线路由
- 已读真相查询

---

## 8. 顺序性设计

### 8.1 seq 在哪一层生成

会话 `seq` 必须在 `postman` 生成。

原因：

- `postoffice` 只是接入层，不应该掌握会话顺序语义
- `postbox` 是存储层，不应让 seq 绑定 Mongo 落库时机
- `postman` 是发送接受与编排层，最适合在 accept 阶段分配稳定 seq

### 8.2 seq 分配规则

- 单聊：按 `conversationId` 单调递增
- 群聊：按群会话 `conversationId` 单调递增

第一版建议直接使用 Redis `INCR`：

```text
im:conversation:seq:{conversationId}
```

如吞吐继续上升，再演进为号段模式。

### 8.3 顺序保证边界

保证：

- 同一 `conversationId` 内消息 seq 单调递增
- Kafka 同 key 分区内消费有序
- `postoffice` 在单连接内尽量按 seq 顺序发送

不保证：

- 不同会话之间全局顺序
- 不同设备之间绝对同时送达

---

## 9. 单聊 / 群聊 / 离线 / 历史落库时序图

### 9.1 单聊发送时序

```text
Client A -> postoffice: SEND(clientMsgId, conversationId, payload)
postoffice -> postman: SendMessageCommand(ctx, payload)
postman -> Redis: sender idempotency check
postman -> Redis: nextSeq(conversationId)
postman -> Kafka(im.message.ingress): publish MessageIngressEvent
postman -> postoffice: SEND_ACK(messageId, seq, ACCEPTED)

Kafka -> postbox: consume ingress/history task
postbox -> Mongo(im_message): upsert message fact
postbox -> Mongo(im_inbox): upsert receiver inbox
postbox -> Kafka(im.message.delivery): publish delivery task

Kafka -> postman: consume delivery task
postman -> postoffice: pushToConnections(receiver, envelope)
postoffice -> Client B: MESSAGE(messageId, seq, payload)
Client B -> postoffice: DELIVERED_ACK(messageId)
postoffice -> Kafka(im.message.receipt): publish receipt
Kafka -> postman/postbox: converge delivered state
```

### 9.2 群聊发送时序

```text
Client A -> postoffice: SEND(groupMessage)
postoffice -> postman: SendMessageCommand
postman -> Redis: sender idempotency check
postman -> Redis: nextSeq(groupConversationId)
postman -> Kafka(im.message.ingress): publish group ingress
postman -> postoffice: SEND_ACK(messageId, seq, ACCEPTED)

Kafka -> postman: consume group ingress
postman -> Redis/postbox GroupMemberService: load member snapshot
postman -> Kafka(im.message.group_fanout): publish member batches

Kafka -> postbox: consume fanout batch
postbox -> Mongo(im_message): upsert single message fact
postbox -> Mongo(im_inbox): bulk upsert batch inbox projections
postbox -> Kafka(im.message.delivery): publish online delivery batch

Kafka -> postman: consume delivery tasks
postman -> postoffice: pushToConnections(batch online users)
postoffice -> Clients: MESSAGE(messageId, seq, payload)
```

### 9.3 离线处理时序

```text
postman -> postoffice: pushToConnections(receiver, envelope)
postoffice -> postman: offline / partial_success
postman -> Kafka(im.message.retry or offline candidate): publish task

Kafka -> push: consume offline candidate
push -> Redis/postoffice route query: recheck user online state
push -> vendor adapter: send push notification if still offline
push -> metrics/store: record push attempt result
```

### 9.4 历史落库时序

```text
Kafka(im.message.ingress) -> postbox
postbox -> Mongo(im_message): upsert by messageId
postbox -> Mongo(im_inbox): upsert by (userId, messageId)
postbox -> Kafka(im.message.delivery): publish delivery task
```

---

## 10. Mongo 落库和在线投递谁先谁后

推荐第一版采用：

```text
先形成稳定 messageId + seq -> postbox 落库 -> 再发在线投递任务
```

### 原因

1. 接收方收到消息后，历史拉取可以立即命中
2. 避免“用户在线收到了，但历史里查不到”的撕裂状态
3. 补偿链路更简单

### 工程上如何理解

- `postman` accept 后即返回发送者 `ACCEPTED`
- `postbox` 负责将消息事实写入 Mongo
- `postbox` 成功后，再发 `im.message.delivery`

未来若追求极致低延迟，可演进为“落库与在线投递并行推进”，但第一版不建议。

---

## 11. 幂等设计

### 11.1 发送幂等

客户端必须提供 `clientMsgId`。

`postman` 使用 Redis 保存：

```text
im:send:idem:{senderId}:{clientMsgId}
```

值结构：

```json
{
  "messageId": "msg_001",
  "seq": 10001,
  "status": "ACCEPTED"
}
```

当客户端因为超时重发时，直接返回同一个 `messageId + seq`。

### 11.2 Kafka 消费重复如何避免重复落库

使用两层保护：

#### 第一层：消费去重键

```text
im:consume:dedup:{consumerGroup}:{eventId}
```

#### 第二层：Mongo 唯一键

- `im_message.messageId` 唯一
- `im_inbox(userId, messageId)` 唯一

即使 Kafka 重复消费，也不会重复产生历史事实或收件箱记录。

### 11.3 Kafka 消费重复如何避免重复推送

使用投递去重键：

```text
im:delivery:dedup:{messageId}:{receiverId}:{deviceId}
```

并要求客户端按 `messageId` 做最终展示去重。

服务端目标：

- 不重复副作用
- 即使重复也不会造成历史数据脏写

---

## 12. ACK / 已读回执设计

### 12.1 回执类型

- `SEND_ACK`: 系统已接受消息，回给发送者
- `DELIVERED_ACK`: 目标端设备收到消息，回给服务端
- `READ_ACK`: 用户已读到某个 `seq`
- `RECALL`: 撤回事件

### 12.2 `SEND_ACK`

由 `postman` accept 成功后返回给 `postoffice`，再同步回给发送端。

包含：

- `messageId`
- `seq`
- `conversationId`
- `acceptTime`

### 12.3 `DELIVERED_ACK`

由接收端客户端在消息成功入本地队列后回传：

- `messageId`
- `conversationId`
- `seq`
- `deviceId`
- `receiptTime`

`postoffice` 收到后发布到 `im.message.receipt`。

### 12.4 `READ_ACK`

建议采用“会话已读游标”模式，而不是“逐条消息已读”：

```json
{
  "conversationId": "group:team-001",
  "userId": "userB",
  "readSeq": 10520,
  "readTime": 1710000002000
}
```

`postbox` 只做游标推进：

- 若新 `readSeq` 小于已有游标，忽略
- 若更大，则更新为新值

### 12.5 群已读设计建议

大群不建议默认维护“每条消息每个人是否已读”的全量明细。

建议：

- 小群可扩展已读明细
- 大群默认只维护会话 read cursor 和已读人数汇总

---

## 13. 为什么在线投递必须回调 `postoffice`

`push` 或 `postman` 不应该自己直推长连接。

原因：

1. 连接生命周期属于 `postoffice`
2. 多网关节点下，只有 `postoffice` 掌握精确 `connId -> node` 路由
3. 协议编解码、设备策略、多端登录策略都集中在 `postoffice`
4. 如果多个服务都持有连接，会导致连接状态分裂、ACK 路径分裂、踢线策略分裂

因此正确模式是：

- `postman` 决策“推给谁”
- `postoffice` 负责“如何推到哪个连接”

---

## 14. 群消息放大与广播压力控制

### 14.1 不在 `postoffice` 做群全量广播

`postoffice` 只接受单条逻辑消息，不做群成员同步遍历。

### 14.2 群 fanout 批处理

`postman.GroupFanoutPlanner` 负责按批次切分成员列表，例如：

- 200 人/批
- 500 人/批

批次大小应可配置。

### 14.3 在线与离线分流

群成员展开后优先区分：

- 在线候选
- 离线候选

在线走 `im.message.delivery`，离线走 `push` 候选任务。

### 14.4 大群投影优化

对超大群建议分层：

- `im_message` 仍只保存一条消息事实
- `im_inbox` 采用批量 upsert
- 对超大群允许只维护“拉取游标”，弱化 per-user inbox

### 14.5 大群拉模式

当群规模极大时，可演进为：

- 在线用户实时推送
- 离线用户主要依赖会话增量拉取

这样可显著降低离线广播压力。

---

## 15. 可靠性设计

### 15.1 接受语义

同步返回给发送方的语义定义为：

```text
消息已被系统接受并获得稳定 messageId + seq
```

而不是：

```text
对方已经收到
```

### 15.2 重试策略

- 瞬时错误：指数退避重试
- 不可恢复错误：直接进入失败态
- 超过最大重试次数：进入 `im.message.dlq`

### 15.3 补偿范围

`postman.DeliveryCompensationService` 应覆盖：

- ingress 已接受但 history 未成功
- history 成功但 delivery 未发出
- delivery 发出但 `postoffice` 回调失败
- `postoffice` 全部无在线连接，需进入离线路径
- receipt 处理失败，需重放回执事件

### 15.4 DLQ 原则

以下情况入死信：

- schema 不合法
- 权限状态已变更且消息不可恢复
- 下游数据损坏
- 重试已超阈值

---

## 16. 监控方案

### 16.1 链路标识

每条消息必须带：

- `traceId`
- `messageId`
- `clientMsgId`
- `conversationId`

### 16.2 关键指标

#### `postoffice`

- `postoffice.send.qps`
- `postoffice.send.accept.rt`
- `postoffice.online.connection.count`
- `postoffice.gateway.push.success.rate`

#### `postman`

- `postman.accept.qps`
- `postman.idempotent.hit.rate`
- `postman.seq.allocate.rt`
- `postman.group.fanout.batch.count`
- `postman.delivery.compensation.count`

#### `postbox`

- `postbox.message.persist.rt`
- `postbox.inbox.bulk_upsert.rt`
- `postbox.mongo.write.error.rate`

#### `push`

- `push.offline.candidate.count`
- `push.vendor.success.rate`
- `push.stale.skip.rate`

#### Kafka

- produce TPS
- consume TPS
- consumer lag
- retry topic backlog
- DLQ backlog

### 16.3 告警建议

- `im.message.ingress` consumer lag 持续升高
- `im.message.delivery` lag 异常
- Mongo 写失败率超阈值
- `postoffice` 在线投递成功率突降
- `push` 厂商失败率突增
- `im.message.dlq` 非零持续增长

---

## 17. 风险分析

### 17.1 事件化后系统复杂度提高

收益是解耦，代价是：

- 跨服务排障复杂
- schema 演进要治理
- topic 管理和重放策略要制度化

### 17.2 顺序和吞吐存在天然权衡

同会话严格保序通常意味着同 key 同分区，会限制单热点会话吞吐。应接受“会话内顺序优先”的设计取舍。

### 17.3 大群成本仍然高

即使异步化，大群的成员展开、投影写入、已读汇总仍然昂贵，需要在第三期引入专项优化。

### 17.4 双链路灰度期间需要对账

必须准备：

- 新旧链路 message 数量对账
- seq 连续性对账
- inbox 数量对账
- 投递成功率对账

---

## 18. 贴近现有工程的模块接口设计

以下接口命名尽量贴近当前工程中的：

- `postoffice.handler.ChatMessageHandler`
- `postoffice.service.GatewayPushServiceImpl`
- `postman.service.MessageDeliveryServiceImpl`
- `postman.service.MessageIdempotencyService`
- `postman.service.GroupFanoutPlanner`
- `postbox.service.MessageStoreServiceImpl`
- `push.service.impl.MessagePushServiceImpl`

### 18.1 `postoffice` 入口

```java
public SendMsgResp receiveClientMessage(ConnectionContext ctx, CheeseMessage req) {
    requireAuthenticated(ctx);
    validatePayload(req);
    rateLimit(ctx.getUserId(), req.getConversationId());

    SendMessageCommand cmd = new SendMessageCommand();
    cmd.setTraceId(currentTraceId());
    cmd.setClientMsgId(req.getClientMsgId());
    cmd.setSenderId(ctx.getUserId());
    cmd.setConversationId(req.getConversationId());
    cmd.setConversationType(req.getConversationType());
    cmd.setPayload(req.getContent());
    cmd.setSessionId(ctx.getSessionId());
    cmd.setDeviceId(ctx.getDeviceId());
    cmd.setSendTime(System.currentTimeMillis());

    return messageDeliveryService.accept(cmd);
}
```

### 18.2 `postman` 接受并发布入口事件

```java
public SendMsgResp accept(SendMessageCommand cmd) {
    messageAuthFacade.checkSession(cmd.getSessionId(), cmd.getSenderId());
    messageSendPermissionChecker.checkSendAllowed(cmd.getSenderId(), cmd.getConversationId());

    SendMsgResp cached = messageIdempotencyService.findAccepted(cmd.getSenderId(), cmd.getClientMsgId());
    if (cached != null) {
        return cached;
    }

    String messageId = messageIdGenerator.nextId();
    long seq = conversationSeqService.nextSeq(cmd.getConversationId());

    MessageIngressEvent event = MessageIngressEvent.from(cmd, messageId, seq);
    kafkaPublisher.publish("im.message.ingress", cmd.getConversationId(), event);

    SendMsgResp accepted = new SendMsgResp();
    accepted.setServerMsgId(messageId);
    accepted.setSeq(seq);
    accepted.setStatus("ACCEPTED");
    messageIdempotencyService.saveAccepted(cmd.getSenderId(), cmd.getClientMsgId(), accepted);
    return accepted;
}
```

### 18.3 `postman` 处理入口事件

```java
public void handleIngressEvent(MessageIngressEvent event) {
    if (consumerDedupService.alreadyProcessed("postman-ingress", event.getEventId())) {
        return;
    }

    if ("SINGLE".equals(event.getConversationType())) {
        kafkaPublisher.publish("im.message.history", event.getConversationId(), HistoryTask.single(event));
        consumerDedupService.markProcessed("postman-ingress", event.getEventId());
        return;
    }

    GroupMemberSnapshot snapshot = groupMemberService.loadSnapshot(event.getConversationId());
    List<List<String>> batches = groupFanoutPlanner.partition(snapshot.getMemberIds(), 500);
    for (List<String> batch : batches) {
        kafkaPublisher.publish("im.message.group_fanout",
                event.getConversationId(),
                GroupFanoutTask.of(event, batch, snapshot.getVersion()));
    }
    consumerDedupService.markProcessed("postman-ingress", event.getEventId());
}
```

### 18.4 `postbox` 持久化

```java
public PersistResult persist(HistoryTask task) {
    if (consumerDedupService.alreadyProcessed("postbox-history", task.getEventId())) {
        return PersistResult.duplicated();
    }

    messageRepository.upsertByMessageId(toMessageDocument(task));

    if (task.isSingle()) {
        inboxRepository.upsert(task.getReceiverId(), task.getConversationId(), task.getMessageId(), task.getSeq());
    } else {
        inboxRepository.bulkUpsert(task.getReceiverIds(), task.getConversationId(), task.getMessageId(), task.getSeq());
    }

    kafkaPublisher.publish("im.message.delivery", task.deliveryKey(), DeliveryTask.from(task));
    consumerDedupService.markProcessed("postbox-history", task.getEventId());
    return PersistResult.success();
}
```

### 18.5 `postman` 在线投递调度

```java
public void dispatch(DeliveryTask task) {
    if (deliveryDedupService.alreadyProcessed(task.getMessageId(), task.getReceiverId())) {
        return;
    }

    PushEnvelope envelope = PushEnvelope.from(task);
    PushResult result = gatewayPushService.push(envelope);

    if (result.isOffline()) {
        kafkaPublisher.publish("im.message.retry", task.getReceiverId(), OfflinePushTask.from(task));
    }

    deliveryDedupService.markProcessed(task.getMessageId(), task.getReceiverId());
}
```

### 18.6 `postoffice` 推送连接

```java
public PushResult pushToConnections(PushEnvelope envelope) {
    List<UserConnection> connections = connectionManager.getUserConnections(envelope.getReceiverId());
    if (connections.isEmpty()) {
        return PushResult.offline();
    }

    int success = 0;
    for (UserConnection connection : connections) {
        if (deliveryDedupService.alreadyPushed(envelope.getMessageId(), connection.getConnectionId())) {
            continue;
        }
        boolean pushed = connectionManager.sendMessage(connection.getConnectionId(), envelope.toWsMessage());
        if (pushed) {
            deliveryDedupService.markPushed(envelope.getMessageId(), connection.getConnectionId());
            success++;
        }
    }
    return PushResult.of(success, connections.size());
}
```

### 18.7 `push` 离线推送

```java
public void dispatch(OfflinePushTask task) {
    if (pushDecisionService.shouldSkip(task.getMessageId(), task.getReceiverId())) {
        return;
    }
    if (onlineRouteService.isOnline(task.getReceiverId())) {
        return;
    }

    PushAttempt attempt = pushAttemptRepository.createOrLoad(task.getMessageId(), task.getReceiverId());
    if (attempt.isCompleted()) {
        return;
    }

    vendorPushProvider.send(task.toPushMessage());
    pushAttemptRepository.markCompleted(attempt.getAttemptId());
}
```

---

## 19. 灰度迁移方案

### 19.1 总体原则

- 先建事件主线
- 再剥离 Mongo 同步落库
- 再切在线投递路径
- 全程保留回滚开关

### 19.2 灰度阶段 A：旁路写 Kafka

做法：

- `postoffice -> postman` 接受消息后
- 保留老的 Mongo 与老的在线推送逻辑
- 同时写 `im.message.ingress`
- 新链路只做消费、日志、对账，不影响线上结果

目标：

- 验证 event schema、topic key、consumer lag、trace 字段

### 19.3 灰度阶段 B：历史落库切到 `postbox`

做法：

- 关闭入口层直写 Mongo
- 改为 `postbox` 消费后落 `im_message` / `im_inbox`
- 在线推送暂时仍可保留老模式或双发比对

目标：

- 将 Mongo 从同步主链路摘掉

### 19.4 灰度阶段 C：在线投递切到 `postman -> postoffice`

做法：

- 关闭 `postoffice` 内部“发完即推”逻辑
- 统一由 `postman` 消费 `im.message.delivery`
- 再通过 `GatewayPushServiceImpl` 回调 `postoffice`

目标：

- 历史与在线彻底解耦

### 19.5 灰度阶段 D：ACK / READ / 补偿闭环

做法：

- `postoffice` 接收 `DELIVERED_ACK` / `READ_ACK`
- 发布到 `im.message.receipt`
- `postman` / `postbox` 收敛状态
- 启用 `retry` / `dlq`

目标：

- 完成完整消息闭环

---

## 20. 分三期改造计划

### 第一期：最小成本引入 Kafka

目标：

- 在不推翻现有可用链路的前提下，建立消息事件主线

改造内容：

- `postman` 新增消息 accept 入口
- 发送幂等统一收口到 `MessageIdempotencyService`
- 在 `postman` 生成 `messageId + seq`
- 发布 `im.message.ingress`
- 保留旧 Mongo 落库和旧在线推送逻辑
- 建立基础监控、trace、对账

完成标志：

- 每条消息都有稳定 `messageId + clientMsgId + seq + traceId`
- Kafka 中能看到 ingress 主事件
- 新链路消费不影响线上功能

### 第二期：拆分历史落库和在线推送

目标：

- 将 MongoDB 从发送同步主链路剥离

改造内容：

- `postbox` 消费 `im.message.history` 或 `im.message.ingress`
- 异步落 `im_message`
- 异步写 `im_inbox`
- 成功后发布 `im.message.delivery`
- 由 `postman -> postoffice` 完成在线投递
- 老的网关直写 Mongo 逻辑下线

完成标志：

- 客户端发送不再等待 Mongo 完成
- 历史和在线投递独立观测

### 第三期：拆出完整投递控制面

目标：

- 完成 ACK、已读、补偿、死信、离线推送的完整体系

改造内容：

- `postman` 完成 group fanout、retry、DLQ
- `push` 接管厂商离线推送
- `postoffice` 接入 `DELIVERED_ACK` / `READ_ACK`
- `postbox` 维护 read cursor
- 增加大群优化策略和监控告警

完成标志：

- 消息链路可追踪、可重试、可对账、可回放
- ACK / 已读 / 离线推送闭环可用

---

## 21. 结论

本次重构的核心不是“给现有工程塞一个 Kafka”，而是把消息系统从同步调用链改造成事件驱动链：

- `postoffice` 负责接入和连接
- `postman` 负责接受、排序、编排、补偿
- `postbox` 负责历史事实和收件箱投影
- `push` 负责厂商离线推送
- Kafka 负责传输、削峰、重试和解耦

在这个模型下：

- Mongo 不再卡发送主 RT
- 单聊与群聊走统一事件主线
- 存储、在线投递、离线推送可独立扩容
- ACK、已读、补偿、监控可以标准化建设

这是你当前工程从“能发消息”演进到“能稳定承载 IM 消息系统”的必要重构方向。
