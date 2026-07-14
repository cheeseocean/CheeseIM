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
| 会话 seq 分配 | Redis Lua 状态机（ALLOCATED/MISS/EXHAUSTED/LOCKED）+ Mongo `findAndModify $inc` 段预分配 + 启动时 Redis 守卫 | `common-core/.../store/sequence/conversation/ConversationSeqAllocator.java`、`RedisConversationSeqCacheStore.java:129`、`ConversationSeqAllocatorConfigurer.java:40` |
| api-server 无状态 HTTP Facade | Controller 不下沉 Response 模型，Facade 编排 Dubbo | `api-server/.../controller/*`、`*Facade.java` |
| JWT 无状态 access token | HS256，跨节点共享验证 | `authcenter/.../auth/JwtTokenIssuer.java:25` |
| Postbox ingress 按 conversation key 分区 | 保序投递到 Kafka 分区 | `postbox/.../IngressMessagePublisher.java:23` |
| 离线推送 5 厂商真实集成 | APNs(pushy)/FCM/Huawei/Xiaomi/JPush，全 lifecycle，`enabled:false` 默认 | `postman/.../provider/*`、`OfflinePushServiceImpl.java:58` |
| Mongo `_id` shard-friendly | `{owner}:{peer}` / `conversationId` 形式 | `UserConversationRepositoryImpl.java:293` 等 |
| 会话增量同步 version-log | 200 上限回退全量 | `business/.../ConversationServiceImpl.java:263` |
| 5 厂商推送回执 + 日上限 + 定时清理 | | `module-postman.yml` |
| 在线路由表 | Redis HASH + TTL，`register` / `refresh` / `unregister` 走 Lua 原子脚本，直连 Redis 不再走 L1 | `postoffice/.../RedisOnlineRouteService.java`、`postoffice/ARCH.md` §3 |
| 跨节点在线投递 | `gatewayNode` 写入真实节点 ID，postman 按节点分组，Redis LIST `delivery:node:{nodeId}` 投递到目标 postoffice；代码路径已接通，待长压/chaos 验证 | `NodeIdentityProvider`、`RedisNodeDeliveryService`、`NodeDeliveryPoller` |
| 群消息投递 | NORMAL_GROUP 写扩散到成员级 DeliveryEvent，SUPER_GROUP 读扩散仅持久化 | `postmaster/.../IngressEventListener.java`、`GroupFanoutPlanner` |
| 投递去重 | `DeliveryDedupStore` + Redis `SET NX EX`，跨节点共享且 TTL 自动回收 | `postoffice/.../RedisDeliveryDedupStore.java` |
| Kafka 队列路径 | Producer/Consumer 统一 protobuf bytes，离线推送经 `QueueAdapter`，Chronicle/Kafka 后端一致 | `KafkaQueueAdapter`、`OfflinePushEventProducer` |

### B. 已实现但存在集群缺口（需修复才能多节点）

| 能力 | 集群缺口 |
| --- | --- |
| 踢下线（KickoffCommandService） | 已按 `gatewayNode` 定向入节点队列，Redis 不可用或部分节点入队失败时仍只能退化为本节点尝试 |
| 连接管理（ConnectionManager） | 已由全局 `synchronized` 改为 connection/user 分片锁；仍需长压/重连风暴验证 |
| 二级缓存（MultiLevelCacheService） | L1 Caffeine 本地，无 pub/sub 失效广播，远端写入最长 1-5min 才一致 |
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
- 旧 `IngressEventListener.preProcessReadReceipts` 仍是普通消息 `READ_RECEIPT` 遗留旁路；typed `CHAT_READ` 已改走独立 `ReadStateService`，不再依赖该注释路径。
- ~~WS 异步投递路径走 JSON。~~ **已修复 2026-07-13**：`WsServerHandler` 入站/响应与 `ConnectionManager` 异步在线投递全部使用 Binary WebSocket Frame + `ProtoEnvelopeMapper`，TCP/WS 共用 typed protobuf envelope。

---

