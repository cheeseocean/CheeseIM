# CheeseIM 整体实现 Review 与重构账本（2026-09-30）

> 状态：源码审查快照。覆盖 Java 服务端、Go SDK、CheeseBox；运行时架构事实入口仍为各模块 ARCH 与 ASSESSMENT。
> 方法：沿调用链核对实现、装配、状态转换与测试，结合本地编译、mock/内存探针；未做真实集群容量验收。
> 这里的“已确认”表示存在可定位的实现或交错时序，不意味着所有部署都已发生故障。
> 本文保留初轮缺陷分析；后续R01/R02/R03/R04/R06/R19已通过代码验收，最新状态、兼容边界与剩余任务见 [修复验收账本](review-remediation-plan-2026-09-30.md)。

## 1. 总体结论

**主干架构可以保留，但业务授权、消息身份、状态一致性和客户端恢复能力需要优先修复。**
项目不是只有接口的空壳：seq 分配、发送/ingress inbox、历史块、节点租约队列、群 fanout、refresh family 和厂商推送均有实质实现。
问题集中在跨组件边界：一个组件返回成功，并不表示下游落库、通知、恢复或授权也正确。

- 发布阻断：默认会话权限无条件放行；会话视图可被设置接口创建并成为 sync/pull 的授权依据。
- 已修复：Kafka 单条发送缺失事务、SDK 先按远端长度分配再校验、群列表吞故障返回成功。
- 仍需重构：未读数、同步游标、安全状态缓存、设备撤销、离线推送重试、连接代际及控制状态恢复。
- 不应从容量脚本、模块名、测试通过或历史“已完成”条目推导生产能力；尚无百万连接实测证据。

优先级：P0 为授权/核心通道阻断；P1 为消息或状态正确性；P2 为边界、维护性和效率。

## 2. 确认问题与当前状态

路径均相对仓库根；行号是审查时定位，后续修改以类与方法名为准。

关键源码导航（表内长 Java 包路径用 `...` 缩写）：

- 授权：原DefaultConversationPermissionService已移除，当前 [ConversationPermissionServiceImpl](../server/business/src/main/java/com/cheeseocean/im/business/service/permission/ConversationPermissionServiceImpl.java)、[ConversationServiceImpl](../server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationServiceImpl.java)、[ConversationSyncServiceImpl](../server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationSyncServiceImpl.java)。
- 消息：[IngressEventListener](../server/postmaster/src/main/java/com/cheeseocean/im/postmaster/listener/IngressEventListener.java)、[MongoMessageHistoryRepository](../server/storage-history/src/main/java/com/cheeseocean/im/storage/history/mongo/MongoMessageHistoryRepository.java)、[HistoryQueryService](../server/postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryQueryService.java)。
- 推送：[MessagePushServiceImpl](../server/postman/src/main/java/com/cheeseocean/im/postman/service/impl/MessagePushServiceImpl.java)、[OfflinePushServiceImpl](../server/postman/src/main/java/com/cheeseocean/im/postman/service/impl/OfflinePushServiceImpl.java)、[OfflinePushEventListener](../server/postman/src/main/java/com/cheeseocean/im/postman/listener/OfflinePushEventListener.java)。
- 安全与生命周期：[UserSecurityRepository](../server/authcenter/src/main/java/com/cheeseocean/im/authcenter/repository/UserSecurityRepository.java)、[ConnectionSessionGuard](../server/postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/ConnectionSessionGuard.java)、[ConnectionManager](../server/postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionManager.java)。
- 客户端：[TCP Client](../sdks/go/transport/tcpim/client.go)、[SDK Client](../sdks/go/client/client.go)、[CheeseBox Syncer](../apps/CheeseBox/internal/sync/syncer.go)、[AppStore](../apps/CheeseBox/internal/store/app_store.go)、[PersistedStore](../apps/CheeseBox/internal/store/persisted_store.go)。

### 2.1 授权与模块装配

