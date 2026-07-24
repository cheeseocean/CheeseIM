# CheeseIM 服务端架构评估与演进路线

> 评估时间：2026-07-05
> 评估对象：`server/` 全模块（Java 17 + Spring Boot 3 + Dubbo 3 + Gradle + MongoDB + Redis）
> 评估方法：基于源码逐行扫描，结论可追溯到具体文件:行号
> 维护原则：本文档为权威评估，与代码事实冲突时以代码为准；下次评估需更新本文。

## 一、综合结论

CheeseIM 是一个**架构骨架已经为集群设计、在线投递主链路已补齐多节点能力**的早期开源 IM 服务端。其模块边界（postoffice / postbox / postmaster / postman / authcenter / business / common-api / common-core）和"邮政"隐喻清晰，业内少见。

- 单节点 `postoffice` + Redis + Mongo 的实测上限大致在 **10-30 万并发长连接**。
- P0 主链路（节点身份、群扩散、路由原子化、投递去重、Kafka 端到端）已修复；P1 中的 ConnectionManager 分片锁、跨节点踢下线、HistoryQuery fail-closed/分页、MessageIdMapping 批量写已完成，authcenter `tokenVersion`/ban 持久化与 WS ticket 原子 consume 已完成，当前瓶颈转为 **存储分片、多副本推送状态与长压验证**。
- 架构骨架本身可演进到百万级，**不需要推倒重写**；后续以容量压测和 P1/P2 瓶颈治理为主。

---

## 二、已实现能力（生产质量分级）

### A. 关键路径已接通（仍需长压/chaos 验证）

| 能力 | 实现 | 关键代码 |
| --- | --- | --- |
| 会话 seq 分配 | Redis Lua 状态机（ALLOCATED/MISS/EXHAUSTED/LOCKED）+ Mongo `findAndModify $inc` 段预分配 + 启动时 Redis 守卫 | common-core `ConversationSeqAllocator` port/状态机；infra-state `RedisConversationSeqCacheStore`/自动配置 |
| api-server 无状态 HTTP Facade | Controller 不下沉 Response 模型，Facade 编排 Dubbo | `api-server/.../controller/*`、`*Facade.java` |
| JWT 无状态 access token | HS256，跨节点共享验证 | `authcenter/.../auth/JwtTokenIssuer.java:25` |
| Postbox ingress 按 conversation key 分区 | 保序投递到 Kafka 分区 | `postbox/.../IngressMessagePublisher.java:23` |
| 离线推送 5 厂商真实集成 | APNs(pushy)/FCM/Huawei/Xiaomi/JPush，全 lifecycle，`enabled:false` 默认 | `postman/.../provider/*`、`OfflinePushServiceImpl.java:58` |
| Mongo `_id` shard-friendly | `{owner}:{peer}` / `conversationId` 形式 | `UserConversationRepositoryImpl.java:293` 等 |
| 会话增量同步 version-log | 200 上限回退全量 | `business/.../ConversationServiceImpl.java:263` |
| 5 厂商推送回执 + 日上限 + 定时清理 | | `module-postman.yml` |
| 在线路由表 | Redis HASH + TTL，`register` / `refresh` / `unregister` 走 Lua 原子脚本，直连 Redis 不再走 L1 | `postoffice/.../RedisOnlineRouteService.java`、`postoffice/ARCH.md` §3 |
| 跨节点在线投递 | `gatewayNode` 写入真实节点 ID，cluster profile/runtime mode 均强制稳定 node-id；生产者统一 `NodeQueueMessage` envelope，Redis ready LIST + processing HASH/租约 ZSET + bounded dead LIST 支持 ACK、失败重试、跨实例过期回收，并对三种状态设容量/TTL 防护；待长压/chaos 验证 | `NodeIdentityProvider`、`RedisNodeDeliveryService`、`NodeDeliveryPoller` |
| 群消息投递 | NORMAL_GROUP 写扩散到成员级 DeliveryEvent，SUPER_GROUP 读扩散仅持久化 | `postmaster/.../IngressEventListener.java`、`GroupFanoutPlanner` |
| 投递去重 | `DeliveryDedupStore` + Redis 单 key claim/commit/abort Lua；ChannelFuture 成功后才提交，跨节点共享且 TTL 自动恢复 | `OnlineDispatcherImpl`、`RedisDeliveryDedupStore`、`DeliveryWriteFinalizer` |
| Kafka 队列路径 | Producer/Consumer 统一 protobuf bytes，离线推送经 `QueueAdapter`；两后端的 payload/订阅契约对齐，可靠性边界不同 | `KafkaQueueAdapter`、`ChronicleQueueAdapter`、`OfflinePushEventProducer` |

### B. 已实现但存在集群缺口（需修复才能多节点）

| 能力 | 集群缺口 |
| --- | --- |
| 踢下线（KickoffCommandService） | 已按 `gatewayNode` 定向入节点队列，Redis 不可用或部分节点入队失败时仍只能退化为本节点尝试 |
| 连接管理（ConnectionManager） | 已由全局 `synchronized` 改为 connection/user 分片锁；仍需长压/重连风暴验证 |
| 业务缓存（CacheStore） | Redis JSON + 显式类型，默认无本地 L1，避免跨节点陈旧读 |
| 队列抽象（QueueAdapter） | Chronicle 仍是单机文件默认后端；Kafka 端到端已修复，集群 profile 默认启用 `cheeseim.queue.type=kafka` 并要求通过环境变量配置 bootstrap |
| WS ticket 一次性 consume | **已修复 2026-07-08**：Redis Lua `GET` + `DEL` 单脚本，RocksDB fallback 同步删除后返回 |
| `tokenVersion` 踢下线校验 | **已修复 2026-07-08**：用户级版本落 Mongo，登录/签票/校验读取同一版本 |
| 用户封禁标志 | **已修复 2026-07-08**：封禁标志落 Mongo `user_security_state`，Redis 仅作缓存 |
| ~~好友 accept 非事务~~ | **已修复 2026-07-13**：cluster 模式下申请状态与双向好友关系通过 MongoDB 事务原子提交，通知及缓存失效延后至提交完成；all-in-one 单机 Mongo 默认关闭事务 |
| ~~History 查询全扫~~ | **已修复 2026-07-07**：`getConversationMessages` 按 latest blockNo + range 窗口读取，不再拉全量 block |
| ~~权限校验失败放行~~ | **已修复 2026-07-07**：`HistoryQueryService.allow` 改为 fail-closed，RPC 异常仅使用短 TTL 本地缓存兜底 |
| MessageIdMappingDoc 逐条 save | 非 `bulkOps`，50k msg/s 写不动 |
| ~~UserMaxSeq / ReadSeq 写 behind~~ | **已修复 2026-07-09**：按 userId 分桶多线程 drain，同桶内聚合最大水位，跨用户并行写 Mongo |
| ~~ConversationVersionLog 无 TTL~~ | **已修复 2026-07-08**：`ConversationVersionLogDoc.createdAt` 增 180 天 TTL 索引 |

### C. 已声明协议但未在链路上接通或尚未完全统一

