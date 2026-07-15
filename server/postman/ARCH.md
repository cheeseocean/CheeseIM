# postman/ARCH.md — 投递与离线推送事实快照

> 在线投递执行 + 离线厂商推送（APNs/FCM/Huawei/Xiaomi/JPush）。
> 跨节点在线投递 P0-1 代码路径已接通；长压/chaos 验证和剩余多副本一致性待办见 `server/docs/architecture/ASSESSMENT.md`。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `DeliveryEventListener` | `listener/DeliveryEventListener.java:40` | 消费 `TopicNames.DELIVERY`，做在线投递 + 触发离线推送 |
| `OfflinePushEventListener` | `listener/OfflinePushEventListener.java:24` | 消费 `TopicNames.OFFLINE_PUSH`，调用厂商推送 |
| `OnlineDispatcherImpl` | **在 postoffice**（`postoffice/api/OnlineDispatcherImpl.java:67`） | Dubbo 投递执行 |
| `OfflinePushServiceImpl` | `service/impl/OfflinePushServiceImpl.java:58` | 多厂商推送 fan-out 编排 |
| `MessagePushServiceImpl` | `service/impl/MessagePushServiceImpl.java:31` | 推送尝试记录 + 投递状态记录 |
| `PushDecisionService` | `service/PushDecisionService.java:13` | 推送决策（依赖 `DeliveryState` 与 attempt 记录） |
| `ControlEventDeliveryScheduler` | `task/ControlEventDeliveryScheduler.java` | claim `conversation_control_event` 后补偿在线控制通知；不触发离线推送 |
| `provider/*` | `provider/` | 5 个厂商真实实现 |

## 2. 在线投递链路（**P0-1 已修复 2026-07-07**）

`DeliveryEventListener.deliverToUser`：
1. `onlineRouteQueryService.findByUser(userId)` 查 Redis 路由（含真实 `gatewayNode`）
2. 按 `gatewayNode` 分组路由
3. 对每个节点：
   - 如果 `NodeDeliveryService` 可用且 gatewayNode 非空 → LPUSH `DispatchMessageReq` JSON 到 `delivery:node:{gatewayNode}` Redis LIST
   - 否则降级为直接 Dubbo 调用（all-in-one / gatewayNode 为空的历史数据）
4. 目标 postoffice 节点的 `NodeDeliveryPoller` 后台 daemon 线程 BRPOP 消费，委托 `OnlineDispatcherImpl` 本地投递

✅ 跨节点在线投递已修复：postman 按路由表中的真实节点 ID 精准投递，不再依赖 Dubbo 随机 LB。

## 3. 群投递（**已闭环 2026-07-06**，P0-2 修复项）

postmaster `IngressEventListener.fanoutGroupDelivery` 已按 `GroupTypeEnum` 分流：

- NORMAL_GROUP → 写扩散：查询群成员 → 按 `GroupFanoutPlanner.partition` 切片 → 每成员 `MessageProducer.publishForMember("g:{groupId}:{memberId}", template, memberId)` 发一份 keyed DeliveryEvent
- SUPER_GROUP → 读扩散：仅持久化，客户端按 seq 拉取
- null → 按 NORMAL_GROUP 兜底，Dubbo 异常被吞 Logged，避免 ingress 批重投引发 dup seq

postman `DeliveryEventListener.resolveTargets` 已**移除** `ChatType.GROUP` 跳过分支：写扩散后每条 DeliveryEvent 已带 `receiverId`，直接按 `receiverId` 投递即可。详见 `postmaster/ARCH.md` §5。

## 4. 投递去重

`ConnectionManager.markDeliveryIfAbsent`（postoffice 模块）已委托 `DeliveryDedupStore`；生产环境注入 `RedisDeliveryDedupStore`，用 Redis `SET NX EX` 做跨节点去重并通过 TTL 自动回收（ASSESSMENT P0-5 已修复）。

`MessagePushServiceImpl` 已通过 `PushStateStore` 使用 Redis 维护 attempt 与 delivery state（2026-07-11，ASSESSMENT P4-24）。同一 `serverMsgId` 的状态放入一个 Redis HASH，Lua claim 会先拒绝 `ONLINE_CONFIRMED` / `READ`，再原子检查并写入 `attempt:{userId}`；多 postman 副本中只有一个能调用厂商推送。状态默认保留 24 小时，可用 `CHEESEIM_PUSH_STATE_TTL_SECONDS` 调整。