| 编号 | 级别/状态 | 源码证据与触发条件 | 影响及重构方向 |
| --- | --- | --- | --- |
| R01 | P0，未修复 | `server/postbox/.../service/DefaultConversationPermissionService.java:15–20` 是实际 `@DubboService`，`check` 恒返回 allow；`HistoryQueryService.allow` 消费其结果 | 非参与者可以通过权限检查；RPC 异常 fail-closed 不等于 provider 的业务授权正确。权限应由 business 域判定主体、会话类型、成员及可见区间，覆盖历史/附件/mutation 等调用方 |
| R02 | P0，未修复 | `server/business/.../ConversationServiceImpl.java:384–419` 的 `setConversations` 无参与关系检查且 `createIfAbsent`；`ConversationSyncServiceImpl.java:164–198` 以视图存在授权，用户水位缺失时使用全局 maxSeq；随后调用无主体的历史区间 RPC | 登录用户可先创建他人会话的视图，再走 sync/pull 查询。设置与授权创建必须分离；读取不能信任用户可写的视图。仅修 R01 不会关闭此旁路 |
| R03 | P1，未修复 | `server/api-server/.../controller/GroupController.java:22–27`、`AuthController.java:28–31` 及多个 Facade 用普通构造器注入远程契约；`ApiServerApplication` 只扫描 API 包，构建不带业务 provider | all-in-one 本地 Bean 掩盖独立进程缺口。统一明确的 Dubbo consumer 装配，写调用禁自动重试；增加独立 API context 测试，不能以扩大扫描 provider 代替 |
| R04 | P1，未修复 | `server/postoffice/.../auth/ConnectionSessionGuard.java:43–62` 的到期复核只有 Heartbeat handler 调用；chat/read/revoke/typing/delivery handler 仍使用本地 `ensureAuthenticated` | kickoff 失败且客户端不发心跳、持续业务流量时，撤销 session 的兜底复核可被绕过。统一认证后命令入口复用现有租约，避免每条新增 RPC |

### 2.2 消息、历史与离线推送

| 编号 | 级别/状态 | 源码证据与触发条件 | 影响及重构方向 |
| --- | --- | --- | --- |
| R05 | P0，**本轮修复** | `KafkaQueueConfiguration.java:53–68` 创建事务模板，原 `KafkaQueueAdapter.send` 在事务外直接发送；consumer 默认 read_uncommitted | RPC/调度/普通 consumer 的单条发布可直接失败，消费还可见失败事务的记录。现统一 producer 事务，等 ACK 与 commit 后返回；业务/DLT 读取强制 read_committed。新增真实 KafkaTemplate + MockProducer、commit 失败与单条/批量 consumer 配置回归 |
| R06 | P1，未修复 | `server/postmaster/.../IngressEventListener.java:164–180` 用首条消息计算整批会话；`ConversationIdUtil.buildQueueKey` 的 PRIVATE/NOTIFICATION 共用双方排序 key，runtime 仅按 key 分组 | 同 key 内混合单聊/通知或反方向通知时，会分配到错误会话的 seq/历史。保持分区 key 兼容，但消费批次应按 canonical 会话与业务类型重新分组，不能把路由 key 当领域身份 |
| R07 | P1，未修复 | `MessageSenderImpl.java:84–87` 的 inbox 含 sender/conversation/clientMsgId；`server/storage-history/.../MongoMessageHistoryRepository.java:56–109` mapping ID 只有 conversation/clientMsgId，upsert 又要求 serverMsgId 匹配 | 同会话不同发送者同 clientMsgId，在非分片 Mongo 可发生重复 `_id`，mapping bulk 抛错后 block bulk 不执行。统一身份并设计旧数据 migration；分片后的具体碰撞行为须单独验证 |
| R08 | P1，未修复 | `IngressEventListener.updateDirectUserState` 对每条使用批末 maxSeq；`GroupFanoutEventListener.java:241–245` 使用 sample sender；`RedisConversationStateStore` 用 seq 差增计并以 maxSeq-readSeq 重算 | 反方向消息同批时可能一方 0、一方 2；seq 允许空洞，水位差并非条数。明确 unread 是计数还是近似值，若为精确计数需按收件消息和稳定幂等身份更新；read ACK 不应简单重算 seq 差 |
| R09 | P1，未修复 | `MessagePushServiceImpl.java:46–54` claim 后发送；`RedisPushStateStore` 拒绝已有 attempt；`OfflinePushEventListener.java:34–44` 忽略失败 PushResult | 厂商失败、执行器拒绝/超时被当成功消费；重放也被既有 attempt 抑制。attempt 需要执行租约、retryable/final 区分及完成/释放转换，不能简单删除去重或直接抛异常了事 |
| R10 | P1，未修复 | `OfflinePushEventFactory` 保留 canonical conversationId/seq；`MessagePushServiceImpl` 的 request→Message 丢字段/groupId；`OfflinePushServiceImpl.createPushMessageForPlatform` 拼 `single_*`、`group_*` | 单聊方向相关、群可能成为 `group_null`，偏离 `s:/g:/n:/ng:`。推送端到端保留 canonical 身份并贯穿至 provider payload 测试，不改客户端协议源 |
| R11 | P1，未修复 | `IngressEventListener` 分别发布 HISTORY/DELIVERY；`HistoryEventListener` 异步落库；`MongoMessageHistoryRepository.persist` 分步写 mapping/attachment/block；ingress inbox 默认 7 天 | 在线投递可早于落库，history 持续失败会有收到但查不到的消息；inbox 到期后旧 ingress 重放可能重新绑定 seq。需要明确持久化门槛、保留窗口、重放策略及 DLT 恢复；不等于本次 Kafka 本地事务已解决端到端原子性 |
| R12 | P1/P2，未修复 | `server/postbox/.../HistoryQueryService.pullMessagesBySeqRange` 读取完整区间后 Java 截断 limit | 大区间 gap repair 的 limit 不限制 Mongo 读取量。查询 port 应支持有界窗口与 continuation，覆盖合法 seq 空洞，避免简单 minSeq+limit 造成假完成 |