- `CHAT_READ` 已接通 `ReadStateService`、跨节点 typed notify 与 version-log 增量信号；`CHAT_REVOKE` 已接通 `MessageMutationService`、两分钟发送者权限校验、跨节点 notify 与历史 overlay。
- 普通消息 `READ_RECEIPT` 遗留旁路已删除：postoffice 拒绝旧内容类型，postmaster 防御性丢弃遗留队列数据；typed `CHAT_READ` 是唯一已读入口。
- ~~WS 异步投递路径走 JSON。~~ **已修复 2026-07-13**：`WsServerHandler` 入站/响应与 `ConnectionManager` 异步在线投递全部使用 Binary WebSocket Frame + `ProtoEnvelopeMapper`，TCP/WS 共用 typed protobuf envelope。

---

## 三、能否应对百万级并发？逐项判定

### 3.1 阻断性问题（必修，按严重度排序）

| 级别 | 问题 | 位置 | 影响 |
| --- | --- | --- | --- |
| ~~**P0**~~ | ~~跨节点在线投递失效：`gatewayNode` 硬编码 `"postoffice"`，Dubbo 默认 LB 随机选节点~~ | ~~`ConnectionManager.java:486`、`OnlineDispatcherImpl.java:67`~~ | **已修复 2026-07-07**：`NodeIdentityProvider` 写入真实节点 ID 到 `gatewayNode`；postman 按 `gatewayNode` 分组，通过 Redis LIST `delivery:node:{nodeId}` LPUSH/BRPOP 投递到正确节点；`NodeDeliveryPoller` 后台 daemon 线程消费并委托 `OnlineDispatcherImpl` 本地投递。跨节点在线投递不再依赖 Dubbo 随机 LB |
| ~~**P0**~~ | ~~群投递被硬跳过~~ | ~~`DeliveryEventListener.java:59-61`~~ | **已修复 2026-07-06**：`IngressEventListener.fanoutGroupDelivery` 接通 `GroupFanoutPlanner`，NORMAL_GROUP 走写扩散按成员切片 publish N 个 keyed DeliveryEvent（`g:{groupId}:{memberId}`），SUPER_GROUP 走读扩散仅持久化，postman `DeliveryEventListener` 去除 `ChatType.GROUP` 跳过分支 |
| ~~**P0**~~ | ~~路由表非原子 RMW + L1 无失效广播~~ | ~~旧本地缓存读改写路径~~ | **已修复 2026-07-06**：`RedisOnlineRouteService` 改为单脚本 Lua 原子 HASH 双字段（route/heartbeat），直连 Redis，见 `postoffice/ARCH.md` §3 |
| ~~**P1**~~ | ~~ConnectionManager 全局 `synchronized`~~ | ~~`ConnectionManager.java:139,203`~~ | **已修复 2026-07-07**：`registerPendingConnection` / `addConnection` / `removeConnection` 改为 connection/user 分片 `ReentrantLock`，认证提升同时持有 connection + user 分片锁，避免 pending 移除与认证提升竞态 |
| ~~**P1**~~ | ~~踢下线跨节点失效~~ | ~~`KickoffCommandServiceImpl.java:18-40`~~ | **已修复 2026-07-07**：`RouteSnapshot` 增 `sessionId`，`RedisOnlineRouteService` 维护 session 路由索引，`KickoffCommandServiceImpl` 按 gatewayNode 定向发布 `KICKOFF` 节点队列命令，`NodeDeliveryPoller` 本地执行 |
| ~~**P1**~~ | ~~`deliveredMessageKeys` 无界本地 HashSet~~ | ~~`ConnectionManager.java:64`~~ | **已修复 2026-07-06**：`ConnectionManager.markDeliveryIfAbsent` 委托给新抽象 `DeliveryDedupStore`；`RedisDeliveryDedupStore` 用 Redis `SET NX EX` 单原子命令做跨节点去重 + TTL 自动过期，key 形如 `idem:delivery:{serverMsgId}:{userId}:{deviceId|*}`。去掉旧 `ConcurrentHashMap.newKeySet()` 本地 Set，长跑 OOM 与跨节点漏去重双问题同时消除。
| ~~**P1**~~ | ~~默认队列 Chronicle（单机）+ Kafka 路径序列化不兼容~~ | ~~`QueueAutoConfigurer.java:28`、`KafkaQueueAdapter.java:54` vs `MessageProducer.java:27`~~ | **已修复 2026-07-16**：`KafkaQueueAdapter` 反序列化对齐 Chronicle（protobuf 原生 / byte[] 透传），`byteKafkaTemplate` 修复泛型不匹配，DeliveryEventListener 改走 `OfflinePushEventProducer` → `QueueAdapter.send`（详见 `postman/ARCH.md` §7）。多节点通道由 `application-cluster.yml` 启用 Kafka，并必须提供 bootstrap servers、topic 契约以及实例唯一事务 prefix。
| ~~**P2**~~ | ~~历史分页全扫~~ | ~~`HistoryQueryService.java:53-82`~~ | **已修复 2026-07-07**：先定位 latest blockNo，再按 conversationId + blockNo range 窗口读取并裁剪 limit |
| ~~**P2**~~ | ~~权限校验失败放行~~ | ~~`HistoryQueryService.java:213`~~ | **已修复 2026-07-07**：RPC/provider 异常默认拒绝，仅复用未过期本地权限缓存 |
| ~~**P2**~~ | ~~MessageSender 三次同步 Dubbo~~ | ~~`MessageSenderImpl.java:109-123`~~ | **已修复 2026-07-09**：postbox 改为一次 `MessageSendPermissionService.check` 聚合查询，business 本地合并黑名单、用户 receiveOpt、会话 receiveOpt |

### 3.2 容量与正确性隐患

- ~~`MessageIdMappingDoc` 逐条 `save` → 单 Mongo 节点 50k msg/s 写不动。~~ **已修复 2026-07-08**：并入 P1-8 批量写，id mapping 与 message block 各走一个 unordered `bulkOps` upsert。
- ~~`UserMaxSeqPersistenceWriter` / `ReadSeqPersistenceWriter` 单线程 drain + 有界队列超限丢弃。~~ **已修复 2026-07-09**：两个 writer 均改为 userId hash 分桶，多 worker 并行 drain；单桶内仍按 `(userId, conversationId)` 聚合最大水位，Redis/RocksDB 热状态继续承担短期权威值。
- ~~`ConversationVersionLog` 无 TTL 长期增长。~~ **已修复 2026-07-08**：`createdAt` 加 180 天 TTL 索引。
- ~~Kafka 配置在 `common.yml` 中被注释，独立模块启动队列为空。~~ **已修复 2026-07-08**：`application-cluster.yml` 提供 Kafka bootstrap 环境变量配置，cluster profile 默认 `cheeseim.queue.type=kafka`；`common.yml` 的注释样例已删除。
- ~~Mongo 仅 `localhost:27017` 单点，无副本集 profile。~~ **已部分修复 2026-07-08**：cluster profile 改用 `MONGODB_URI` 注入副本集 URI；分片声明仍属 P1-7。
- ~~WS ticket consume 非原子，有重放窗口。~~ **已修复 2026-07-08**：`SessionIssueServiceImpl.consumeWsTicket` 委托 `SessionStateStore.consumeWsTicket`，Redis 后端用 Lua 原子 `GET` + `DEL`，RocksDB dev 后端同步删除后返回，ticket 重放只能成功一次。
- ~~`tokenVersion` 恒 1L，版本号踢下线形同虚设。~~ **已修复 2026-07-08**：access token / WS ticket / session 校验统一比较用户级 `tokenVersion`，`kickoffAll` bump 后旧令牌失效。
- ~~用户封禁仅 Redis 缓存，flush 即解封。~~ **已修复 2026-07-08**：用户安全状态落 Mongo `user_security_state`，Redis 缓存 flush 后可回源恢复。
- ~~群会话解析仍接受旧前缀。~~ **已修复 2026-07-15**：群会话只接受 `g:{groupId}`。
- ~~`OfflinePushServiceImpl` 日计数增量是非原子 read-modify-write。~~ **已修复 2026-07-13**：`PushStateStore` 新增按用户/自然日的 Redis Lua 原子配额预占；发送前 claim，厂商全部失败时 release，计数在次日自动过期，多副本不会并发越过每日上限。

