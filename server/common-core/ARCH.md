# common-core/ARCH.md — 基础设施事实快照

> 服务端共享 port/model 与状态机内核：Repository/Queue/Cache/State port + 序列状态机。
> 改公共组件前必读。

common-core 是基础设施 library，不拥有 HTTP server。只为厂商调用共享 `spring-web` 客户端类型，
禁止重新引入 `spring-boot-starter-web` 或 Prometheus registry 并传递给所有业务模块；
管理端与 registry 应由可执行模块显式声明。

## 1. 子包总览

| 包 | 职责 | 关键实现 |
| --- | --- | --- |
| `business/repository/` | 业务 Repository port；Mongo adapter 在 storage-business | `UserRepository` 等 |
| `business/transaction/` | 持久化事务 port；Mongo transaction executor 在 storage-business | `PersistenceTransactionExecutor` |
| `cache/` | typed `CacheStore` / `CacheRegion` port；Redis 实现在 infra-state | `CacheStore` |
| `history/` | 历史 port + 无框架 model；Mongo 实现在 storage-history | `MessageHistoryRepository`、`history/model` |
| `queue/` | `QueueAdapter` port、消息/订阅模型、handler 与监听注解；运行时实现在 infra-queue | `QueueAdapter`、`QueueListener` |
| `queue/dlt/` | DLT 查询/redrive port 与审计存储契约；Kafka 实现在 infra-queue | `DltOperations`、`DltRedriveAuditStore` |
| `store/sequence/conversation/` | **会话 seq allocator/状态 model/port**；cache adapter 在 infra-state | `ConversationSeqAllocator` |
| `store/session/` | SessionStateStore port；Redis/RocksDB 实现在 infra-state | `SessionStateStore` |
| `store/session/refresh/` | Refresh token family port/result；codec 与实现位于 infra-state | `RefreshTokenStateStore` |
| `store/conversation/` | ConversationStateStore（每用户 maxSeq/readSeq/minSeq hot state） | |
| `store/idempotency/message/` | postbox 发送 inbox（稳定消息身份、短租约、首次 ACK） | Redis Lua / RocksDB 两实现 |
| `store/idempotency/ingress/` | postmaster ingress inbox（处理租约、稳定 seq、完成状态） | Redis pipeline + 单 key Lua / RocksDB |
| `store/fanout/` | 大群 fanout job lease、generation fencing 与页游标 | `MongoGroupFanoutJobStore` |
| `notification/` | `NotificationSender` 基于 MessageSender 发系统通知，规则在 `NotificationRules` | `NotificationSender.java:36` |

## 2. Mongo 持久层不变量

- 所有 `*Doc` 用拼接字符串作复合 `_id`（shard-friendly），见 `UserConversationRepositoryImpl.java:293`。
- 已分片集合的 upsert/query 必须显式携带 shard key；`_id` 是复合字符串不代表 mongos 能从中推导路由字段。
- 写热点（`conversation_sequence`）用 `findAndModify $inc`，禁止 read-modify-write。
- 用户 maxSeq 单条与批量更新统一使用 `$max`；writer drain 通过 unordered bulk upsert 落库，
  禁止恢复逐条 `$set` 导致跨副本水位回退。
- write-behind 统一经 `ImMetrics.writerBacklog` 上报 queued/inflight depth 和基于最老入队时间戳
  动态计算的 age；标签仅使用固定 writer/state，不引入用户或会话高基数。
- 历史块 `message_block._id = {conversationId}:{blockNo}`，块内消息存稀疏 `messages.{seq-offset}`。
- `MessageHistoryRepository` 只返回 `history.model` 纯模型；`*Doc`、Mongo/BSON 类型不得穿过 port。
  `storage-history` 在 adapter 边界转换 Document，并把 BSON Binary 规范为 `byte[]`。
- `group_member` 是当前成员资料；`group_member_epoch` 是不可物理删除的成员生命周期事实，
  以 `[joinedVersion,leftVersionExclusive)` 服务版本化 fanout 快照。
- `conversation` 服务 owner 维度当前态；`conversation_delivery_preference` 服务 conversation 维度反向过滤，
  两者由 business 事务双写，禁止在 owner-sharded 当前态上恢复全会话 scatter-gather 查询。
- Mongo Document、索引与 impl 已迁入 `storage-business`；common-core 禁止重新 import Spring Data Mongo。

## 3. 会话 seq 分配器（生产级，不要乱改）