### 2.3 状态、事务与缓存

| 编号 | 级别/状态 | 源码证据与触发条件 | 影响及重构方向 |
| --- | --- | --- | --- |
| R13 | P1，未修复 | `server/authcenter/.../UserSecurityRepository.java:42–59` 普通缓存读取/写入，TTL 7 天；`RedisCacheRegion.getOrLoad` 无版本比较回填 | 旧回源跨过封禁或 tokenVersion bump 后覆盖新缓存，撤销态回退。安全状态用单调版本专用 Store；仅换成删缓存不能消除延迟回填 |
| R14 | P1，未修复 | `SessionLifecycleService` 每次登录创建新 session；`RedisSessionStateStore` 用户索引留多个 SID，设备索引仅最新；`SessionRevocationServiceImpl.revokeDeviceSession` 撤销一个 SID | 同设备再次登录后，踢设备未覆盖旧 access/refresh family。使用设备 generation 或完整 session 集合，并定义并发登录/撤销共同原子边界 |
| R15 | P1，未修复 | `server/storage-business/.../ConversationVersionLogRepositoryImpl.java:37–51` latest+1 且 UUID ID；`ConversationServiceImpl.syncConversations` 不校验日志保留下界、未使用 idHash；日志 TTL 180 天 | 并发重复 version、晚提交或日志过期可永久漏增量。按 owner 原子版本锚点与提交顺序设计日志，超出可用窗口显式回退全量；只换 `$inc` 仍需处理晚提交可见性 |
| R16 | P1/P2，未修复 | `MongoPersistenceTransactionExecutor` 尊重 transactions-enabled；`CommonMongoPersistenceConfiguration` 仍注册 transaction manager；`ConversationServiceImpl` 另用 `@Transactional` | 关闭开关不关闭注解事务，单机 Mongo 语义不一致。统一事务入口与 profile 开关；cluster 多集合写必须有真实事务边界 |
| R17 | P1/P2，未修复 | `ConversationServiceImpl.java:564–585` afterCommit 顺序失效多个 cache region，无补偿 | 第一次 Redis 删除失败可留下其他陈旧缓存、DB 已提交却请求报错。保留 afterCommit 时序，为失效建立可观察、有界补偿并定义返回语义 |
| R18 | P1，未修复 | `ReadStateServiceImpl.java:94–109` 先推进 hot state，changed=false 提前返回；`199–215` 吞 outbox 写异常 | readSeq 成功而日志/outbox 失败后，同 ACK 重试无法补事件。参考已有 DeliveryStateService 的 unchanged retry，稳定 event ID 幂等补齐，覆盖两类故障窗口 |

### 2.4 连接、SDK 与 CheeseBox