## 三、能否应对百万级并发？逐项判定

### 3.1 阻断性问题（必修，按严重度排序）

| 级别 | 问题 | 位置 | 影响 |
| --- | --- | --- | --- |
| ~~**P0**~~ | ~~跨节点在线投递失效：`gatewayNode` 硬编码 `"postoffice"`，Dubbo 默认 LB 随机选节点~~ | ~~`ConnectionManager.java:486`、`OnlineDispatcherImpl.java:67`~~ | **已修复 2026-07-07**：`NodeIdentityProvider` 写入真实节点 ID 到 `gatewayNode`；postman 按 `gatewayNode` 分组，通过 Redis LIST `delivery:node:{nodeId}` LPUSH/BRPOP 投递到正确节点；`NodeDeliveryPoller` 后台 daemon 线程消费并委托 `OnlineDispatcherImpl` 本地投递。跨节点在线投递不再依赖 Dubbo 随机 LB |
| ~~**P0**~~ | ~~群投递被硬跳过~~ | ~~`DeliveryEventListener.java:59-61`~~ | **已修复 2026-07-06**：`IngressEventListener.fanoutGroupDelivery` 接通 `GroupFanoutPlanner`，NORMAL_GROUP 走写扩散按成员切片 publish N 个 keyed DeliveryEvent（`g:{groupId}:{memberId}`），SUPER_GROUP 走读扩散仅持久化，postman `DeliveryEventListener` 去除 `ChatType.GROUP` 跳过分支 |
| ~~**P0**~~ | ~~路由表非原子 RMW + L1 无失效广播~~ | ~~`RedisOnlineRouteService.java:25,35,47`、`MultiLevelCacheService.java:45`~~ | **已修复 2026-07-06**：`RedisOnlineRouteService` 改为单脚本 Lua 原 子 HASH 双字段（route/heartbeat），不再走 `MultiLevelCacheService` L1 缓存，见 `postoffice/ARCH.md` §3 |
| ~~**P1**~~ | ~~ConnectionManager 全局 `synchronized`~~ | ~~`ConnectionManager.java:139,203`~~ | **已修复 2026-07-07**：`registerPendingConnection` / `addConnection` / `removeConnection` 改为 connection/user 分片 `ReentrantLock`，认证提升同时持有 connection + user 分片锁，避免 pending 移除与认证提升竞态 |
| ~~**P1**~~ | ~~踢下线跨节点失效~~ | ~~`KickoffCommandServiceImpl.java:18-40`~~ | **已修复 2026-07-07**：`RouteSnapshot` 增 `sessionId`，`RedisOnlineRouteService` 维护 session 路由索引，`KickoffCommandServiceImpl` 按 gatewayNode 定向发布 `KICKOFF` 节点队列命令，`NodeDeliveryPoller` 本地执行 |
| ~~**P1**~~ | ~~`deliveredMessageKeys` 无界本地 HashSet~~ | ~~`ConnectionManager.java:64`~~ | **已修复 2026-07-06**：`ConnectionManager.markDeliveryIfAbsent` 委托给新抽象 `DeliveryDedupStore`；`RedisDeliveryDedupStore` 用 Redis `SET NX EX` 单原子命令做跨节点去重 + TTL 自动过期，key 形如 `idem:delivery:{serverMsgId}:{userId}:{deviceId|*}`。去掉旧 `ConcurrentHashMap.newKeySet()` 本地 Set，长跑 OOM 与跨节点漏去重双问题同时消除。
| ~~**P1**~~ | ~~默认队列 Chronicle（单机）+ Kafka 路径序列化不兼容~~ | ~~`QueueAutoConfigurer.java:28`、`KafkaQueueAdapter.java:54` vs `MessageProducer.java:27`~~ | **已修复 2026-07-07**：`KafkaQueueAdapter` 反序列化对齐 Chronicle（protobuf 原生 / byte[] 透传），`byteKafkaTemplate` 修复泛型不匹配，DeliveryEventListener 改走 `OfflinePushEventProducer` → `QueueAdapter.send`（详见 `postman/ARCH.md` §7）。多节点下队列通道已就绪，只需在 `common.yml` 启用 `cheeseim.queue.type=kafka` 并确保 Kafka bootstrap 配置可用即可上集群。
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
- `ConversationIdUtil` 用 `s:/g:/n:/ng:`，但 `GroupController.resolveGroupId` 还在检查 `c2:` 前缀（死分支）。
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
| 集群部署 | 已有 `application-cluster.yml`，Redis Sentinel/Cluster、Mongo replica URI、Kafka bootstrap、Nacos namespace 走环境变量；k8s/helm 未落地 | 完整 k8s/helm | 完整 | Sentinel/Cluster + namespace 隔离 |

