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

## 2. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `ConnectionManager` | `connection/ConnectionManager.java` | 6 张本地 `ConcurrentHashMap` 持连接态，connection/user 分片锁保护 pending 注册、认证提升、移除 |
| `RedisOnlineRouteService` | `service/RedisOnlineRouteService.java` | 在线路由注册/刷新/踢下线（**Lua 原子脚本**，HASH + 双字段：route/heartbeat） |
| `OnlineDispatcherImpl` | `api/OnlineDispatcherImpl.java:67` | Dubbo 投递入口，仅本地 dispatch |
| `KickoffCommandServiceImpl` | `kickoff/KickoffCommandServiceImpl.java` | Dubbo 踢下线接口，按 `gatewayNode` 定向本地执行或节点队列转发 |
| `HeartbeatMessageHandler` | `handler/HeartbeatMessageHandler.java:42` | 心跳处理 |
| `ChatReadMessageHandler` | `handler/ChatReadMessageHandler.java` | 已读命令入口：认证、payload 校验、调用共享 readSeq 状态服务并返回 typed ACK |
| `BusinessMessageExecutor` | `server/BusinessMessageExecutor.java` | connection hash 分片的有界单线程队列；业务命令离开 Netty EventLoop，同连接保序，满载返回 503 |
| `ReadNotifyDispatcher` | `delivery/ReadNotifyDispatcher.java` | 已读位点推进后，单聊通知 peer + 阅读者其他端，群聊只同步阅读者其他端；按 gatewayNode 跨节点投递 |
| `ChatRevokeMessageHandler` | `handler/ChatRevokeMessageHandler.java` | typed 撤回入口：认证、调用 `MessageMutationService`、返回 ACK 并触发在线通知 |
| `RevokeNotifyDispatcher` | `delivery/RevokeNotifyDispatcher.java` | 单聊/普通群撤回按 gatewayNode 通知在线端；超级群以 mutation 增量接口 + 历史 overlay 同步收敛 |
| `TcpEnvelopeEncoder` / `Decoder` | `codec/` | Protobuf wire 编解码 |

## 3. 路由表契约

- 存储：Redis HASH key `online:user:{userId}`（参见 `RedisKeys.onlineUser`），30min TTL
  - Field `route:{deviceId}` = `RouteSnapshot` 的 JSON（注册/重连时整体覆盖）
  - Field `heartbeat:{deviceId}` = 心跳时间戳字符串（被 `refresh` 高频更新，独立字段避免每次心跳重序列化整条 JSON）
- 原子性：`register` / `refresh` / `unregister` 均走单脚本 Lua（参见 `RedisOnlineRouteService`），消除原 `MultiLevelCacheService` 「读-改-写」竞态与 L1 无失效广播问题（ASSESSMENT P0-3 已修复）
- 不再使用 `MultiLevelCacheService` L1 Caffeine：路由是跨节点共享真相，L1 本地缓存会让多节点 1-5min 不一致；读写都直连 Redis
- `findByUser` 走 `HGETALL`，Java 侧合并 `route:` / `heartbeat:` 双字段，按 `deviceId` 排序
- `gatewayNode` 经 `NodeIdentityProvider` 写入真实节点 ID（配置或 UUID），不再是硬编码（ASSESSMENT P0-1，**已修复 2026-07-07**）
- postman 按 `gatewayNode` 分组路由，通过 Redis LIST `delivery:node:{nodeId}` 投递到正确节点
- `OnlineDispatcherImpl.java:67` 仍只做本地连接查找；跨节点命中由 postman 的 `gatewayNode` 分组 + `NodeDeliveryPoller` 节点队列保证

## 4. 多端登录策略

`module-postoffice.yml`：`multiLoginStrategy: SAME_TERMINAL_KICK`、`maxConnectionsPerUser: 10`、`connectionTimeoutMs: 300000`。当前是**每节点**独立计数，跨节点超限不会触发；修复见 ASSESSMENT P4-23。

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
