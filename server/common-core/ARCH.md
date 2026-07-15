# common-core/ARCH.md — 基础设施事实快照

> 服务端基础设施层：Mongo Repo + 队列 + 缓存 + 序列状态机。
> 改公共组件前必读。

## 1. 子包总览

| 包 | 职责 | 关键实现 |
| --- | --- | --- |
| `business/mongo/` | Mongo 持久化框架 | `MongoTemplate` + `@EnableMongoRepositories` |
| `business/mongo/document/` | `*Doc` 持久化对象 + `@CompoundIndexes` | `UserConversationDoc` 等 |
| `business/mongo/impl/` | `*RepositoryImpl`（手写，无泛型基类） | 每个 impl 都 dup `docId()` |
| `business/mongo/repository/` | `*Repository` 接口（domain 侧抽象） | |
| `cache/` | typed `CacheStore` / `CacheRegion`（Redis JSON） | `RedisCacheStore.java` |
| `history/` | 消息块、映射、附件、mutation 的唯一 Mongo 持久化 seam | `MessageHistoryRepository` |
| `queue/` | `QueueAdapter` 抽象 + chronicle/kafka 两实现 | `QueueAutoConfigurer.java:28` |
| `store/sequence/conversation/` | **会话 seq 状态机（生产级）** | `ConversationSeqAllocator.java:54`、`RedisConversationSeqCacheStore.java:129`、`ConversationSeqAllocatorConfigurer.java:40` |
| `store/session/` | SessionStateStore（redis/rocksdb 两实现） | `StateStoreAutoConfigurer.java` |
| `store/conversation/` | ConversationStateStore（每用户 maxSeq/readSeq/minSeq hot state） | |
| `notification/` | `NotificationSender` 基于 MessageSender 发系统通知，规则在 `NotificationRules` | `NotificationSender.java:36` |

## 2. Mongo 持久层不变量

- 所有 `*Doc` 用拼接字符串作复合 `_id`（shard-friendly），见 `UserConversationRepositoryImpl.java:293`。
- 写热点（`conversation_sequence`）用 `findAndModify $inc`，禁止 read-modify-write。
- 历史块 `message_block._id = {conversationId}:{blockNo}`，块内消息存稀疏 `messages.{seq-offset}`。
- 索引全部在 `@CompoundIndexes` 声明。
- impl 无泛型基类，新增时复制邻近 impl 的结构。

## 3. 会话 seq 分配器（生产级，不要乱改）

- 路径：`store/sequence/conversation/ConversationSeqAllocator.allocate`
- 状态机：`ALLOCATED` / `MISS` / `EXHAUSTED` / `LOCKED`
- Redis Lua 单脚本原子操作 + lock owner UUID + lock TTL 3s（`RedisConversationSeqCacheStore.java:129`）
- Mongo `findAndModify $inc` 作全局真相，Redis 是热缓存段
- `CLUSTER` 模式启动强校验 Redis 存在（`ConversationSeqAllocatorConfigurer.java:40`）
- 段预分配：单聊 50 / 群聊 100
- 允许空洞、禁止重复/回退
- seq 分配可作为消息 id/通知 id/批次 id 的模板复用

## 4. 队列与缓存

- `QueueAdapter` 后端：Chronicle（默认，单机文件）/ Kafka（集群，P1-6 序列化已修复 2026-07-07，两种后端端到端一致）
- 发送：`MessageProducer` 发 Protobuf bytes，key = `ConversationIdUtil.buildQueueKey`，保证同会话同 Kafka 分区
- 消费：`@QueueListener`，批量 `batch=true, batchSize=500` 优先
- 缓存写操作必须 `afterCommit` 删 key，避免脏读
- 业务缓存统一 `StringRedisTemplate` + 显式 JSON 类型；不使用本地 L1、Java 序列化或 DefaultTyping
- `SessionStateStore`、`ConversationStateStore`、`IdempotencyStore` 和 seq Lua 是原子状态接口，不属于通用缓存

## 5. 伸缩性约束（曰前）

- ~~`ReadSeqPersistenceWriter` / `UserMaxSeqPersistenceWriter` 单线程 drain + 有界队列~~：**已修复 2026-07-09**，两个 writer 均按 userId hash 分桶多线程 drain，同桶内聚合最大水位，见 ASSESSMENT P1-13
- `ConversationVersionLogDoc.createdAt` 有 180 天 TTL 索引，版本日志只保留增量同步窗口；长期历史仍以会话与消息主表为准。
- 用 Mongo 时不要假设单节点；新代码按可分片原则写

## 6. 改动评估 checklist

- [ ] 改 Mongo Doc `_id` 形态会破坏正在用的 collection，需加 migration 注释
- [ ] 改 `ConversationIdUtil` 必须同步所有 Repository impl 的 `_id` 拼接
- [ ] 新建 `*RepositoryImpl` 复制邻近 impl 模式，不要自创风格
- [ ] 改缓存 key 前缀需考虑存量数据兼容