## 5. 离线推送 5 厂商

每个 provider 真实 SDK 调用，`enabled:false` 默认，`@ConditionalOnProperty` 装配：

| Provider | 文件 | SDK |
| --- | --- | --- |
| APNs | `provider/APNsPushProvider.java:36` | pushy，同步 `sendNotification().get()` |
| FCM | `provider/FCMPushProvider.java` | Firebase Admin |
| Huawei | `provider/HuaweiPushProvider.java` | HuaweiPush SDK |
| Xiaomi | `provider/XiaomiPushProvider.java` | MiPush SDK |
| JPush | `provider/JPushPushProvider.java:104` | `jpushClient.sendPush(payload)` |

`OfflinePushServiceImpl.selectPushProvider`（line 284）按优先级 O(n) 选择可用 provider。

## 6. 离线推送 fan-out

- `OfflinePushServiceImpl.java:58` 用 `CompletableFuture.runAsync` 并发 fan-out
- ⚠️ 使用 **common ForkJoinPool**（无显式 executor），1M 级 fan-out 会饿死其它 CompletableFuture 用户
- 日推送计数通过 `PushStateStore.claimDailyQuota` 的 Redis Lua 在发送前原子预占，按用户/自然日计数并在次日自动过期；厂商全部失败时归还配额，多副本不会越过上限

## 7. Kafka 序列化绕过 QueueAdapter（**已修复 2026-07-07**，P0-6）

旧：`DeliveryEventListener.emitOfflinePushIfNeeded` 直连 `kafkaTemplate.send` 旁路 `QueueAdapter`，切回 Chronicle 队列模式时离线推送失效。
新：经 `OfflinePushEventProducer`（postman 模块新增）通过 `QueueAdapter.send(OFFLINE_PUSH, userId, bytes)` 投递，Chronicle / Kafka 两种 `cheeseim.queue.type` 后端**端到端一致**。

`QueueAdapter` 抽象层同时修复端到端不兼容（ASSESSMENT P1-6 根因）：

- `KafkaQueueAdapter.subscribe` 的反序列化路径**对齐 `ChronicleQueueAdapter.deserialize`**：`byte[]` 透传、`Message`/`HistoryEvent`/`OfflinePushEvent` 走 protobuf 原生解析，其它类型才走 Jackson 兜底。原 Jackson `readValue(String, payloadType)` 对 protobuf 字节就是错的，故以字节级 template + 反序列化原生 protobuf 取代。
- `KafkaQueueConfiguration` 仅提供 `byteKafkaTemplate()`（`ByteArraySerializer`），由 `QueueAutoConfigurer.kafkaQueueAdapter` 注入；不再保留未接入的对象/String Kafka 模板或 `@EnableKafka` 监听路径，防止绕过 `QueueAdapter` 形成第二条消息通道。

消费端 `OfflinePushEventListener.onMessage(byte[])` 仍是 protobuf 字节直收 → `ProtoOfflinePushEventMapper.parse(byte[])`，路径不变。

## 8. 配置

`application-postman.yml` 导入 `module-postman.yml`：
- 5 厂商开关全部 `enabled:false`
- `max-retry=3`、`max-daily-push-count=100`
- 定时任务清理 `scheduled-tasks interval=6h`
- Kafka consumer group `postman-delivery-group`
- 所有厂商配置统一在 `cheeseim.push.*`，由 `CHEESEIM_PUSH_*` 环境变量注入
- actuator + Prometheus
- 控制事件补偿：`cheeseim.control-event.delivery`，默认每秒扫描、每次 100 条、30 秒 claim lease、最多 3 次在线投递；超出次数的离线补齐交由客户端 control-events cursor 拉取。

## 9. 改动评估 checklist

- [ ] 改在线 dispatch 必须同步 postoffice `OnlineDispatcherImpl`
- [ ] 加新推送厂商继承 `PushProvider` 接口，加 `enabled` 开关
- [ ] 改 `OfflinePushEvent` 结构需同步 `IngressEventListener` 发布侧
- [ ] 加跨节点去重 key 必须上 Redis，禁止本地 Set/Map