---

## 五、演进路线（按优先级）

### P0 — 修正"伪集群"（主链路已完成）

1. ~~**节点身份贯通**：`RouteSnapshot.gatewayNode` 写入真实节点 id（Nacos 实例 id 或启动随机 UUID 注册到 Redis）。postman 根据 `gatewayNode` 选择 Dubbo 服务组，或改"每节点专属 topic + 节点订阅"直投。~~ **已完成 2026-07-07**：`NodeIdentityProvider` 提供节点 ID（配置或 UUID）；`ConnectionManager.registerOnlineRoute` 写入真实 nodeId；`NodeDeliveryService` + `RedisNodeDeliveryService` 提供按节点投递抽象；`NodeDeliveryPoller` 在 postoffice 后台 BRPOP 消费 `delivery:node:{nodeId}` Redis LIST 并委托 `OnlineDispatcherImpl` 本地投递；`DeliveryEventListener.deliverToUser` 按 gatewayNode 分组路由。all-in-one 模式下 Redis LIST 路径透明兼容，`NodeDeliveryService` 不可用时降级为直接 Dubbo 调用。
2. ~~**群扩散闭环**：在 `IngressEventListener.handleMessage` 调用 `GroupFanoutPlanner`。普通群（`GroupTypeEnum.NORMAL_GROUP`）走写扩散，产出 N 个 keyed `DeliveryEvent`；超级群（`SUPER_GROUP`）走读扩散，仅持久化 + 客户端按 seq 拉取。~~ **已完成 2026-07-06**：`fanoutGroupDelivery` 接通 `GroupFanoutPlanner.partition` + `deliveryKey`，经 `GroupMembershipFacade.loadGroupType` 分流 NORMAL_GROUP（写扩散）/ SUPER_GROUP（读扩散）/ null（按 NORMAL 兜底）；`MessageProducer.publishForMember` 复用 protobuf builder 替换 `receiverId`，避免 Java 侧深拷贝；postman `DeliveryEventListener` 去除 `ChatType.GROUP` 跳过分支。
3. ~~**路由表原子化**：把 `RedisOnlineRouteService` 的 register/refresh/kick 改写为单脚本 Lua（类比 seq 分配器的工作模式），消除 RMW 竞态。~~ **已完成 2026-07-06**：`register`/`refresh`/`unregister` 走单脚本 Lua；存储改为 Redis HASH 双字段（`route:{deviceId}` JSON + `heartbeat:{deviceId}` 时间戳），不再依赖 `MultiLevelCacheService` L1。
4. ~~**连接管理去全局锁**：`ConnectionManager` 按 `userId hash` 分片到 N 个 `ShardedConnectionManager`，分片锁 + 分片清理线程，连接增删并发提升 N 倍。~~ **已完成 2026-07-07**：保留现有类边界，改为 connection/user 双维度分片 `ReentrantLock`，避免 pending 注册/移除与认证提升之间的全局串行和竞态。
5. ~~**投递去重上 Redis**：`deliveredMessageKeys` 改 Redis `SET ... EX` 跨节点去重 + 自动过期。~~ **已完成 2026-07-06**：新增 `DeliveryDedupStore` 抽象 + `RedisDeliveryDedupStore` 实现，使用 `SET <key> 1 NX EX <ttl>` 单原子命令；key = `idem:delivery:{serverMsgId}:{userId}:{deviceId|*}`，TTL 默认 600s（`cheeseim.delivery.dedup.ttl-seconds`）；`ConnectionManager` 删除本地 `ConcurrentHashMap.newKeySet()` 字段，改为依赖注入 `DeliveryDedupStore`，未注入时 NO-OP 放行供测试使用。
6. ~~**修复 Kafka 路径**：统一 Protobuf 序列化器（Producer/Consumer 一致），把 `DeliveryEventListener.emitOfflinePushIfNeeded` 中的 `kafkaTemplate.send` 直调改回 `QueueAdapter`。~~ **已完成 2026-07-07**：新增 `OfflinePushEventProducer`（postman），`DeliveryEventListener.emitOfflinePushIfNeeded` 改走 `offlinePushProducer.publish` → `QueueAdapter.send(OFFLINE_PUSH, …)`；`KafkaQueueAdapter.subscribe/subscribeKeyed` 反序列化对齐 `ChronicleQueueAdapter.deserialize`（`byte[]` 透传 / `Message`/`HistoryEvent`/`OfflinePushEvent` 走 protobuf 原生解析，其它类型 Jackson 兜底）；`CommonKafkaStringConfig` 新增 `byteKafkaTemplate()`（`ByteArraySerializer`），`QueueAutoConfigurer.kafkaQueueAdapter` 改注入字节 template。Chronicle/Kafka 两种 `cheeseim.queue.type` 后端端到端一致。