- 路径：`store/sequence/conversation/ConversationSeqAllocator.allocate`
- 状态机：`ALLOCATED` / `MISS` / `EXHAUSTED` / `LOCKED`
- Redis Lua 单脚本原子操作 + lock owner UUID + lock TTL 3s（实现位于 infra-state）
- Mongo `findAndModify $inc` 作全局真相，Redis 是热缓存段
- `CLUSTER` 模式启动强校验 Redis 存在（`ConversationSeqAllocatorConfigurer.java:40`）
- 段预分配：单聊 50 / 群聊 100
- 允许空洞、禁止重复/回退
- seq 分配可作为消息 id/通知 id/批次 id 的模板复用

## 4. 队列与缓存

- `QueueAdapter` 后端实现在 `infra-queue`：Chronicle（默认，单机文件）/ Kafka（集群，两种后端 payload 语义一致）
- Cache/State/seq cache 的 Redis/RocksDB 实现在 `infra-state`；common-core 禁止重新依赖对应驱动
- common-core 禁止 import Spring Kafka、Kafka client、Chronicle 或 `infra.queue`；根构建门禁执行该边界
- 发送：`MessageProducer` 发 Protobuf bytes，key = `ConversationIdUtil.buildQueueKey`，保证同会话同 Kafka 分区
- 消费身份：`@QueueListener.group` 是稳定数据契约；禁止用环境变量改变 group
- 消费吞吐：`cheeseim.queue.listeners.<group>` 统一配置 concurrency/batchSize/batchInterval；
  `cheeseim.queue.consumer` 统一两种后端的总尝试次数与间隔
- `spring.kafka.consumer` 只管理 Kafka transport；本项目不使用 `@KafkaListener`，
  因此 `spring.kafka.listener.*` 不会成为 QueueAdapter 的配置入口
- 消费：`@QueueListener`，批量 `batch=true, batchSize=500` 优先
- DLT 运维只接受 `TopicNames` 管理的 source topic；查询使用 assign + seek，不加入 group、不提交 offset；
  redrive 先校验精确 offset/checksum，再取得 Mongo 审计租约，broker ACK 后完成审计。
- `dlt_redrive_audit` 默认长期保留；operationId 与记录身份、operator、reason 永久绑定。
- 缓存写操作必须 `afterCommit` 删 key，避免脏读
- 业务缓存统一 `StringRedisTemplate` + 显式 JSON 类型；不使用本地 L1、Java 序列化或 DefaultTyping
- `SessionStateStore`、`ConversationStateStore`、`IdempotencyStore` 和 seq Lua 是原子状态接口，不属于通用缓存
- `MessageSendInboxStore` 是专用状态机，不下沉到通用 `IdempotencyStore`：它需要固定 `serverMsgId/createTime`、载荷冲突检测、执行租约和首次 ACK，不能用简单 `SET NX` 表达
- `IngressMessageInboxStore` 同样是专用状态机：消费前 `SET NX` 会在崩溃时丢消息，因此必须使用 claim/seq binding/complete/release；Redis 批量实现不得退化为逐消息网络往返
- `RefreshTokenStateStore` 按 family 聚合 current/used/status/session；Redis key 使用 family hash tag，rotate/reuse detection 必须保持单 key 原子，且只存 token hash、绝不存原 token
- Redis 多 key 不变量：节点 ready/processing/lease/dead 共享 node tag；同一 `(userId, conversationId)` 的 read/max/unread 共享 `uc` tag；tag 对长度分隔身份做 URL-safe Base64，禁止直接拼接不可信 `{}` 字符
- 跨任意 slot 的批量缓存读取/删除禁止使用 MGET/多 key DEL，统一使用按 key pipeline；pipeline 只降低 RTT，不提供跨 key 原子性

## 5. 伸缩性约束（曰前）

- ~~`ReadSeqPersistenceWriter` / `UserMaxSeqPersistenceWriter` 单线程 drain + 有界队列~~：**已修复 2026-07-09**，两个 writer 均按 userId hash 分桶多线程 drain；UserMaxSeq writer 于 2026-07-19 进一步接入 Mongo unordered bulk `$max`
- `ConversationVersionLogDoc.createdAt` 有 180 天 TTL 索引，版本日志只保留增量同步窗口；长期历史仍以会话与消息主表为准。
- 用 Mongo 时不要假设单节点；新代码按可分片原则写

## 6. 改动评估 checklist

- [ ] 改 Mongo Doc `_id` 形态会破坏正在用的 collection，需加 migration 注释
- [ ] 改 `ConversationIdUtil` 必须同步所有 Repository impl 的 `_id` 拼接
- [ ] 新建 `*RepositoryImpl` 复制邻近 impl 模式，不要自创风格
- [ ] 改缓存 key 前缀需考虑存量数据兼容
- [ ] 新增 Lua 若使用 `KEYS[2+]`，必须证明所有 key 的 `{hashTag}` 完全一致并加入 cluster 集成用例