### 3.3 容量上限估算

| 部署 | 估算并发上限 | 主要瓶颈 |
| --- | --- | --- |
| 单节点 all-in-one（Chronicle + injvm Dubbo） | 1-3 万连接 | 单 JVM、Chronicle 单机队列 |
| 单节点 postoffice + Redis + Mongo + Kafka | 10-30 万连接 | 单 JVM IO、Redis 路由/节点队列压力 |
| 多节点 postoffice（当前代码，P0/P1 关键链路已接通） | 50-100 万连接（待长压确认） | Redis 节点队列/路由表压力、Mongo 分片、cluster profile |
| 多节点 postoffice（完成 P1/P2 后） | 百万级目标 | 取决于连接管理分片、存储分片、队列 lag、压测与 chaos 结果 |

---

## 四、与同类开源 IM 对照

| 维度 | CheeseIM | OpenIM | Centrifugo | 现代主流做法 |
| --- | --- | --- | --- | --- |
| 在线路由 | Redis HASH + 真实 gatewayNode + Redis LIST 按节点直投 | Redis + 一致性哈希到 gateway | 内置 broker | gateway 节点 id + 服务组路由，或 per-node topic 直投 |
| 群扩散 | 普通群写扩散，超级群读扩散 | 写扩散+读扩散+fanout worker | N/A | 小群写扩散、大群读扩散（inbox timeline） |
| 消息存储 | 单 collection + 逐条 mapping | MySQL/分片 | 内存/Redis | Mongo sharded + 时间分区 + 冷热分离 |
| 消息队列 | Chronicle 默认；Kafka protobuf bytes 路径已打通 | Kafka | 内置 | Kafka + 分区 + consumer group 并行 |
| 缓存失效 | L1 本地无广播 | Redis-only | 内置 | Redis pub/sub 或 MQTT 广播 L1 失效 |
| 协议 | 控制面无 Protobuf | gRPC 全栈 | WebSocket | gRPC + Protobuf 全栈 |
| 多端策略 | 每节点 10 连接，无全局计数 | 全局在线表 | N/A | 在线表 Lua 维护 `connectionCount` |
| 踢下线 | Dubbo 随机节点 | Redis pub/sub 到 gateway | N/A | per-node topic + 节点订阅 |
| 集群部署 | cluster overlay 已真实装配；七服务 Helm/OCI 仓库基线已落地，真实集群验收待办 | 完整 k8s/helm | 完整 | Sentinel/Cluster + namespace 隔离 |

---

## 五、演进路线（按优先级）

### P0 — 修正"伪集群"（主链路已完成）

1. ~~**节点身份贯通**：`RouteSnapshot.gatewayNode` 写入真实节点 id（Nacos 实例 id 或启动随机 UUID 注册到 Redis）。postman 根据 `gatewayNode` 选择 Dubbo 服务组，或改"每节点专属 topic + 节点订阅"直投。~~ **已完成 2026-07-07**：`NodeIdentityProvider` 提供节点 ID（配置或 UUID）；`ConnectionManager.registerOnlineRoute` 写入真实 nodeId；`NodeDeliveryService` + `RedisNodeDeliveryService` 提供按节点投递抽象；`NodeDeliveryPoller` 在 postoffice 后台 BRPOP 消费 `delivery:node:{nodeId}` Redis LIST 并委托 `OnlineDispatcherImpl` 本地投递；`DeliveryEventListener.deliverToUser` 按 gatewayNode 分组路由。all-in-one 模式下 Redis LIST 路径透明兼容，`NodeDeliveryService` 不可用时降级为直接 Dubbo 调用。
2. ~~**群扩散闭环**：在 `IngressEventListener.handleMessage` 调用 `GroupFanoutPlanner`。普通群（`GroupTypeEnum.NORMAL_GROUP`）走写扩散，产出 N 个 keyed `DeliveryEvent`；超级群（`SUPER_GROUP`）走读扩散，仅持久化 + 客户端按 seq 拉取。~~ **已完成并于 2026-07-19 加固**：NORMAL_GROUP 由独立 fanout worker 按 `membershipVersion` 从 `group_member_epoch` 做稳定 keyset 分页；SUPER_GROUP 保持读扩散。成员当前态、历史 epoch 与群版本由统一 command service 在 cluster Mongo 事务内变更。
3. ~~**路由表原子化**：把 `RedisOnlineRouteService` 的 register/refresh/kick 改写为单脚本 Lua（类比 seq 分配器的工作模式），消除 RMW 竞态。~~ **已完成 2026-07-06**：`register`/`refresh`/`unregister` 走单脚本 Lua；存储改为 Redis HASH 双字段（`route:{deviceId}` JSON + `heartbeat:{deviceId}` 时间戳），不使用通用缓存。
4. ~~**连接管理去全局锁**：`ConnectionManager` 按 `userId hash` 分片到 N 个 `ShardedConnectionManager`，分片锁 + 分片清理线程，连接增删并发提升 N 倍。~~ **已完成 2026-07-07**：保留现有类边界，改为 connection/user 双维度分片 `ReentrantLock`，避免 pending 注册/移除与认证提升之间的全局串行和竞态。
5. ~~**投递去重上 Redis**：`deliveredMessageKeys` 改 Redis 跨节点去重 + 自动过期。~~ **已完成并于 2026-07-19 收紧终态**：`DeliveryDedupStore` 使用单 key claim/commit/abort Lua；key = `idem:delivery:{deliveryId}:{userId}:{deviceId|*}`。只有 ChannelFuture 成功后才 commit，失败 abort，超时由有界 finalizer 异步收口。
6. ~~**修复 Kafka 路径**：统一 Protobuf 序列化器（Producer/Consumer 一致），把 `DeliveryEventListener.emitOfflinePushIfNeeded` 中的 `kafkaTemplate.send` 直调改回 `QueueAdapter`。~~ **已完成 2026-07-16**：新增 `OfflinePushEventProducer`（postman），`DeliveryEventListener.emitOfflinePushIfNeeded` 改走 `offlinePushProducer.publish` → `QueueAdapter.send(OFFLINE_PUSH, …)`；`KafkaQueueAdapter.subscribe/subscribeKeyed` 反序列化对齐 `ChronicleQueueAdapter.deserialize`；Kafka 发送等待 broker ACK，batch 使用事务原子提交，失败消费进入有界重试与 DLT。Chronicle 仍是单机文件后端，batch 逐条 append 不提供 Kafka 事务的整批原子性；两者只保证 payload 和消费契约对齐，不宣称可靠性语义完全相同。

### P1 — 存储与索引（4-8 周）

7. **Mongo 副本集 + 分片**：**仓库侧 migration 已完成、生产验收待办 2026-07-19**。现已真实补齐
   `distro/mongo/enable-im-sharding.js`，并先修正 message block/mapping/epoch upsert 的 shard-key 查询；
   脚本安全启用 `message_block(conversationId hashed)`、`message_id_mapping(serverMsgId hashed)`、
   `group_member_epoch(groupId range)`、`group_fanout_job(_id hashed)`；新增
   `conversation_delivery_preference` 消除 conversationId 反向全扫，migration 回填后再启用
   `conversation_delivery_preference(conversationId,ownerUserId)` 与
   `conversation(ownerUserId,conversationId)`。真实 mongos smoke、备份恢复与其余集合审计仍未完成。