| 编号 | 级别/状态 | 源码证据与触发条件 | 影响及重构方向 |
| --- | --- | --- | --- |
| R19 | P1，未修复 | `server/postoffice/.../ConnectionManager.java:229–234` 的 pending 校验允许缺失；Auth RPC 返回后继续提升 | 断线清理先完成、认证晚返回时，可复活关闭连接及路由，计数二次释放并可能踢其他有效连接。锁内要求同一个 pending 仍存在且 channel/state 活跃 |
| R20 | P1，未修复 | `sdks/go/transport/tcpim/client.go` 和 `sdks/go/client/client.go` 的 emit 使用非阻塞 default；队列容量 16/32 | message/ACK/revoke/disconnect/forceLogout 都可静默丢弃；最后一条丢失没有后续 seq 触发 repair。可靠事件用流控或显式溢出重同步；typing 可合并。真实丢失频率仍需压力测试 |
| R21 | P1，未修复 | `tcpim.Client.Connect` 覆盖 conn/done；旧 readLoop 只对 conn 做身份比较，却关闭当前 done 并发布 disconnect | 新连接安装后旧循环可关闭其 heartbeat、产生过时 disconnect。连接实例绑定 conn/done/generation，事件也须有代际隔离，不能仅加互斥锁 |
| R22 | P1，未修复 | `apps/CheeseBox/internal/sync/syncer.go:49–140` 用 localMax 判断完整性，localMax=0 不修 gap，单次拉 50 且忽略 Completed/EndSeq | 首次实时 seq101 可让空缓存看似超过 bootstrap max100，历史1–100不再加载；多页及部分 repair 也可假完成。区分已见最大值与已覆盖区间，通用同步策略归 SDK |
| R23 | P1，未修复 | `internal/store/app_store.go` 的 read/delivery 高水位与 revoke tombstone 仅内存；`persisted_store.go` 保存控制 cursor，却未同快照保存这些状态；UI历史恢复只读/日志 | 事件先于消息到达、cursor 落盘后重启，事件不再重放、状态又丢失。cursor 必须与派生状态原子快照，或保留可重放事件，并建立唯一消息恢复入口 |
| R24 | P1，**本轮修复** | TCP 原认证/接收路径读取 uint32 长度，先分配/读 body 后 DecodeFrame | 超大声明可在 1 MiB 守卫前尝试巨额分配。现统一 readFrame，先验证 magic/version/uint32 上限，再转 int 和读 body；非法头保持连接开启也立即报错 |
| R25 | P1/P2，未修复 | TCP/WS `channelInactive` 同步 `ConnectionManager.removeConnection`，锁内 Redis 注销/lease release；命令响应直接 writeAndFlush | 慢 Redis 与断线风暴阻塞 EventLoop；慢读响应缺少统一 writable 策略。拆本地移除/远端 fenced 清理、统一写入策略；容量影响须真实测量 |

### 2.5 真正的虚实现与假成功

| 位置 | 判定 | 当前状态 |
| --- | --- | --- |
| `DefaultConversationPermissionService.check` | 真实导出的占位授权，恒 allow；严重于普通 TODO | 未修复，R01 |
| `UserFacade.updateUserSettings` / `UserController` PUT | 忽略设置请求，仅检查 session，却返回 204；实际未写入 | 未修复，应补领域写契约/缓存失效或明确未支持，不能保留假成功 |
| `GroupController.list` | catch-all 无日志，首次故障空列表、中途故障部分列表均为200 | **本轮修复**，交统一异常处理；两类故障 HTTP 回归通过 |
| `AuthMessageHandler.notifyUserOnline` | 只写“通知已发送”日志，无实际事件 | 清理候选，应删空钩子或准确表达能力 |
| `InMemoryPushStatisticsService` + `PushScheduledTasks` | 生产没有记录调用，定时读取断开的统计；实时接口按最后记录时间返回累计值 | 应统一既有 Micrometer 指标，避免将零统计解释为无流量 |
| `MessageOptions.senderSync` 等 | 公共兼容字段仍在，部分选项无独立执行语义 | 属能力缺口；保留兼容并明确支持矩阵，不能据字段存在宣传功能 |

## 3. 值得保留的设计