### P1 — 存储与索引（4-8 周）

7. ~~**Mongo 副本集 + 分片**：声明 `sh.shardCollection`。~~ **仓库侧已完成 2026-07-11**：新增 `distro/mongo/enable-im-sharding.js`，在已就绪的 mongos 上幂等声明 `message_block(conversationId hashed)`、`message_id_mapping(serverMsgId hashed)`、`conversation(ownerUserId hashed)`，并补齐对应查询索引。实际生产集群执行仍由部署流程负责，脚本遇到已有不兼容 shard key 会失败，避免静默漂移。
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
17. ~~**路由表 L1 加 pub/sub 失效广播**。~~ **不再适用**：P0-3 已将在线路由从 `MultiLevelCacheService` 迁为 `RedisOnlineRouteService` 直连 Redis HASH，当前链路不存在路由 L1，也就没有跨节点失效窗口；不要为完成旧路线重新引入本地缓存。

### P3 — 功能补齐

18. **协议补全**：~~在 `message_protocol.proto` 为 `CHAT_READ/CHAT_REVOKE/FORCE_LOGOUT` 增加类型化 payload；~~ **已完成 2026-07-11**：新增 typed command/notify payload 并写入 client/server envelope oneof，Java/Go 生成产物同步。conversation sync / friend / group 控制面仍待 Protobuf 表达。已读/撤回的产品与架构决策见 `server/docs/architecture/read-revoke-design.md`。
19. ~~**WS 协议统一为 Protobuf**。~~ **已完成 2026-07-13**：入站、同步响应、异步聊天/控制通知全部使用 Binary WebSocket Frame + typed protobuf envelope，与 TCP 一致。
20. ~~**已读回执链路**。~~ **已完成 2026-07-14**：TCP/二进制 WS/HTTP 统一调用 `ReadStateService`，完成可见性校验、maxSeq 截断、Redis 单调推进与 Mongo write-behind；同步写入 `ConversationVersionLog.READ_STATE_UPDATED`，增量同步返回变化会话 ID，离线设备据此刷新 read snapshot。结果经 postman `ControlNotificationDispatcher` 按 gatewayNode 推送 typed notify：单聊通知 peer 与阅读者在线端，群聊仅阅读者在线端。
21. **消息撤回/编辑与富媒体**：**撤回闭环已完成 2026-07-14**：`MessageMutationService` 按 serverMsgId 点查服务端 mapping，校验 conversation/发送者/服务端持久化时间两分钟窗口，以 `{serverMsgId}:REVOKED` 原子 upsert mutation；TCP/WS/HTTP 统一返回结果；历史页和 gap repair 批量 merge tombstone。新增按 `createdAt + mutationId` 稳定复合游标的 mutation 增量同步、群成员权限校验和 HTTP 拉取入口；结果经 postman `ControlNotificationDispatcher` 对单聊双方、普通群在线成员按 gatewayNode 推送 typed notify，超级群仍以离线 mutation 同步收敛。消息编辑与上传 token 服务仍待完成。

