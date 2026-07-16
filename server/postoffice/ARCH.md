# postoffice/ARCH.md — 网关事实快照

> TCP/WS 长连接网关 + 在线路由 + 连接管理 + 心跳 + 踢下线。
> 详细架构评估见 `server/docs/architecture/ASSESSMENT.md` P0 项。

## 1. 端口与协议

| 协议 | 端口 | path | 编码 |
| --- | --- | --- | --- |
| TCP | 5148 | – | Protobuf（`TcpEnvelopeCodecSupport`） |
| WS | 5147 | `/ws` | Protobuf Binary WebSocket Frame |
| TLS | 可选 | – | 见 `module-postoffice.yml` |

TCP/WS 共用 `ProtoClientEnvelope` / `ProtoServerEnvelope`；异步在线投递同样经 `ProtoEnvelopeMapper` 编码为 Binary Frame，不再存在 JSON 命令体分支。

TCP/WS frame 默认限制 64 KiB；`ChatMessageHandler` 在 Protobuf 解析前限制 envelope body，并按内容类型限制 content：文本 16 KiB、自定义 64 KiB、富媒体 metadata 32 KiB、其他 32 KiB。所有阈值在 `module-postoffice.yml` 可通过环境变量覆盖。旧普通消息 `READ_RECEIPT` 明确拒绝，已读只允许 typed `CHAT_READ`。

网关不持有 JWT 签名密钥，也不本地解析 access token；认证和 session 有效性统一委托 authcenter 的 ticket / `SessionQueryService` 契约。

## 2. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `ConnectionManager` | `connection/ConnectionManager.java` | 6 张本地 `ConcurrentHashMap` 持连接态，connection/user 分片锁保护 pending 注册、认证提升、移除 |
| `RedisOnlineRouteService` | `service/RedisOnlineRouteService.java` | 在线路由注册/刷新/踢下线（**Lua 原子脚本**，HASH + 双字段：route/heartbeat） |
| `OnlineDispatcherImpl` | `api/OnlineDispatcherImpl.java:67` | Dubbo 投递入口，仅本地 dispatch |
| `KickoffCommandServiceImpl` | `kickoff/KickoffCommandServiceImpl.java` | Dubbo 踢下线接口，按 `gatewayNode` 定向本地执行或节点队列转发 |
| `HeartbeatMessageHandler` | `handler/HeartbeatMessageHandler.java:42` | 心跳处理 |
| `ChatReadMessageHandler` | `handler/ChatReadMessageHandler.java` | 唯一已读命令入口：认证、payload 校验、调用共享 readSeq 状态服务并返回 typed ACK |
| `ChatDeliveryMessageHandler` | `handler/ChatDeliveryMessageHandler.java` | 设备送达高水位入口：校验当前 device、调用 `DeliveryStateService`，不接受网关 write 结果冒充 ACK |
| `ChatTypingMessageHandler` | `handler/ChatTypingMessageHandler.java` | 输入中命令入口：认证、payload 校验、调用短 TTL `TypingStateService`；不进入普通消息链路 |
| `BusinessMessageExecutor` | `server/BusinessMessageExecutor.java` | connection hash 分片的有界单线程队列；业务命令离开 Netty EventLoop，同连接保序，满载返回 503 |
| `ChatRevokeMessageHandler` | `handler/ChatRevokeMessageHandler.java` | typed 撤回入口：认证、调用 `MessageMutationService`、返回 ACK 并触发在线通知 |
| `ControlNotificationDispatcher` | **在 postman** | 已读、撤回与输入中均经共享控制通知契约按 `gatewayNode` 跨节点投递；可靠补偿由 postman outbox scheduler 执行 |
| `TcpEnvelopeEncoder` / `Decoder` | `codec/` | Protobuf wire 编解码 |

## 3. 路由表契约

- 存储：Redis HASH key `online:user:{userId}`（参见 `RedisKeys.onlineUser`），30min TTL
  - Field `route:{deviceId}` = `RouteSnapshot` 的 JSON（注册/重连时整体覆盖）
  - Field `heartbeat:{deviceId}` = 心跳时间戳字符串（被 `refresh` 高频更新，独立字段避免每次心跳重序列化整条 JSON）
