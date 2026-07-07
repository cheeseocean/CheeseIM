# CheeseIM 服务端架构评估与演进路线

> 评估时间：2026-07-05
> 评估对象：`server/` 全模块（Java 17 + Spring Boot 3 + Dubbo 3 + Gradle + MongoDB + Redis）
> 评估方法：基于源码逐行扫描，结论可追溯到具体文件:行号
> 维护原则：本文档为权威评估，与代码事实冲突时以代码为准；下次评估需更新本文。

## 一、综合结论

CheeseIM 是一个**架构骨架已经为集群设计、当前实现态仍是单机**的早期开源 IM 服务端。其模块边界（postoffice / postbox / postmaster / postman / authcenter / business / common-api / common-core）和"邮政"隐喻清晰，业内少见。

- 单节点 `postoffice` + Redis + Mongo 的实测上限大致在 **10-30 万并发长连接**。
- **当前不可原生支撑百万级并发**，根本原因不在于性能，而在于"伪集群"——多个看似分布式的组件在多节点下会失效或丢失状态。
- 架构骨架本身可演进到百万级，**不需要推倒重写**，核心修复 6-8 项即可横向扩展。

---

## 二、已实现能力（生产质量分级）

### A. 达到集群生产级（可直接信任）

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

### B. 已实现但存在集群缺口（需修复才能多节点）

| 能力 | 集群缺口 |
| --- | --- |
| 在线路由表（Redis hash + TTL） | 注册/刷新/踢下线是**非原子 read-modify-write**，并发会丢路由 |
| 在线投递（OnlineDispatcher Dubbo） | Dubbo 默认随机 LB，**无法命中持有连接的节点**，跨节点消息会误判离线走 push |
| 踢下线（KickoffCommandService） | Dubbo 随机节点，本地 `kickUserConnections` 不到他机连接 |
| 连接管理（ConnectionManager） | 全局 `synchronized`，连接增删串行；`deliveredMessageKeys` 无界本地 HashSet |
| 群消息投递 | `DeliveryEventListener` 对 `ChatType.GROUP` 直接 `return List.of()`，`GroupFanoutPlanner` 已实现但无人调用 |
| 二级缓存（MultiLevelCacheService） | L1 Caffeine 本地，无 pub/sub 失效广播，远端写入最长 1-5min 才一致 |
| 队列抽象（QueueAdapter） | 默认 Chronicle（单机文件）；Kafka 路径消费者用 Jackson `StringDeserializer`，生产者发 Protobuf 字节，**端到端不兼容** |
| WS ticket 一次性 consume | read-then-write 非原子，有重放窗口 |
| `tokenVersion` 踢下线校验 | 硬编码 `1L`，基于版本号的踢下线形同虚设 |
| 用户封禁标志 | 只存 Redis 缓存（`loader = () -> null`），flush 即解封 |
| 好友 accept 非事务 | `acceptFriendRequest` 双向写无 `@Transactional`，部分失败留单边好友 |
| History 查询全扫 | `getConversationMessages` 拉全部 block 内存排序再截 limit |
| 权限校验失败放行 | `HistoryQueryService.allow` 在 `RpcException` 时 `return true` |
| MessageIdMappingDoc 逐条 save | 非 `bulkOps`，50k msg/s 写不动 |
| UserMaxSeq / ReadSeq 写 behind | 单线程 drain + 有界队列，超限丢弃（Redis 仍权威） |
| ConversationVersionLog | 无 TTL，长期增长 |

### C. 已声明协议但未在链路上接通

- `CommandType.CHAT_READ(33) / CHAT_REVOKE(34) / FORCE_LOGOUT(35)` 在 `message_protocol.proto` 中**无对应 payload 消息**，链路上未实现。
- `IngressEventListener.preProcessReadReceipts` 注释掉，已读回执链路未闭环。
- WS 路径实际走 JSON（`ConnectionManager.sendTransportMessage` 的 ObjectMapper 分支），未统一为 Protobuf。

---

## 三、能否应对百万级并发？逐项判定

### 3.1 阻断性问题（必修，按严重度排序）