8. ~~**历史块批量写**：`BlockHistoryPersistenceService` 收批后 unordered bulk insert。~~ **已完成 2026-07-08**：一个 `HistoryEvent` 内 id mapping 与按 blockNo 分桶的块更新各合入一个 unordered `bulkOps` upsert（`_id` 幂等，重放安全）；删除循环逐条 `mappingRepository.save`，`MessageIdMappingRepository` 无使用点一并删除。
9. ~~**历史分页改 blockNo range**：`getConversationMessages` 改为按 block 二分定位起始块再顺序读，避免全扫。~~ **已完成 2026-07-07**：按 latest blockNo + range 窗口读取，`message_block` 增 `conversationId + blockNo` 复合索引。
10. ~~**附件查询去 regex**：`BlockMessageQueryService.findAttachmentCandidates` 的 `content.regex` 改为附件元数据表（`_id=attachmentId`）。~~ **已完成 2026-07-08**：新增 `attachment_metadata` 集合（`_id=attachmentId`），postmaster 历史持久化时对 `ContentType.hasAttachment()`（IMAGE/VOICE/VIDEO/FILE）消息从 content JSON 提取 `attachmentId` 批量 upsert；postbox `findAttachmentCandidate` 改为点查。原 regex 查询的 `content` 字段在 `message_id_mapping` 上并不存在，属死查询。
11. ~~**权限校验失败拒绝**：`HistoryQueryService.allow` 在异常时 `return false`，加本地兜底缓存降级。~~ **已完成 2026-07-07**：RPC 异常/provider 缺失默认拒绝，仅使用 30s 本地权限缓存兜底。
12. ~~**`ConversationVersionLog` TTL 索引**：`createdAt` 加 `expireAfterSeconds`，或加定期 compact job。~~ **已完成 2026-07-08**：`ConversationVersionLogDoc.createdAt` 声明 `@Indexed(expireAfterSeconds = 180 天)`，避免版本日志长期无界增长。
13. ~~**read-seq 写并发化**：`ReadSeqPersistenceWriter` 改按 `userId` 分桶的多线程 drain 或走 Kafka 通道。~~ **已完成 2026-07-09**：`ReadSeqPersistenceWriter` 与同类 `UserMaxSeqPersistenceWriter` 均改为 userId hash 分桶多线程 drain，shutdown drain 全部分桶，单桶内聚合最大水位避免乱序回退。

### P2 — 链路性能（8-16 周）

14. ~~**MessageSender 权限链合并**：黑名单/用户 receiveOpt/会话 receiveOpt 合并为单个 Dubbo `PermissionAggregate` 一次性返回；本地 Caffeine + 异步刷新。~~ **已完成 2026-07-09**：新增 `MessageSendPermissionService` 聚合契约，business 本地复用 friend/user/conversation 服务与缓存，postbox 发送热路径只保留一次同步 Dubbo。
15. ~~**Ingress batch 内批量 seq + 批量 Mongo upsert + 批量 delivery publish**。~~ **已完成 2026-07-13**：seq 已按会话整批申请，历史块与 id mapping 已走 unordered bulk upsert；本次新增 `QueueAdapter.sendBatch`，非群消息按 ingress 批次提交，普通群按 groupId 聚合后一次查询群类型/成员，复用 protobuf 模板向成员切片批量发布，消除逐消息重复 Dubbo 查询与完整序列化。
16. **postoffice 单机 C100K+**：**线程隔离与背压已完成 2026-07-13**：新增 `BusinessMessageExecutor`，按 connection hash 路由到有界单线程分片，保证同连接命令顺序；TCP/WS 的 Dubbo/Redis 业务处理不再占用 Netty EventLoop，队列满立即返回 503。`ConnectedChannel`/连接对象池化仍待长压数据证明收益后再决定，避免提前引入对象生命周期复杂度。
17. ~~**路由表 L1 加 pub/sub 失效广播**。~~ **不再适用**：P0-3 已将在线路由迁为 `RedisOnlineRouteService` 直连 Redis HASH，当前链路不存在路由 L1，也就没有跨节点失效窗口；不要为完成旧路线重新引入本地缓存。

### P3 — 功能补齐

18. **协议补全**：~~在 `message_protocol.proto` 为 `CHAT_READ/CHAT_REVOKE/FORCE_LOGOUT` 增加类型化 payload；~~ **已完成 2026-07-11**：新增 typed command/notify payload 并写入 client/server envelope oneof，Java/Go 生成产物同步。conversation sync / friend / group 控制面仍待 Protobuf 表达。已读/撤回的产品与架构决策见 `server/docs/architecture/read-revoke-design.md`。
19. ~~**WS 协议统一为 Protobuf**。~~ **已完成 2026-07-13**：入站、同步响应、异步聊天/控制通知全部使用 Binary WebSocket Frame + typed protobuf envelope，与 TCP 一致。
20. ~~**已读回执链路**。~~ **已完成 2026-07-16**：TCP/二进制 WS/HTTP 统一调用 `ReadStateService`，完成可见性校验、maxSeq 截断、Redis Lua 原子单调推进 readSeq/未读数与 Mongo write-behind；同步写入 `ConversationVersionLog.READ_STATE_UPDATED`，增量同步返回变化会话 ID，离线设备据此刷新 read snapshot。结果先追加至 `conversation_control_event` 分片 outbox，再经 postman `ControlNotificationDispatcher` 按 gatewayNode 推送 typed notify：单聊通知 peer 与阅读者在线端，群聊仅阅读者在线端。
21. **消息撤回/编辑与富媒体**：**撤回闭环已完成 2026-07-14**：`MessageMutationService` 按 serverMsgId 点查服务端 mapping，校验 conversation/发送者/服务端持久化时间两分钟窗口，以 `{serverMsgId}:REVOKED` 原子 upsert mutation；TCP/WS/HTTP 统一返回结果；历史页和 gap repair 批量 merge tombstone。新增按 `createdAt + mutationId` 稳定复合游标的 mutation 增量同步、群成员权限校验和 HTTP 拉取入口；撤回控制通知同样追加至 `conversation_control_event` outbox 后再直推，单聊双方、普通群在线成员按 gatewayNode 推送，超级群仍以 mutation 增量同步收敛。消息编辑与上传 token 服务仍待完成。

控制事件基础设施于 2026-07-16 完成热点与重入治理：已读、撤回写入 `conversation_control_event`，cursor 计数器按 userId 固定映射为 64 个 Mongo 文档，复合 long cursor 对单用户保持单调且从旧 global cursor 之后继续；目标列表先按 cursor 分片、再按每事件 200 人上限拆分，分片 eventId 由业务稳定 ID + shard + 目标摘要确定，并以 `$setOnInsert` 幂等 upsert，中途失败重入只补缺失分片。claimable 查询按 shard 轮转扫描并与 PENDING/过期 CLAIMED 复合索引对齐，避免已完成 backlog 的全局阻塞排序。离线或超过补偿上限的客户端仍通过 `/api/im/conversations/control-events` 保存单个用户专属 cursor 补拉，无需修改协议。控制事件不触发离线厂商推送。输入中已从可靠 outbox 拆除：仅走 `CHAT_TYPING`，Redis 按 sender + conversation 用 `SET NX EX` 保存 3-5 秒瞬时状态并节流，只尽力通知在线目标；普通群有可配置成员上限，超级群禁用，不进入 Mongo outbox、普通消息 ingress、历史或会话 seq。