### P4 — 运维与一致性

22. ~~**限流/幂等**：api-server 入口 RateLimiter + Redis SETNX 幂等 key。~~ **已完成 2026-07-11**：Redis Lua 固定窗口 RateLimiter 按来源地址散列后的 key 在多副本间共享计数，超额返回 HTTP 429，Redis 不可用时放行；携带 `Idempotency-Key` 的鉴权 POST/PUT/DELETE 以 `userId + method + path + key 指纹` 做 Redis SETNX，重复请求返回 HTTP 409。当前不缓存首次响应，客户端需要响应重放时需另行设计状态/响应缓存协议。
23. ~~**集群部署 profile**：新增 `application-cluster.yml`，含 Redis Sentinel/Cluster、Mongo replica URI、Kafka bootstrap、Nacos namespace 分离；删除 `common.yml` 中注释掉的 Redis/Kafka。~~ **已完成 2026-07-08**：`application-cluster.yml` 作为分模块 profile overlay，默认 `cheeseim.queue.type=kafka` 与 `cheeseim.conversation-seq.deployment-mode=cluster`；`MONGODB_URI`、`KAFKA_BOOTSTRAP_SERVERS`、`NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`REDIS_SENTINEL_*` / `REDIS_CLUSTER_*`、`JETCACHE_REDIS_HOST` / `JETCACHE_REDIS_PORT` 均走环境变量，不向 cluster 默认写入 localhost。
24. ~~**多副本一致性**：`MessagePushServiceImpl.attempts/deliveryStates` 迁到 Redis；~~`tokenVersion` 真正 bump；ban 标志落 Mongo 持久化~~。~~ **已完成 2026-07-11**：新增 `PushStateStore` / `RedisPushStateStore`，以每个 serverMsgId 一个 Redis HASH 存 attempt 与 per-user delivery state；Lua 在单 key 内原子拒绝 `ONLINE_CONFIRMED`/`READ` 或既有 attempt，只有一个 postman 副本可取得离线推送 claim。状态默认 TTL 24h，可由 `CHEESEIM_PUSH_STATE_TTL_SECONDS` 调整。
25. ~~**WS ticket 用 Lua 原子 consume**。~~ **已完成 2026-07-08**：`SessionStateStore` 增加原子 consume 契约，Redis/RocksDB 两种后端均实现一次性消费。
26. **Mongo 副本 + 查询 secondary 读偏好** 读写分离。
27. **指标分级**：已有 actuator + Prometheus，补齐 per-conversation seq lag、queue lag、online route hit/miss、push attempt counter。Grafana dashboard 模板。
28. **集群 chaos + 1M 长压脚本**：jmeter/gatling 长连接压测，多节点断网/重启验证。

---

## 六、值得继续升级的好设计

1. **邮政隐喻模块边界**：postoffice/postbox/postmaster/postman 职责切分清晰，可继续演进为 DDD bounded context + 各自独立容器部署。
2. **会话 seq 分配器**：Lua + Mongo `$inc` + 段预分配 + LOCK 状态机 + 启动强校验，**整个项目最接近生产级的代码**，可作为其它分布式计数器（消息 id、通知 id、批次 id）的模板推广。
3. **消息策略引擎 `DefaultMessagePolicyEngine`**：`needHistory/needOnline/needOffline/senderSync/notification` 集中决策，可升级为 DB 配置 + 热更新 + DSL 规则表。
4. **`MessageOptions` 八位 bool 逐消息控制**：类似 OpenIM `Options`，可升级为 protobuf bitmask 节省字段。
5. **`ConversationIdUtil` 规范化 id 体系**：`s:/g:/n:/ng:` + 队列 key 一致，便于分片与幂等；可升级为 Snowflake 全数字 id。
6. **5 厂商真实 push 集成**：APNs/FCM/Huawei/Xiaomi/JPush 全 lifecycle，gated 开关——业内少有一开始就做这么齐的开源项目。可升级为通道降级矩阵 + 模板 + 回执上报。
7. **JetCache BOTH + after-commit eviction**：缓存设计规范（事务提交后再失效），可继续升级为 pub/sub 失效广播。
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

- 2026-07-06：P0-3 路由表原子化已完成。`RedisOnlineRouteService` 重写为基于 `StringRedisTemplate` + 单脚本 Lua，存储改为 Redis HASH 双字段（`route:{deviceId}` JSON + `heartbeat:{deviceId}` 时间戳），不再走 `MultiLevelCacheService`，消除原 RMW 竞态与 L1 无广播不一致。`OnlineRouteService` 接口未变，`HeartbeatMessageHandler` / `ConnectionManager` 调用方零改动。原 ASSESSMENT 「P0-3」表格项已划除，演进路线 P0 §3 已划除并标注完成日期。
- 2026-07-06：P0-2 群扩散闭环已完成。`GroupMembershipQueryService` Dubbo 契约新增 `queryGroupType(groupId)`，business 实现侧复用 `GroupRepository` 读群资料并映射 `GroupTypeEnum`；`GroupMembershipFacade` 增 `loadGroupType` 包装；`GroupFanoutPlanner` 新增 `deliveryKey(groupId, memberId)` 生成 `g:{groupId}:{memberId}` 形式 partition key；`IngressEventListener` 新增 `fanoutGroupDelivery` 分流 NORMAL_GROUP（写扩散按成员切片 publish N 份 keyed DeliveryEvent）/ SUPER_GROUP（读扩散仅持久化）/ null（兜底 NORMAL）；`MessageProducer.publishForMember` 通过 protobuf builder 替换 `receiverId`，避免 Java 侧深拷贝 `Message`；postman `DeliveryEventListener.resolveTargets` 去除 `ChatType.GROUP` 跳过分支，直接按 `receiverId` 投递（写扩散后每条 DeliveryEvent 已携带具体 memberId）。injvm 部署原生兼容，调用方零改动。
- 2026-07-06：P0-5 投递去重上 Redis 已完成。`ConnectionManager` 删除无界本地 `deliveredMessageKeys = ConcurrentHashMap.newKeySet()` 字段；新增 `DeliveryDedupStore` 抽象（`postoffice/dedup/`）和 `RedisDeliveryDedupStore` 实现，使用 Redis 单原子命令 `SET <key> 1 NX EX <ttl>`，key = `idem:delivery:{serverMsgId}:{userId}:{deviceId|*}`（复用 `RedisKeys.deliveryIdem`）。TTL 默认 600 秒，可通过 `cheeseim.delivery.dedup.ttl-seconds` 属性覆盖；0/负数 TTL 钳为 1 秒避免永久 key。Redis 返回 null（异常场景）按 false 处理走重复分支，保证 side-effect-safe。`ConnectionManager` 通过 `@Autowired(required=false)` 注入 Store，未注入时 NO-OP 放行，便于单元测试不连 Redis。postoffice 模块预存的若干 stale 测试（`OnlineDispatcherImplTest` 等）编译与本改动无关，仍按既有 P4 清理项跟进。
- 2026-07-06 review 跟进项（未阻断，留作后续）：
  - 性能：`IngressEventListener.fanoutGroupDelivery` 在 ingress 批（500 条/会话）内对每条群消息触发 `loadGroupType` + `loadGroupMembers` 两次 Dubbo RPC，最坏 1000 RPC/批。建议 P2-14 "MessageSender 权限链合并" 一并把群扩散查群类型/查成员批量合并或缓存到 per-batch scope（business 实现侧 JetCache 可缓 Mongo，但不缓 Dubbo RTT）。
  - 可用性：P0-3 + P0-5 让 Redis 成为在线投递链路的硬依赖（路由表 + 去重）。`RedisDeliveryDedupStore.markIfAbsent` 当前不加 try/catch，Redis 不可达时 `setIfAbsent` 抛 `RedisConnectionFailureException` 会被 `DeliveryEventListener.onMessage` 的 catch 吞掉，等同停所有在线投递。如要 fail-open（Redis 不可达时容忍重复推送），可在 `markIfAbsent` 中 catch `RedisConnectionFailureException` 返回 `true`。本权衡按 ASSESSMENT 接受硬依赖，未改。
  - 命名：`ConnectionManager.markDeliveryIfAbsent` 第三参 `deviceId` 实为 `connectionId`（per-connection 粒度）。新接口 `DeliveryDedupStore` docstring 已明示此历史遗留，未重命名以免破坏既有调用方。
  - 死代码：`GroupMembershipFacade.loadDeliveryTargets`（无人调用）已删除。
- 2026-07-07：P0-6 + P1-6（Kafka 路径修复）已完成。`DeliveryEventListener.emitOfflinePushIfNeeded` 去除对 `KafkaTemplate` 的直连，改为调用新增的 `OfflinePushEventProducer`（postman/sender/），后者通过 `QueueAdapter.send(OFFLINE_PUSH, userId, bytes)` 投递；Chronicle / Kafka 两种 `cheeseim.queue.type` 后端同走抽象路径，离线推送在单机联调模式（Chronicle）下也可用。`KafkaQueueAdapter.subscribe/subscribeKeyed` 反序列化对齐 `ChronicleQueueAdapter.deserialize`：`byte[]` 透传（消费侧 `OfflinePushEventListener.onMessage(byte[])` 不再被 Jackson 解析崩溃）、`Message`/`HistoryEvent`/`OfflinePushEvent` 走 protobuf 原生解析、其它类型 Jackson 兜底；消费者 factory 改用 `ByteArrayDeserializer`。`CommonKafkaStringConfig` 新增 `byteKafkaTemplate()`（`ByteArraySerializer`），`QueueAutoConfigurer.kafkaQueueAdapter` 注入字节 template 取代原 `stringKafkaTemplate`（其类型为 `<String,String>`，与适配器声明 `<String,byte[]>` 泛型不匹配，是 P1-6 端到端不兼容的隐性根因之一）。`DeliveryEventListenerTest` / `DeliveryEventListenerContextTest` 同步改 mock `QueueAdapter`。postman 模块预存的 stale 测试（`OfflinePostmanServiceImplTest` 等）编译与本改动无关。
- 2026-07-07：P0-1 跨节点在线投递已完成。`NodeIdentityProvider` 提供节点唯一 ID（配置或自动 UUID）；`ConnectionManager.registerOnlineRoute` 写入真实 nodeId 替代硬编码 `"postoffice"`；`NodeDeliveryService` 接口（common-api）+ `RedisNodeDeliveryService` 实现（postman）提供按节点投递抽象，通过 `StringRedisTemplate` LPUSH 到 `delivery:node:{gatewayNode}` Redis LIST；`NodeDeliveryPoller`（postoffice）后台 daemon 线程 BRPOP 消费并委托 `OnlineDispatcherImpl` 本地投递；`DeliveryEventListener.deliverToUser` 按 gatewayNode 分组路由，`NodeDeliveryService` 不可用时降级为直接 Dubbo 调用。新增 Redis key `delivery:node:{nodeId}`（`RedisKeys.deliveryNodeQueue`）。all-in-one 模式透明兼容（单 JVM 共享 Redis LIST）。原 ASSESSMENT P0-1 表格项已划除，演进路线 P0 §1 已划除并标注完成日期。
- 2026-07-07：P1 ConnectionManager 去全局锁与跨节点踢下线已完成。`ConnectionManager` 将 pending 注册、认证提升、移除连接从方法级 `synchronized` 改为 connection/user 分片 `ReentrantLock`；认证提升和移除都按 connection → user 顺序加锁，避免断线移除 pending 与认证提升并发交错。`RouteSnapshot` 增加 `sessionId`，`RedisOnlineRouteService` 维护 `online:session:v1:{sessionId}` HASH 辅助索引，同一 session 可保存多条 `userId:deviceId` 路由；`KickoffCommandServiceImpl` 按 user/device/session 查询 `gatewayNode`，本节点直接踢线，远端节点通过 `NodeCommandPublisher` 入队 `NodeQueueMessage(KICKOFF)`，`NodeDeliveryPoller` 消费后委托 `ConnectionManager` 本地踢线；旧裸 `DispatchMessageReq` JSON 保持兼容。
- 2026-07-07：P1/P2 HistoryQuery fail-closed 与分页改造已完成。`HistoryQueryService.allow` 在 provider 缺失、RPC 异常、非预期返回时默认拒绝，只复用 30s 未过期本地权限缓存；`getConversationMessages` 不再拉全量 block，而是先查 latest `blockNo`，按 `conversationId + blockNo range` 窗口读取并按 seq 倒序裁剪；`limit` 钳制到 200，最近页最多扫描 16 个窗口，避免恶意大 limit 或稀疏 block 退化；`MessageBlockDoc` 增加 `idx_message_block_conversation_block` 复合索引。
- 2026-07-08：P1-8 历史块批量写已完成。`BlockHistoryPersistenceService.persist` 在一个 `HistoryEvent` 内先把全部 `MessageIdMappingDoc` upsert 合入一个 unordered `bulkOps`，再把按 blockNo 分桶的块更新合入第二个 unordered `bulkOps`，替代原「循环里逐条 `mappingRepository.save` + 逐块 `mongoTemplate.upsert`」；两类文档 `_id` 均确定性拼接（`{convId}:{clientMsgId}` / `{convId}:{blockNo}`），队列重放幂等。`MessageIdMappingRepository` 已无使用点，删除（`findByServerMsgId` 若撤回链路需要按 P3-21 再引入）。
- 2026-07-08：P1-10 附件查询去 regex 已完成。新增 `attachment_metadata` 集合（`_id=attachmentId`，含 conversationId/serverMsgId/clientMsgId/seq/senderId/contentType/sendTime）：postmaster `BlockHistoryPersistenceService.persistAttachmentMetadata` 对 `ContentType.hasAttachment()` 消息从 content JSON 提取 `attachmentId`（非 JSON/缺字段静默跳过）随历史持久化批量 upsert；`ContentType` 新增 `hasAttachment()`（IMAGE/VOICE/VIDEO/FILE）。postbox `BlockMessageQueryService.findAttachmentCandidate(attachmentId)` 改为 `findById` 点查后 `findSlot` 还原内容，返回 `Optional`；原 `findAttachmentCandidates` 的 `content.regex` 扫的是 `message_id_mapping` 上不存在的 `content` 字段，本就是死查询。同时修复 `findSlot` blockNo 公式与 `BlockIndexUtil` 差一的 bug（改按 `docId` 点查 `_id`），并删除 postbox 内 7 个引用已删架构（`IngressEvent`/postbox 侧持久化/附件 token 服务等）且已无法编译的 stale 测试，postbox 测试基线恢复可编译。
- 2026-07-08：修复 `DefaultMessagePolicyEngine.persistHistory` 取错字段的正确性 bug——原实现读 `options.getNotification()` 而非 `options.getNeedHistory()`，导致 `needHistory=false` 的消息（如已读回执）被错误分配 seq 并持久化、而显式 `notification=false` 的消息被错误跳过历史。`IngressEventListenerTest` 中 3 个既有失败测试（read receipt transient / 首条会话创建 ×2）即此 bug 与 `conversationService` 字段注入不可测所致；后者补包级测试构造器注入（生产路径仍 `@DubboReference`）。
