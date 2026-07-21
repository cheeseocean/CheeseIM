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

`CHAT_SEND` 只允许 TEXT/IMAGE/VOICE/VIDEO/FILE/LOCATION/CUSTOM。网关覆盖 sender/source/platform，并清除 options/serverMsgId/time/status/seq 等服务端字段；客户端不能伪装 SYSTEM/ADMIN、系统通知或改变历史/投递策略。postbox 的结构化发送错误码由网关原样写入错误 envelope。

网关不持有 JWT 签名密钥，也不本地解析 access token；认证和 session 有效性统一委托 authcenter 的 ticket / `SessionQueryService` 契约。

`CHAT_SEND` 与一次性 ticket 鉴权都是有副作用调用，Dubbo consumer 显式 `retries=0`：
消息重试必须由后续 clientMsgId inbox 提供业务幂等，一次性 ticket 失败则由客户端重新签票，禁止依赖框架透明重试。

## 2. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `ConnectionManager` | `connection/ConnectionManager.java` | 6 张本地 `ConcurrentHashMap` 持连接态，connection/user 分片锁保护 pending 注册、认证提升、移除 |
| `RedisOnlineRouteService` | `service/RedisOnlineRouteService.java` | 在线路由注册/刷新/踢下线（**Lua 原子脚本**，HASH + 双字段：route/heartbeat） |
| `OnlineDispatcherImpl` | `api/OnlineDispatcherImpl.java:67` | Dubbo 投递入口，仅本地 dispatch |
| `DeliveryWriteFinalizer` | `delivery/DeliveryWriteFinalizer.java` | 超出同步期限的 ChannelFuture 在有界业务线程池完成 dedup commit/abort |
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
  - Field `heartbeat:{deviceId}` = 最近持久化心跳
  - Field `connection:{deviceId}` = 当前连接身份，保护批刷、注销和 stale cleanup 不覆盖新连接
- 原子性：`register` / `refresh` / `unregister` 均走单 key Lua；refresh/unregister 在 Redis 内比较
  connectionId。心跳先在 `RouteHeartbeatBuffer` 按 connection 合并，默认 60 秒持久化一次，再用两阶段
  pipeline 先刷用户主路由、后刷成功项的 session 反向索引
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

`module-postoffice.yml` 的 `connection` 是唯一配置入口：默认节点 TCP + WS 总连接上限 100,000、
单用户节点内上限 10、`SAME_TERMINAL_KICK`、空闲超时 300 秒。总上限在 pending 注册前用 CAS 抢占，
包含未认证连接；单用户上限在认证提升的 user 分片锁内执行。当前仍是**每节点**策略，跨节点超限不会触发。

TCP/WS 各自设置默认 32/64 KiB Netty write-buffer watermark。所有业务写统一经
`ConnectionManager.writeMessageToConnection` 检查 `channel.isWritable()`；不可写视为投递失败并进入
既有 claim abort/retry/补偿，禁止继续堆积 outbound buffer。

跨节点替换命令使用 `KickoffCommand.connectionId` 精确定位旧连接。消费端只在该字段缺失时才回退到
device/session/user 范围；精确目标已消失时 NOOP，禁止迟到命令误踢同设备的新连接。路由快照同步发布
稳定 `platformId`，作为全局多端策略的判定元数据。

全局策略由 `LoginLeaseStore` 承担。Redis 实现按 tenant/user 安全 hash tag 保存 active ZSET 与 metadata
HASH，CLAIM Lua 原子执行过期清理、同 device 唯一、四种策略、全局上限和 generation fencing。
RENEW/RELEASE 必须携带 connectionId + generation。节点每 60 秒主动批量续租，lease 默认 180 秒。

`cheeseim.postoffice.login-lease.enforce` 默认 false，这是滚动升级门禁而非本地降级开关：所有节点先升级
并确认支持精确 generation kickoff，随后 drain 旧连接，再集中开启。cluster 启用后 Redis 不可用必须
拒绝新登录，禁止回退节点本地策略制造 split-brain。

## 5. 连接状态机

`ConnectionState`：`PENDING → AUTHENTICATED → CLOSING → CLOSED`，本地内存维护，无持久化。

已认证连接在 WS ticket 成功消费时建立本地 session 复核租约，默认 60 秒。租约内聊天、回执、控制命令和
心跳只校验本地上下文；到期后同一连接单飞调用一次 authcenter `isSessionValid`。主动撤销仍以跨节点
kickoff 为主，周期复核只兜底丢通知，配置为 `cheeseim.postoffice.session-validation.interval-ms`。

## 6. 投递去重

`OnlineDispatcherImpl` 通过 `DeliveryDedupStore` 执行 claim/commit/abort：

- 生产环境注入 `RedisDeliveryDedupStore`，使用单 key Lua 维护短租约 claim 与 delivered 终态：
  - 跨节点共享：多 postoffice 节点共用同一 Redis，跨节点的重复推送也会被去重
  - 无界增长问题修复：每个去重记录一个独立 key，TTL 自动回收，与进程生命期无关
  - Key：`idem:delivery:{serverMsgId}:{userId}:{deviceId|*}`，复用 `RedisKeys.deliveryIdem`
  - TTL 默认 600 秒（`cheeseim.delivery.dedup.ttl-seconds`），覆盖典型客户端重试窗口
  - Redis 异常返回 `UNAVAILABLE`，调用方明确失败，禁止当作已投递
- `ConnectionManager.writeMessageToConnection` 返回真实 `ChannelFuture`；只有 future 成功后才能 commit，失败必须 abort
- 单次 dispatch 的全部连接共用一个总等待期限（默认 1 秒），不会按设备数线性放大 RPC 超时
- 超时返回 `WRITE_PENDING`，由 `DeliveryWriteFinalizer` 在 Netty EventLoop 之外异步提交/释放；线程数和队列均有界，过载时依赖 claim TTL 恢复
- 节点队列只有在每个连接均成功或连接已消失时 ACK；部分成功会在重试时由 per-connection dedup 跳过已完成设备，不再“任一成功即 ACK 全部”
- 聊天投递在 ACK 节点 processing claim 前发布 `DELIVERY_OUTCOME`；发布失败保留 claim 等待租约恢复，
  重试耗尽进入 dead 前发布 `FAILED_FINAL`。新路由通过 `deliveryOutcomeVersion=1` 声明该能力
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
- [ ] 新增投递结果必须进入 `DispatchResultCode`，禁止在 postoffice/postman 复制字符串分类