### P4 — 运维与一致性

设备送达状态于 2026-07-16 完成服务端闭环：新增 typed `CHAT_DELIVERY(37)`，客户端按设备/会话提交 `maxDeliveredSeq + deviceId + opId`，`DeliveryStateService` 校验会话可见上界后用 Redis Lua 单调推进，并通过有界批量 write-behind 以 Mongo `$max` 持久化；单聊只向发送方通知 delivery 高水位。`CHAT_SEND_ACK` 明确增加 `acceptedAt + BROKER_ACCEPTED`，仅表示 broker 已可靠接收；postoffice 的 channel write 成功不再被解释为客户端送达。客户端仍需由 Go SDK/CheeseBox 基于同一 proto 生成代码并在本地消息落盘后发送 ACK。

22. ~~**限流/幂等**：api-server 入口 RateLimiter + Redis SETNX 幂等 key。~~ **已完成并于 2026-07-19 修复代码漂移**：Redis Lua 固定窗口 RateLimiter 按来源地址散列后的 key 在多副本间共享计数，超额返回 HTTP 429；默认不信任 X-Forwarded-For，Redis 故障以节点本地短熔断 fail-open，避免每请求等待 timeout。携带 `Idempotency-Key` 的鉴权 POST/PUT/DELETE 以 `userId + method + path + key 指纹` 做 Redis SETNX，重复请求返回 HTTP 409。当前不缓存首次响应，客户端需要响应重放时需另行设计状态/响应缓存协议。
23. ~~**集群部署 profile**：新增 `application-cluster.yml`，含 Redis Sentinel/Cluster、Mongo replica URI、Kafka bootstrap、Nacos namespace 分离；删除 `common.yml` 中注释掉的 Redis/Kafka。~~ **已完成并于 2026-07-19 修正装配**：`application-cluster.yml` 作为分模块 profile overlay，默认 `cheeseim.queue.type=kafka` 与 `cheeseim.conversation-seq.deployment-mode=cluster`；`MONGODB_URI`、`KAFKA_BOOTSTRAP_SERVERS`、`NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`REDIS_SENTINEL_*` / `REDIS_CLUSTER_*` 均走环境变量，不向 cluster 默认写入 localhost。由于启动类使用自定义 `spring.config.name`，现由每个 `application-{module}.yml` 显式 import 带 `on-profile: cluster` 的 overlay，修复此前文件存在但不会自动加载的漂移。
24. ~~**多副本一致性**：`MessagePushServiceImpl.attempts/deliveryStates` 迁到 Redis；~~`tokenVersion` 真正 bump；ban 标志落 Mongo 持久化~~。~~ **已完成 2026-07-11**：新增 `PushStateStore` / `RedisPushStateStore`，以每个 serverMsgId 一个 Redis HASH 存 attempt 与 per-user delivery state；Lua 在单 key 内原子拒绝 `ONLINE_CONFIRMED`/`READ` 或既有 attempt，只有一个 postman 副本可取得离线推送 claim。状态默认 TTL 24h，可由 `CHEESEIM_PUSH_STATE_TTL_SECONDS` 调整。

25. ~~**节点投递结果与离线补偿**：不能把节点队列受理当作用户在线投递成功；dead/超时必须形成可消费结果。~~
    **已完成 2026-07-19**：`RouteSnapshot.deliveryOutcomeVersion` 支持滚动升级协商；postoffice 在节点
    processing ACK 前发布 `DELIVERY_OUTCOME`，dead 前发布最终失败；postman 以 `(deliveryId,userId)`
    的 Redis attempt 幂等聚合冻结节点，任一成功结束、全部失败或 deadline 到期触发离线推送。
    attempt 与 64 分片 deadline ZSET 同槽，离线发布使用可恢复租约并在 broker ACK 后完成。
26. ~~**心跳写放大治理**。~~ **已完成 2026-07-19**。已认证连接新增默认
    60 秒本地 session 复核租约，主动撤销仍走 kickoff；租约到期只调用一次已包含
    active/ban/tokenVersion 的 `isSessionValid`，删除每次心跳的第二次重复 RPC，并关闭 Dubbo
    框架重试。路由心跳按 connection 在节点本地合并，默认 60 秒一次，两阶段 pipeline 先刷新仍匹配
    connectionId 的用户主路由、再刷新 session 反向索引；注销同样改为 Redis compare-and-delete，
    关闭旧连接删新路由竞态。
27. ~~**普通群 fanout worker 化**。~~ **已完成 2026-07-19**：ingress 只发布按 groupId 分区的
    `GROUP_FANOUT` 紧凑任务；独立 consumer 查询成员、创建首会话、切片投递并推进用户水位。
    job 重放复用 serverMsgId 投递去重与 maxSeq 单调 Lua。严格 membership snapshot 仍需后续
    group version/as-of 能力。
28. ~~**用户 maxSeq writer bulk `$max`**。~~ **已完成 2026-07-19**：
    `UserMaxSeqPersistenceWriter` 聚合后调用一次 Mongo unordered bulk upsert；单条与批量 maxSeq
    均使用 `$max`，跨副本及 fallback 乱序不再回退。
29. ~~**登录可信身份源**。~~ **已完成 2026-07-19**：登录主体只来自可信业务系统短期签名
    assertion 的 sub；校验 issuer/audience/iat/exp/jti，Redis NX 一次性消费 jti，默认关闭即拒绝，
    userId 仅作一致性校验。账户域签发端与 Go SDK assertion 交换仍是部署前置条件。
30. ~~**网关节点连接准入与写背压**。~~ **已完成 2026-07-19**：TCP/WS 共用 CAS 节点总连接预算，
    pending 阶段即拒绝超限；单用户节点上限与 multi-login 配置真实生效。两种 transport 设置 Netty
    write-buffer watermark，统一写入口拒绝 unwritable channel 并沿用 delivery retry/补偿。
    跨节点策略由下一项全局 login lease 接管。
31. ~~**跨节点全局多端登录 lease**。~~ **已完成 2026-07-19（rollout-controlled）**：
    tenant/user 同槽 active ZSET + metadata HASH 由 Lua 原子执行四策略、全局上限和 generation fencing；
    服务端主动批量续租，旧 generation 无法 renew/release/误踢新连接。默认关闭，必须全节点升级并 drain
    旧连接后启用；真实 Redis Cluster 并发/故障测试仍待执行。
32. ~~**WS ticket 用 Lua 原子 consume**。~~ **已完成 2026-07-08**：`SessionStateStore` 增加原子 consume 契约，Redis/RocksDB 两种后端均实现一次性消费。
33. **Mongo 副本 + 查询 secondary 读偏好** 读写分离。
34. **指标分级**：已有 actuator + Prometheus，补齐 per-conversation seq lag、queue lag、online route hit/miss、push attempt counter。Grafana dashboard 模板。
35. **集群 chaos + 1M 长压脚本**：**工具与门槛已落地 2026-07-16，百万规模仍待实测**。`server/perf/k6/ws-im.js` 提供 auth/heartbeat/chat-send/read/delivery-ack 二进制 Protobuf WS 场景，连接数、ramp、消息速率均可配置；`verify-summary.mjs` 核对 broker ACK、唯一接收、重复与 delivery ACK。`server/perf/chaos/run-chaos.sh` 以显式 start/heal 命令覆盖 postoffice 重启、Redis 短断、Kafka broker 不可用、Mongo primary stepdown，并默认 dry-run。当前只完成语法/smoke 入口，50-100 万及 1M 结论必须等待分布式负载机长压和 chaos 证据，不能从代码或小规模结果外推。
36. **Kafka DLT 受控运维闭环**：**仓库侧已完成 2026-07-19，真实 Kafka 验收待办**。
    新增无 HTTP/Dubbo 的 `ops-cli`；查询以 assign + seek 读取固定分区摘要，不加入 group、不提交 offset、
    不输出 payload。redrive 每次只复制一条精确 `(topic,partition,offset,checksum)`，要求 operator/reason，
    以 Mongo `dlt_redrive_audit` 的 operationId lease 审计并等待 broker ACK。原 DLT 不删除，重放使用新
    CreateTime。当前仍需真实 broker 故障、ACK 后进程崩溃与告警规则验收。
37. **生产 OCI 镜像**：**仓库侧已完成 2026-07-19，镜像引擎验收待办**。统一 Dockerfile
    只允许七个生产服务，构建 Spring Boot 分层镜像，以非 root UID 10001 运行并默认 cluster profile。
    Kubernetes 探针、drain、PDB、NetworkPolicy 已由第 42 项补齐仓库模板；基础镜像 digest、
    SBOM/签名/扫描及真实镜像构建仍未完成。
38. **共享依赖去外溢**：**第一阶段已完成 2026-07-19**。common-core 不再通过
    `spring-boot-starter-web` 和 Prometheus registry 让所有服务隐式携带 servlet server/exporter，
    仅保留实际使用的 `spring-web` 客户端与 micrometer-core。Kafka/Chronicle、Mongo、Redis/RocksDB
    adapter 已分别由第 49、48/51、52 项物理迁出 common-core。
39. **显式管理面与健康语义**：**仓库侧已完成 2026-07-19，运行环境验收待办**。七个独立服务
    各自声明 Actuator、Prometheus 和 Web runtime；worker/Dubbo 服务关闭无意义业务 HTTP，
    只开放独立 19080–19085 管理端口。liveness 只看进程，readiness 纳入模块 Mongo/Redis，
    健康详情默认隐藏并启用 graceful shutdown。Kafka AdminClient health contributor、
    NetworkPolicy 与真实 kubelet probe 尚未完成。
40. **独立 API 生产入口**：**仓库侧已完成 2026-07-19，运行环境验收待办**。api-server 新增
    独立 Spring Boot/Dubbo consumer 入口、18079 业务端口、19079 管理端口和生产镜像；
    只扫描 HTTP adapter，并以 non-transitive common-core/infra-state 依赖分别获得幂等 port/Redis adapter；
    `cheeseim.state.auto-config-enabled=false` 阻止完整状态运行时装配。
    其 74.4 MiB bootJar 不含 Mongo/Kafka/Chronicle/RocksDB。all-in-one 显式排除该启动配置，
    保留原本嵌入式 controller 模式。
41. **API 限流实现复原**：**仓库侧已完成 2026-07-19，编译/装配验收通过 2026-07-24，真实 Redis 环境验收待办**。修复文档声称完成、
    但源码只剩 Redis key 常量的漂移；新增 `/api/**` 多副本 Lua 固定窗口、429/Retry-After、
    来源地址指纹、显式可信代理跳数和 Redis 故障短熔断 fail-open 指标。边缘连接/带宽/DDoS
    防护仍是生产前置条件。
42. **七服务 Helm 工作负载基线**：**仓库侧已完成 2026-07-19，原生渲染/集群验收待办**。
    新增七服务独立 Secret、资源 request/limit、startup/liveness/readiness、graceful preStop、
    read-only/non-root 安全上下文、PDB、双 topology spread 和默认 ingress NetworkPolicy。
    postoffice 使用 StatefulSet 稳定 nodeId，其余使用 Deployment。HPA/KEDA、ServiceMonitor、
    egress policy、Helm lint/template、server-side dry-run 和真实滚动发布尚未完成。
43. **Kafka topic DDL/校验解耦**：**仓库侧已完成 2026-07-19，真实 broker 验收待办**。
    cluster/ops 默认不再自动创建 topic，业务 Pod 可去除 DDL 权限；启动仍强校验十二个主/DLT topic。
    migration 脚本从 TopicNames 发现六个主 topic，并按 12 分区、3 副本、minISR 2、7 天 retention
    同时创建 DLT，修复旧脚本解析失败、遗漏 DLT 和 3×1 demo 默认值。
44. **分级灾备与恢复契约**：**仓库侧 Runbook 已完成 2026-07-19，真实演练待办**。
    `docs/disaster-recovery.md` 明确 Mongo 是持久事实源，跨时间点恢复必须使用空 Redis/新 namespace；
    禁止把旧 seq `CURR/LAST`、route、lease、session 和 node queue 与新 Mongo/Kafka 时间线拼接。
    文档给出 PITR、Kafka offset、七服务灰度恢复、RPO/RTO 与季度证据清单。Mongo 分片 restore、
    Kafka 跨区域复制、对象存储灾备和实际 RPO/RTO 仍未落地。
45. **write-behind RPO 可观测与停机 drain**：**仓库侧已完成 2026-07-19，编译/演练待办**。
    read/max/delivery writer 上报 queued/inflight depth 与动态 oldest age，Mongo 调用卡死后 age 仍增长；
    停机先以共享 30 秒预算等待已取出批次，再 drain 队列，timeout/interrupted/drop 使用固定指标结果。
    business/postmaster Helm grace 提高到 120 秒。阈值、Mongo timeout 与最坏 backlog 仍需发布演练校准。
46. **Prometheus Operator 交付闭环**：**仓库侧已完成 2026-07-19，原生 Helm/集群验收待办**。
    Chart 可选生成只选择普通管理 Service 的 ServiceMonitor，以及 namespace-scoped writer oldest-age/
    persistence-failure PrometheusRule；默认关闭避免缺 CRD 集群安装失败，阈值关系启动时校验。
    Prometheus selector labels、NetworkPolicy 和告警路由仍需按真实集群 values 验收。
47. **history port 去 Mongo Document 泄漏**：**仓库侧已完成 2026-07-21，编译/装配验收通过 2026-07-24**。
    新增无 Spring Data/BSON import 的 history model，`MessageHistoryRepository` 不再返回 `*Doc`；
    postbox/postmaster 历史查询与撤回服务只依赖 port model。Mongo adapter 负责 Document 转换并将
    BSON Binary 规范为 `byte[]`。Mongo adapter 的物理迁移已由第 48 项完成。
48. **storage-history 物理模块拆分**：**仓库侧已完成 2026-07-21，编译/装配验收通过 2026-07-24**。
    历史 Mongo adapter、四类 Document 与自动配置迁入独立 library；仅 postbox/postmaster 显式依赖，
    feature 不再直接声明 Mongo starter。common-core 保留 port/model，构建门禁阻止 Document/BSON 回流与
    common-core 反向依赖。common-core 其余 business Mongo 与 Redis/RocksDB adapter 已由第 51/52 项迁出。
49. **infra-queue 物理模块拆分**：**仓库侧已完成 2026-07-21，编译/装配验收通过 2026-07-24**。
    `QueueAdapter`、监听注解和 DLT port 留在 common-core；Kafka/Chronicle adapter、listener runtime、
    byte producer、topic 校验与 Kafka DLT 实现迁入独立 library。实际生产/消费队列的 feature 显式依赖
    infra-queue，但源码只 import port。自动配置按 queue type 隔离：Chronicle 模式不再创建 Kafka
    producer/admin。根构建门禁阻止 common-core/feature 回流实现依赖。
50. **队列订阅生命周期**：**仓库侧已完成 2026-07-21，编译/生命周期测试通过 2026-07-24**。listener runtime 集中持有
    `QueueAdapter.subscribe*` 返回的 Subscription，Spring context 关闭时逆序释放；Chronicle poller 从立即
    `shutdownNow` 改为最多 30 秒协作式 drain 后再中断，Kafka container 统一经 Subscription.stop 关闭。
51. **storage-business 物理模块拆分**：**仓库侧已完成 2026-07-21，编译/装配验收通过 2026-07-24**。
    业务 Mongo Document、39 个 adapter/config 源文件与相应测试迁入独立 library；common-core 只保留
    Repository/store port 与事务抽象，并移除 Mongo starter。authcenter/business/postmaster/postman/ops-cli
    显式依赖 adapter，自动配置只在 MongoTemplate 存在时生效。DLT operations 改为显式 enabled，仅 ops-cli 开启。
52. **infra-state 物理模块拆分**：**仓库侧已完成 2026-07-21，编译/装配验收通过 2026-07-24**。
    Redis/RocksDB state、typed cache、message/ingress inbox、refresh family、conversation seq cache、节点队列 Lua
    与三类自动配置迁入独立 library；common-core 只保留 port/model/allocator，并删除 Redis/RocksDB 依赖。
    api-server 以 non-transitive 依赖只显式构造 RedisIdempotencyStore，关闭完整状态自动配置，避免 RocksDB/其他
    state Bean 回流。构建门禁阻止技术依赖与 adapter import 回流。

---

## 六、值得继续升级的好设计

1. **邮政隐喻模块边界**：postoffice/postbox/postmaster/postman 职责切分清晰，可继续演进为 DDD bounded context + 各自独立容器部署。
2. **会话 seq 分配器**：Lua + Mongo `$inc` + 段预分配 + LOCK 状态机 + 启动强校验，**整个项目最接近生产级的代码**，可作为其它分布式计数器（消息 id、通知 id、批次 id）的模板推广。
3. **消息策略引擎 `DefaultMessagePolicyEngine`**：`needHistory/needOnline/needOffline/senderSync/notification` 集中决策，可升级为 DB 配置 + 热更新 + DSL 规则表。
4. **`MessageOptions` 八位 bool 逐消息控制**：类似 OpenIM `Options`，可升级为 protobuf bitmask 节省字段。
5. **`ConversationIdUtil` 规范化 id 体系**：`s:/g:/n:/ng:` + 队列 key 一致，便于分片与幂等；可升级为 Snowflake 全数字 id。
6. **5 厂商真实 push 集成**：APNs/FCM/Huawei/Xiaomi/JPush 全 lifecycle，gated 开关——业内少有一开始就做这么齐的开源项目。可升级为通道降级矩阵 + 模板 + 回执上报。
7. **CacheStore + after-commit eviction**：缓存值以显式 JSON 类型写入 Redis，事务提交后再失效；不默认启用本地缓存。
8. **api-server 薄 Controller + Facade**：DTO 不下沉约束严格，可升级为 GraphQL/BFF 子图拆分。
9. **all-in-one 单 JVM 联调**：injvm Dubbo + Chronicle，开发体验远超微服务拉起；保留同时加 `cluster` profile。
10. **`ConversationVersionLog` 增量同步**：服务器侧 version-log 是主流 IM 同步模型（OpenIM/微信 MMKV 思路一致），可升级为按 `ownerUserId` 分片的独立同步服务。

---

## 七、一句话方向

**短期继续补 MessageIdMapping 批量写、多副本推送状态迁移与长压验证，中长期把存储分片化 + 控制面 Protobuf 化 + 多副本一致性补齐**，架构骨架本身已够支撑百万级演进，不需要推倒重写。

---

## 八、评估维护规则

- 每次重大架构改动后，作者需更新本文"二、已实现能力"和"三、阻断性问题"。
- 评估证据需带 `file:line`，避免无依据断言。
- 超过 6 个月未更新，本文标记为 `可能过时`，需重新评估。
- 与代码冲突时以代码为准，并在本文底部追加"勘误记录"。

## 九、勘误记录

- 2026-07-19：B-01 Redis Cluster 同槽约束已收口。节点 ready/processing/lease/dead key 使用同一安全编码 node hash tag；同一 `(userId, conversationId)` 的 read/max/unread key 使用同一 `uc` tag，两个 `KEYS[2+]` Lua 状态机均满足同槽。跨任意会话/缓存 key 的 MGET 与多 key DEL 改为按 key pipeline，避免 cluster `CROSSSLOT`。旧 `uc:*` 热状态和无 tag 节点队列不做在线双写迁移，首次启用本版本 cluster profile 前必须停写、排空旧节点队列并按 Mongo 真相重建热状态。
- 2026-07-06：P0-3 路由表原子化已完成。`RedisOnlineRouteService` 重写为基于 `StringRedisTemplate` + 单脚本 Lua，存储改为 Redis HASH 双字段（`route:{deviceId}` JSON + `heartbeat:{deviceId}` 时间戳），消除旧读改写竞态与 L1 无广播不一致。`OnlineRouteService` 接口未变，`HeartbeatMessageHandler` / `ConnectionManager` 调用方零改动。原 ASSESSMENT 「P0-3」表格项已划除，演进路线 P0 §3 已划除并标注完成日期。
- 2026-07-06：P0-2 群扩散闭环已完成。`GroupMembershipQueryService` Dubbo 契约新增 `queryGroupType(groupId)`，business 实现侧复用 `GroupRepository` 读群资料并映射 `GroupTypeEnum`；`GroupMembershipFacade` 增 `loadGroupType` 包装；`GroupFanoutPlanner` 新增 `deliveryKey(groupId, memberId)` 生成 `g:{groupId}:{memberId}` 形式 partition key；`IngressEventListener` 新增 `fanoutGroupDelivery` 分流 NORMAL_GROUP（写扩散按成员切片 publish N 份 keyed DeliveryEvent）/ SUPER_GROUP（读扩散仅持久化）/ null（兜底 NORMAL）；`MessageProducer.publishForMember` 通过 protobuf builder 替换 `receiverId`，避免 Java 侧深拷贝 `Message`；postman `DeliveryEventListener.resolveTargets` 去除 `ChatType.GROUP` 跳过分支，直接按 `receiverId` 投递（写扩散后每条 DeliveryEvent 已携带具体 memberId）。injvm 部署原生兼容，调用方零改动。
- 2026-07-06：P0-5 投递去重上 Redis初版完成；**2026-07-19 A-06 进一步改为 claim/commit/abort 状态机**。`ConnectionManager.writeMessageToConnection` 返回真实 ChannelFuture，`OnlineDispatcherImpl` 仅在 future 成功后 commit；超时由 `DeliveryWriteFinalizer` 在 EventLoop 外异步收口。节点队列按每个连接结果决定 ACK/重试，不再任一成功即 ACK 全部。
- 2026-07-06 review 跟进项（未阻断，留作后续）：
  - ~~性能：ingress 批内逐消息查询群类型和全量成员。~~ **已修复 2026-07-19**：同群批量权限校验返回 groupType + membershipVersion，ingress 只发布紧凑 job；成员枚举在独立 worker 分页执行。剩余风险是 job 尚无分页 checkpoint，且 event messages 尚未按 broker 字节上限拆分。
  - 可用性：P0-3 + P0-5 让 Redis 成为在线投递链路的硬依赖（路由表 + 去重）。当前 Redis claim 异常明确返回 `DEDUP_UNAVAILABLE` 并进入节点重试，不再伪装重复或成功；策略选择 side-effect-safe，但 Redis 长时间故障会停止在线投递，必须用告警、dead redrive 和离线补偿闭环而非 fail-open 制造重复。
  - 命名：`ConnectionManager.markDeliveryIfAbsent` 第三参 `deviceId` 实为 `connectionId`（per-connection 粒度）。新接口 `DeliveryDedupStore` docstring 已明示此历史遗留，未重命名以免破坏既有调用方。
  - 死代码：`GroupMembershipFacade.loadDeliveryTargets`（无人调用）已删除。
- 2026-07-07：P0-6 + P1-6（Kafka 路径修复）已完成，并于 2026-07-21 将运行时实现迁入 `infra-queue`。`DeliveryEventListener.emitOfflinePushIfNeeded` 去除对 `KafkaTemplate` 的直连，改为调用新增的 `OfflinePushEventProducer`（postman/sender/），后者通过 `QueueAdapter.send(OFFLINE_PUSH, userId, bytes)` 投递；Chronicle / Kafka 两种 `cheeseim.queue.type` 后端同走抽象路径，离线推送在单机联调模式（Chronicle）下也可用。`KafkaQueueAdapter.subscribe/subscribeKeyed` 反序列化对齐 `ChronicleQueueAdapter.deserialize`：`byte[]` 透传、protobuf 原生解析、其它类型 Jackson 兜底。`KafkaQueueConfiguration` 提供 byte[] producer，由 `QueueInfrastructureAutoConfiguration` 注入；未接入的对象/String Kafka 模板、通用序列化器与 `@EnableKafka` 监听路径已删除，所有 feature 消息统一经 `QueueAdapter`。
- 2026-07-07：P0-1 跨节点在线投递已完成。`NodeIdentityProvider` 提供节点唯一 ID（配置或自动 UUID）；`ConnectionManager.registerOnlineRoute` 写入真实 nodeId 替代硬编码 `"postoffice"`；`NodeDeliveryService` 接口（common-api）+ `RedisNodeDeliveryService` 实现（postman）提供按节点投递抽象，通过 `StringRedisTemplate` LPUSH 到 `delivery:node:{gatewayNode}` Redis LIST；`NodeDeliveryPoller`（postoffice）后台 daemon 线程 BRPOP 消费并委托 `OnlineDispatcherImpl` 本地投递；`DeliveryEventListener.deliverToUser` 按 gatewayNode 分组路由，`NodeDeliveryService` 不可用时降级为直接 Dubbo 调用。新增 Redis key `delivery:node:{nodeId}`（`RedisKeys.deliveryNodeQueue`）。all-in-one 模式透明兼容（单 JVM 共享 Redis LIST）。原 ASSESSMENT P0-1 表格项已划除，演进路线 P0 §1 已划除并标注完成日期。
- 2026-07-07：P1 ConnectionManager 去全局锁与跨节点踢下线已完成。`ConnectionManager` 将 pending 注册、认证提升、移除连接从方法级 `synchronized` 改为 connection/user 分片 `ReentrantLock`；认证提升和移除都按 connection → user 顺序加锁，避免断线移除 pending 与认证提升并发交错。`RouteSnapshot` 增加 `sessionId`，`RedisOnlineRouteService` 维护 `online:session:v1:{sessionId}` HASH 辅助索引，同一 session 可保存多条 `userId:deviceId` 路由；`KickoffCommandServiceImpl` 按 user/device/session 查询 `gatewayNode`，本节点直接踢线，远端节点通过 `NodeCommandPublisher` 入队 `NodeQueueMessage(KICKOFF)`，`NodeDeliveryPoller` 消费后委托 `ConnectionManager` 本地踢线；旧裸 `DispatchMessageReq` JSON 保持兼容。
- 2026-07-07：P1/P2 HistoryQuery fail-closed 与分页改造已完成。`HistoryQueryService.allow` 在 provider 缺失、RPC 异常、非预期返回时默认拒绝，只复用 30s 未过期本地权限缓存；`getConversationMessages` 不再拉全量 block，而是先查 latest `blockNo`，按 `conversationId + blockNo range` 窗口读取并按 seq 倒序裁剪；`limit` 钳制到 200，最近页最多扫描 16 个窗口，避免恶意大 limit 或稀疏 block 退化；`MessageBlockDoc` 增加 `idx_message_block_conversation_block` 复合索引。
- 2026-07-08：P1-8 历史块批量写已完成。`BlockHistoryPersistenceService.persist` 在一个 `HistoryEvent` 内先把全部 `MessageIdMappingDoc` upsert 合入一个 unordered `bulkOps`，再把按 blockNo 分桶的块更新合入第二个 unordered `bulkOps`，替代原「循环里逐条 `mappingRepository.save` + 逐块 `mongoTemplate.upsert`」；两类文档 `_id` 均确定性拼接（`{convId}:{clientMsgId}` / `{convId}:{blockNo}`），队列重放幂等。`MessageIdMappingRepository` 已无使用点，删除（`findByServerMsgId` 若撤回链路需要按 P3-21 再引入）。
- 2026-07-08：P1-10 附件查询去 regex 已完成。新增 `attachment_metadata` 集合（`_id=attachmentId`，含 conversationId/serverMsgId/clientMsgId/seq/senderId/contentType/sendTime）：postmaster `BlockHistoryPersistenceService.persistAttachmentMetadata` 对 `ContentType.hasAttachment()` 消息从 content JSON 提取 `attachmentId`（非 JSON/缺字段静默跳过）随历史持久化批量 upsert；`ContentType` 新增 `hasAttachment()`（IMAGE/VOICE/VIDEO/FILE）。postbox `BlockMessageQueryService.findAttachmentCandidate(attachmentId)` 改为 `findById` 点查后 `findSlot` 还原内容，返回 `Optional`；原 `findAttachmentCandidates` 的 `content.regex` 扫的是 `message_id_mapping` 上不存在的 `content` 字段，本就是死查询。同时修复 `findSlot` blockNo 公式与 `BlockIndexUtil` 差一的 bug（改按 `docId` 点查 `_id`），并删除 postbox 内 7 个引用已删架构（`IngressEvent`/postbox 侧持久化/附件 token 服务等）且已无法编译的 stale 测试，postbox 测试基线恢复可编译。
- 2026-07-08：修复 `DefaultMessagePolicyEngine.persistHistory` 取错字段的正确性 bug——原实现读 `options.getNotification()` 而非 `options.getNeedHistory()`，导致 `needHistory=false` 的消息（如已读回执）被错误分配 seq 并持久化、而显式 `notification=false` 的消息被错误跳过历史。`IngressEventListenerTest` 中 3 个既有失败测试（read receipt transient / 首条会话创建 ×2）即此 bug 与 `conversationService` 字段注入不可测所致；后者补包级测试构造器注入（生产路径仍 `@DubboReference`）。