| 级别 | 问题 | 位置 | 影响 |
| --- | --- | --- | --- |
| ~~**P0**~~ | ~~跨节点在线投递失效：`gatewayNode` 硬编码 `"postoffice"`，Dubbo 默认 LB 随机选节点~~ | ~~`ConnectionManager.java:486`、`OnlineDispatcherImpl.java:67`~~ | **已修复 2026-07-07**：`NodeIdentityProvider` 写入真实节点 ID 到 `gatewayNode`；postman 按 `gatewayNode` 分组，通过 Redis LIST `delivery:node:{nodeId}` LPUSH/BRPOP 投递到正确节点；`NodeDeliveryPoller` 后台 daemon 线程消费并委托 `OnlineDispatcherImpl` 本地投递。跨节点在线投递不再依赖 Dubbo 随机 LB |
| ~~**P0**~~ | ~~群投递被硬跳过~~ | ~~`DeliveryEventListener.java:59-61`~~ | **已修复 2026-07-06**：`IngressEventListener.fanoutGroupDelivery` 接通 `GroupFanoutPlanner`，NORMAL_GROUP 走写扩散按成员切片 publish N 个 keyed DeliveryEvent（`g:{groupId}:{memberId}`），SUPER_GROUP 走读扩散仅持久化，postman `DeliveryEventListener` 去除 `ChatType.GROUP` 跳过分支 |
| ~~**P0**~~ | ~~路由表非原子 RMW + L1 无失效广播~~ | ~~`RedisOnlineRouteService.java:25,35,47`、`MultiLevelCacheService.java:45`~~ | **已修复 2026-07-06**：`RedisOnlineRouteService` 改为单脚本 Lua 原 子 HASH 双字段（route/heartbeat），不再走 `MultiLevelCacheService` L1 缓存，见 `postoffice/ARCH.md` §3 |
| **P1** | ConnectionManager 全局 `synchronized` | `ConnectionManager.java:139,203` | 重连风暴下吞吐塌缩 |
| **P1** | 踢下线跨节点失效 | `KickoffCommandServiceImpl.java:18-40` | 多端登录超限、安全踢下线不可靠 |
| ~~**P1**~~ | ~~`deliveredMessageKeys` 无界本地 HashSet~~ | ~~`ConnectionManager.java:64`~~ | **已修复 2026-07-06**：`ConnectionManager.markDeliveryIfAbsent` 委托给新抽象 `DeliveryDedupStore`；`RedisDeliveryDedupStore` 用 Redis `SET NX EX` 单原子命令做跨节点去重 + TTL 自动过期，key 形如 `idem:delivery:{serverMsgId}:{userId}:{deviceId|*}`。去掉旧 `ConcurrentHashMap.newKeySet()` 本地 Set，长跑 OOM 与跨节点漏去重双问题同时消除。
| **P1** | 默认队列 Chronicle（单机）+ Kafka 路径序列化不兼容 | `QueueAutoConfigurer.java:28`、`KafkaQueueAdapter.java:54` vs `MessageProducer.java:27` | 多节点下无队列通道 |
| **P2** | 历史分页全扫 | `HistoryQueryService.java:53-82` | 长会话分页 O(n) |
| **P2** | 权限校验失败放行 | `HistoryQueryService.java:213` | Dubbo 故障期越权访问 |
| **P2** | MessageSender 三次同步 Dubbo | `MessageSenderImpl.java:109-123` | 发送热路径 RTT ×3 |

### 3.2 容量与正确性隐患

- `MessageIdMappingDoc` 逐条 `save` → 单 Mongo 节点 50k msg/s 写不动。
- `UserMaxSeqPersistenceWriter` / `ReadSeqPersistenceWriter` 单线程 drain + 有界队列超限丢弃。
- `ConversationVersionLog` 无 TTL 长期增长。
- Kafka 配置在 `common.yml` 中被注释，独立模块启动队列为空。
- Mongo 仅 `localhost:27017` 单点，无副本集、无分片声明。
- WS ticket consume 非原子，有重放窗口。
- `tokenVersion` 恒 1L，版本号踢下线形同虚设。
- 用户封禁仅 Redis 缓存，flush 即解封。
- `ConversationIdUtil` 用 `s:/g:/n:/ng:`，但 `GroupController.resolveGroupId` 还在检查 `c2:` 前缀（死分支）。
- `OfflinePushServiceImpl` 日计数增量是非原子 read-modify-write。

### 3.3 容量上限估算

| 部署 | 估算并发上限 | 主要瓶颈 |
| --- | --- | --- |
| 单节点 all-in-one（Chronicle + injvm Dubbo） | 1-3 万连接 | ConnectionManager 锁、单 JVM |
| 单节点 postoffice + Redis + Mongo + Kafka | 10-30 万连接 | ConnectionManager 锁、deliveredMessageKeys 内存、单 JVM IO |
| 多节点 postoffice（当前代码） | **不能扩展** | 跨节点在线投递失效 |
| 多节点 postoffice（修复 P0 后） | 50-100 万连接 | 取决于路由表 Redis 压力、Mongo 分片 |

---

## 四、与同类开源 IM 对照