1. **唯一 seq 分配路径**：`ConversationSeqAllocator` 的段预分配、owner lease 与 Lua 状态机；允许空洞、不允许重复/回退。修未读应适配此语义，不应改成普通 INCRBY。
2. **稳定发送身份与 ingress 绑定**：`MessageSenderImpl` 的 payload fingerprint、首次 ACK；`IngressEventListener.bindSequences` 在副作用前绑定 seq，短期重放复用身份。需补保留期外恢复，而非删除 inbox。
3. **Mongo 与领域分离**：storage-history/storage-business 拥有 Document 和 adapter，feature 用 port/model。避免把 MongoTemplate 拉回 Controller/Service。
4. **路由指向真实节点与可靠节点队列**：node ready/processing/lease/dead 支持 ACK、恢复及有界容量；generation/connectionId fencing 避免旧连接误删新路由。
5. **投递语义分层**：broker accepted、ChannelFuture 成功、设备落盘 ACK、read ACK 是不同状态；`OnlineDispatcherImpl` write 后 commit 去重值得保留。
6. **普通群写扩散、超级群读扩散**：独立 worker、membership epoch 和 keyset 分页控制大群工作量；需要修批次 sender/未读语义。
7. **可信身份 assertion、refresh family、一次性 ticket**：真实验证 jti/sub/issuer/audience/时间窗，原子 rotate/reuse/consume。不要把 authcenter 降回 demo。
8. **TCP/WS 共用 typed Protobuf**：唯一协议源，HTTP 保持控制面 DTO 边界。不可用 JSON 长连接绕过契约。
9. **有界业务线程与低基数指标**：业务处理移出 Netty EventLoop，writer 分桶、push executor 有界，适合扩展；应补生命周期与响应隔离。

## 4. 模块拆分建议

**先修语义和装配，再按所有权收敛；当前 16 个 Java 模块没有必要立即进一步微服务化。**

| 当前模块 | 保留的边界 | 下一步收敛 |
| --- | --- | --- |
| api-server | HTTP / principal / Facade，独立无状态 consumer | 统一远程引用；群查询编排从 Controller 移到 Facade/批量领域查询；禁止扩大 provider scan |
| authcenter | identity / token / session / ticket | 专用安全状态缓存与设备 generation，补完整登录-撤销-刷新交错测试 |
| business | 用户、好友、群、会话与权限域 | 权限 provider 收归真实领域；先内部 package 拆 conversation settings/access/sync/control，避免继续膨胀 ConversationServiceImpl |
| postoffice | transport / connection / 本地在线投递 | 连接状态转换集中，生命周期本地/远端隔离；domain handler 不承担 Redis adapter |
| postbox | 发送接入与历史查询 RPC | 权限按域调用，不拥有放行 stub；保留查询/接入边界直到性能数据证明拆进程价值 |
| postmaster | seq / ingress 编排 / history / fanout | canonical 分组、消息身份一致、持久化与重放契约；避免用 batch sample 推导每条业务属性 |
| postman | 在线结果聚合、补偿与厂商推送 | 分清 node attempt 与 vendor attempt；移除断开的统计；推送重试状态机后再考虑独立 push runtime |
| common-api | wire / RPC / 公共领域契约 | 按能力列支持矩阵；公共模型删除必须评估外部消费者，禁止一刀切 |
| common-core | port / model / 通用状态机 | `notification/NotificationSender` 等业务编排是后续所有权收敛候选，避免成为万能公共模块 |
| infra-state / infra-queue | Redis/RocksDB、Kafka/Chronicle 运行时 | feature 仍有 Redis-specific service 与 Lua，应按 port 逐项迁回 adapter；现有拆分是部分完成 |
| storage-* | Mongo adapter、Document、索引、事务装配 | 先修身份/版本/事务，再单独机械迁移旧 package，不能同时改集合与 import |
| SDK / CheeseBox | SDK 拥有 IM 通用能力，应用拥有 UI | catch-up/gap repair 下沉 SDK，以存储接口注入；AppStore/MemoryStore/PersistedStore 明确唯一写入和恢复入口 |

源码边界门禁有价值，但 `server/build.gradle:32–221` 主要是路径/import 正则：没有完整 Gradle 依赖图门禁，也未全面禁止 feature 直接使用驱动。
例如 postoffice 的 RedisOnlineRouteService/RedisLoginLeaseStore 和 postman 的 RedisPushStateStore 仍 import Redis API；infra-state ARCH 已承认节点队列迁移例外。
因此“拆出了 adapter module”与“feature 完全隔离技术实现”要分开验收。后续补图级依赖检查与明确白名单，不先写更严格门禁让现有构建全红。