- 原子性：`register` / `refresh` / `unregister` 均走单脚本 Lua（参见 `RedisOnlineRouteService`），消除旧读改写竞态（ASSESSMENT P0-3 已修复）
- 路由是跨节点共享真相，读写都直连 Redis；不得接入业务 CacheStore 或本地 L1
- `findByUser` 走 `HGETALL`，Java 侧合并 `route:` / `heartbeat:` 双字段，按 `deviceId` 排序
- `gatewayNode` 经 `NodeIdentityProvider` 写入真实节点 ID；all-in-one 可使用进程内默认值，cluster 必须显式配置稳定 node-id（ASSESSMENT P0-1）
- postman 按 `gatewayNode` 分组路由，通过 Redis 节点可靠队列投递到正确节点：生产者统一写
  `NodeQueueMessage` envelope；消费者用 Lua 将 ready 原子领取到 processing HASH，并在 ZSET 记录 60 秒租约，
  成功 ACK，失败原子重入队；同 node-id 的重启/替代实例每 5 秒回收过期 claim，超过 5 次进入 dead
- ready 最大 100,000 条、processing 最大 10,000 条、dead 最大 10,000 条，三者空闲 TTL 均为 24 小时。
  ready 满时生产者明确失败；重试/恢复遇 ready 满时保留 processing claim 并续租，不丢消息；dead 满时淘汰最老死信。
  因此永久下线节点的在途 key 最迟 24 小时释放，恢复同 node-id 后可继续处理
- `OnlineDispatcherImpl.java:67` 仍只做本地连接查找；跨节点命中由 postman 的 `gatewayNode` 分组 + `NodeDeliveryPoller` 节点队列保证

## 4. 多端登录策略

`module-postoffice.yml`：`multiLoginStrategy: SAME_TERMINAL_KICK`、`maxConnectionsPerUser: 10`、`timeoutMs: 300000`。当前是**每节点**独立计数，跨节点超限不会触发；修复见 ASSESSMENT P4-23。

## 5. 连接状态机

`ConnectionState`：`PENDING → AUTHENTICATED → CLOSING → CLOSED`，本地内存维护，无持久化。

## 6. 投递去重

`ConnectionManager.markDeliveryIfAbsent` 委托给 `DeliveryDedupStore`：

- 生产环境注入 `RedisDeliveryDedupStore`，使用 Redis 单命令 `SET <key> 1 NX EX <ttl>` 做原子 mark-if-absent：
  - 跨节点共享：多 postoffice 节点共用同一 Redis，跨节点的重复推送也会被去重
  - 无界增长问题修复：每个去重记录一个独立 key，TTL 自动回收，与进程生命期无关
  - Key：`idem:delivery:{serverMsgId}:{userId}:{deviceId|*}`，复用 `RedisKeys.deliveryIdem`
  - TTL 默认 600 秒（`cheeseim.delivery.dedup.ttl-seconds`），覆盖典型客户端重试窗口
  - Redis 异常返回 null 时按 false 处理，调用方走重复分支避免重复推送（保 side-effect-safe）
- 测试环境不注入 `DeliveryDedupStore` 时走 NO-OP 放行，由测试用例自行断言期望的推送次数
- **禁止**把旧的本地 `ConcurrentHashMap.newKeySet()` 重新引入或换另一份本地 Set（根 AGENTS §8、ASSESSMENT P0-5）

## 7. 修复优先级（按 ASSESSMENT）

1. ~~P0-3 路由注册改 Lua 原子~~（已修复 2026-07-06）
2. ~~P0-1 真实 gatewayNode + Redis LIST 按节点投递~~（已修复 2026-07-07）
3. ~~P1 ConnectionManager 分片去 global lock~~（已修复 2026-07-07）
4. ~~P1 踢下线跨节点~~（已修复 2026-07-07，复用 `delivery:node:{nodeId}` 节点队列）
5. ~~P2-16 Netty 业务线程隔离 + bounded queue 背压~~（已修复 2026-07-13）

## 8. 改动评估 checklist

- [ ] 改 `ConnectionManager` 数据结构会同时影响 dispatch / heartbeat / kick 三条链路
- [ ] 改路由表结构需同步 postman 的 `OnlineDispatcher` 调用
- [ ] 改 Protobuf envelope 需评估 sdks/go + CheeseBox 客户端