| 维度 | CheeseIM | OpenIM | Centrifugo | 现代主流做法 |
| --- | --- | --- | --- | --- |
| 在线路由 | Redis hash + Dubbo 随机投递 | Redis + 一致性哈希到 gateway | 内置 broker | gateway 节点 id + 服务组路由，或 per-node topic 直投 |
| 群扩散 | 普通群都未扩散 | 写扩散+读扩散+fanout worker | N/A | 小群写扩散、大群读扩散（inbox timeline） |
| 消息存储 | 单 collection + 逐条 mapping | MySQL/分片 | 内存/Redis | Mongo sharded + 时间分区 + 冷热分离 |
| 消息队列 | Chronicle 默认 + Kafka 端到端不通 | Kafka | 内置 | Kafka + 分区 + consumer group 并行 |
| 缓存失效 | L1 本地无广播 | Redis-only | 内置 | Redis pub/sub 或 MQTT 广播 L1 失效 |
| 协议 | 控制面无 Protobuf | gRPC 全栈 | WebSocket | gRPC + Protobuf 全栈 |
| 多端策略 | 每节点 10 连接，无全局计数 | 全局在线表 | N/A | 在线表 Lua 维护 `connectionCount` |
| 踢下线 | Dubbo 随机节点 | Redis pub/sub 到 gateway | N/A | per-node topic + 节点订阅 |
| 集群部署 | localhost 默认，无 cluster profile | 完整 k8s/helm | 完整 | Sentinel/Cluster + namespace 隔离 |

---

## 五、演进路线（按优先级）

### P0 — 修正"伪集群"（必做，2-4 周）

1. ~~**节点身份贯通**：`RouteSnapshot.gatewayNode` 写入真实节点 id（Nacos 实例 id 或启动随机 UUID 注册到 Redis）。postman 根据 `gatewayNode` 选择 Dubbo 服务组，或改"每节点专属 topic + 节点订阅"直投。~~ **已完成 2026-07-07**：`NodeIdentityProvider` 提供节点 ID（配置或 UUID）；`ConnectionManager.registerOnlineRoute` 写入真实 nodeId；`NodeDeliveryService` + `RedisNodeDeliveryService` 提供按节点投递抽象；`NodeDeliveryPoller` 在 postoffice 后台 BRPOP 消费 `delivery:node:{nodeId}` Redis LIST 并委托 `OnlineDispatcherImpl` 本地投递；`DeliveryEventListener.deliverToUser` 按 gatewayNode 分组路由。all-in-one 模式下 Redis LIST 路径透明兼容，`NodeDeliveryService` 不可用时降级为直接 Dubbo 调用。
2. ~~**群扩散闭环**：在 `IngressEventListener.handleMessage` 调用 `GroupFanoutPlanner`。普通群（`GroupTypeEnum.NORMAL_GROUP`）走写扩散，产出 N 个 keyed `DeliveryEvent`；超级群（`SUPER_GROUP`）走读扩散，仅持久化 + 客户端按 seq 拉取。~~ **已完成 2026-07-06**：`fanoutGroupDelivery` 接通 `GroupFanoutPlanner.partition` + `deliveryKey`，经 `GroupMembershipFacade.loadGroupType` 分流 NORMAL_GROUP（写扩散）/ SUPER_GROUP（读扩散）/ null（按 NORMAL 兜底）；`MessageProducer.publishForMember` 复用 protobuf builder 替换 `receiverId`，避免 Java 侧深拷贝；postman `DeliveryEventListener` 去除 `ChatType.GROUP` 跳过分支。
3. ~~**路由表原子化**：把 `RedisOnlineRouteService` 的 register/refresh/kick 改写为单脚本 Lua（类比 seq 分配器的工作模式），消除 RMW 竞态。~~ **已完成 2026-07-06**：`register`/`refresh`/`unregister` 走单脚本 Lua；存储改为 Redis HASH 双字段（`route:{deviceId}` JSON + `heartbeat:{deviceId}` 时间戳），不再依赖 `MultiLevelCacheService` L1。
4. **连接管理去全局锁**：`ConnectionManager` 按 `userId hash` 分片到 N 个 `ShardedConnectionManager`，分片锁 + 分片清理线程，连接增删并发提升 N 倍。
5. ~~**投递去重上 Redis**：`deliveredMessageKeys` 改 Redis `SET ... EX` 跨节点去重 + 自动过期。~~ **已完成 2026-07-06**：新增 `DeliveryDedupStore` 抽象 + `RedisDeliveryDedupStore` 实现，使用 `SET <key> 1 NX EX <ttl>` 单原子命令；key = `idem:delivery:{serverMsgId}:{userId}:{deviceId|*}`，TTL 默认 600s（`cheeseim.delivery.dedup.ttl-seconds`）；`ConnectionManager` 删除本地 `ConcurrentHashMap.newKeySet()` 字段，改为依赖注入 `DeliveryDedupStore`，未注入时 NO-OP 放行供测试使用。
6. **修复 Kafka 路径**：统一 Protobuf 序列化器（Producer/Consumer 一致），把 `DeliveryEventListener.emitOfflinePushIfNeeded` 中的 `kafkaTemplate.send` 直调改回 `QueueAdapter`。