## 5. 旧代码、无效代码与兼容资产

### 已确认并清理

- `server/postbox/.../policy/ChannelPolicy.java`：内部 Spring Bean，canAccess 恒 false；源码/配置无调用，无授权链路责任，已删除。
- `OfflinePushServiceImpl.resolveSessionType/resolveContentType`：私有转换方法无调用，已删除。
- TCP 认证与 readLoop 的重复拼帧/分配/解码路径：已收敛为 readFrame。

### 后续清理候选

- `InMemoryPushStatisticsService` 的断开记录接口、对应定时读取，与真实 Micrometer 统计统一。
- `AuthMessageHandler.notifyUserOnline` 空通知钩子及误导日志。
- `ConnectionManager.sendMessageToUser/broadcastMessage` 无仓内调用，统计仅是发起写入数；需先评估暴露边界。
- `FriendRealtimeNotifier` 的删除/备注/资料通知方法，仓内仅定义；公共能力删除前确认外部调用。
- CheeseBox `Puller.GetSyncedMaxSeq` 当前未被 Syncer 使用；SDK `EventKindGapRepaired` 无发出点。公共 SDK API 暂留兼容。

### 不应按“垃圾代码”直接删除

- 测试 fake/mock、RocksDB 单机实现、禁用但真实的厂商 provider，均有明确用途。
- 已迁模块但仍为 `common.core.*` 的 package 是迁移债务，不是第二份实现。
- `DeliveryEvent/ConversationSettingsEvent/UserSettingsEvent` 等公共事件和兼容 options 需先评估外部消费者。
- 早期 im_skeleton/im_* 文档按 INDEX 的草案/过程/废弃状态追溯；本轮不物理删历史材料。

## 6. 分阶段重构与验收

| 阶段 | 内容 | 必须证明的结果 |
| --- | --- | --- |
| 第一阶段，本轮已完成 | Kafka 本地事务、TCP 头部限界、群查询明确失败、内部无引用清理 | 事务提交失败不会返回成功；非法头部不读 body；中途查询失败不返回部分200；编译与对应测试通过 |
| 第二阶段，优先 | R01/R02 授权、R03 独立 API 装配、R04 命令 session 复核 | 非参与者所有读取入口拒绝，设置不能铸造权限，独立 consumer context 可启动；不发心跳也不能绕过撤销复核 |
| 第三阶段 | 消息 canonical 分组/身份 migration、精确未读、版本日志、安全缓存、设备撤销、read outbox | 混合方向批次、seq 空洞、旧 cache 回填、晚提交日志、同设备多 session、相同 ACK 重试都有确定性交错测试 |
| 第四阶段 | vendor attempt、连接 generation、可靠事件、同步覆盖区间与控制快照 | 厂商恢复后可重试；旧连接不影响新连接；末尾事件溢出能收敛；重启前后 cursor 与状态一致 |
| 第五阶段 | adapter 所有权/包名/重复 API 清理、查询批量化、容量验证 | 无跨层驱动依赖回归；真实 Kafka/Redis/Mongo 故障窗口有证据；多节点容量用可复核结果报告 |

涉及已持久化 ID、缓存 key、版本和 cursor 的修复必须单列兼容/migration 与回滚方案；不可混入“纯重命名”提交。

## 7. 本轮验证记录

已通过：

- `server`：`./gradlew compileJava :infra-queue:test :postbox:test :postman:test`，含四类架构边界门禁。
- `server`：`./gradlew :api-server:test`，含群查询首步失败和中途失败的 HTTP 回归。
- 审查阶段：`./gradlew --offline :authcenter:test --rerun :business:test --rerun :api-server:test --rerun :storage-business:test --rerun`。
- `sdks/go`：`go test ./...`、`go build ./...`、`go test -race ./transport/tcpim`。
- 审查阶段 `apps/CheeseBox`：`go test ./...`；SDK client/transport 与 CheeseBox sync/store/ui 的 race 测试通过。

Kafka 回归使用真实 KafkaTemplate 与内存 MockProducer；群接口使用真实异常 advice 与 mock 下游；TCP 使用 reader 与 net.Pipe。
测试通过不是对上述未修复交错场景的否定。真实 E2E 需要 `CHEESEIM_E2E=1`，本轮未进行真实中间件联调、厂商发送或长压/chaos。