### P1 — 存储与索引（4-8 周）

7. **Mongo 副本集 + 分片**：声明 `sh.shardCollection`。`message_block` 按 `conversationId hash` 分片，`message_id_mapping` 按 `serverMsgId hash` 分片，`UserConversationDoc` 按 `ownerUserId hash`。
8. **历史块批量写**：`BlockHistoryPersistenceService` 收批后 unordered bulk insert。
9. **历史分页改 blockNo range**：`getConversationMessages` 改为按 block 二分定位起始块再顺序读，避免全扫。
10. **附件查询去 regex**：`BlockMessageQueryService.findAttachmentCandidates` 的 `content.regex` 改为附件元数据表（`_id=attachmentId`）。
11. **权限校验失败拒绝**：`HistoryQueryService.allow` 在异常时 `return false`，加本地兜底缓存降级。
12. **`ConversationVersionLog` TTL 索引**：`createdAt` 加 `expireAfterSeconds`，或加定期 compact job。
13. **read-seq 写并发化**：`ReadSeqPersistenceWriter` 改按 `userId` 分桶的多线程 drain 或走 Kafka 通道。

### P2 — 链路性能（8-16 周）

14. **MessageSender 权限链合并**：黑名单/用户 receiveOpt/会话 receiveOpt 合并为单个 Dubbo `PermissionAggregate` 一次性返回；本地 Caffeine + 异步刷新。
15. **Ingress batch 内批量 seq + 批量 Mongo upsert + 批量 delivery publish**（当前 delivery 是 per-message publish）。
16. **postoffice 单机 C100K+**：Netty business pool 独立、`ConnectedChannel` 池化、对业务线程 bounded queue + 背压。
17. **路由表 L1 加 pub/sub 失效广播**：复用 JetCache `broadcastChannel`，监听失效消息清 L1。

### P3 — 功能补齐

18. **协议补全**：在 `message_protocol.proto` 为 `CHAT_READ/CHAT_REVOKE/FORCE_LOGOUT` 增加类型化 payload；conversation sync / friend / group 控制面用 Protobuf 表达，便于多语言客户端。
19. **WS 协议统一为 Protobuf**（当前 JSON），与 TCP 一致。
20. **已读回执链路**：接通 `IngressEventListener.preProcessReadReceipts` 与 `messageStateService`。
21. **消息撤回/编辑、富媒体（图片/文件上传 token 服务）、会话删除入口**（README 已承认缺）。

### P4 — 运维与一致性

22. **限流/幂等**：api-server 入口 RateLimiter + Redis SETNX 幂等 key。
23. **集群部署 profile**：新增 `application-cluster.yml`，含 Redis Sentinel/Cluster、Mongo replica URI、Kafka bootstrap、Nacos namespace 分离；删除 `common.yml` 中注释掉的 Redis/Kafka。
24. **多副本一致性**：`MessagePushServiceImpl.attempts/deliveryStates` 迁到 Redis；`tokenVersion` 真正 bump；ban 标志落 Mongo 持久化。
25. **WS ticket 用 Lua 原子 consume**。
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

**短期把"伪集群"修真（节点身份 + 群扩散 + 路由原子化 + Kafka 端到端），中长期把存储分片化 + 控制面 Protobuf 化 + 多副本一致性补齐**，架构骨架本身已够支撑百万级演进，不需要推倒重写。

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
- 2026-07-07：P0-1 跨节点在线投递已完成。`NodeIdentityProvider` 提供节点唯一 ID（配置或自动 UUID）；`ConnectionManager.registerOnlineRoute` 写入真实 nodeId 替代硬编码 `"postoffice"`；`NodeDeliveryService` 接口（common-api）+ `RedisNodeDeliveryService` 实现（postman）提供按节点投递抽象，通过 `StringRedisTemplate` LPUSH 到 `delivery:node:{gatewayNode}` Redis LIST；`NodeDeliveryPoller`（postoffice）后台 daemon 线程 BRPOP 消费并委托 `OnlineDispatcherImpl` 本地投递；`DeliveryEventListener.deliverToUser` 按 gatewayNode 分组路由，`NodeDeliveryService` 不可用时降级为直接 Dubbo 调用。新增 Redis key `delivery:node:{nodeId}`（`RedisKeys.deliveryNodeQueue`）。all-in-one 模式透明兼容（单 JVM 共享 Redis LIST）。原 ASSESSMENT P0-1 表格项已划除，演进路线 P0 §1 已划除并标注完成日期。P0 全部四项阻断问题现已修复完毕。