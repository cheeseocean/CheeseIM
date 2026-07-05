# postoffice/ARCH.md — 网关事实快照

> TCP/WS 长连接网关 + 在线路由 + 连接管理 + 心跳 + 踢下线。
> 详细架构评估见 `server/docs/architecture/ASSESSMENT.md` P0 项。

## 1. 端口与协议

| 协议 | 端口 | path | 编码 |
| --- | --- | --- | --- |
| TCP | 5148 | – | Protobuf（`TcpEnvelopeCodecSupport`） |
| WS | 5147 | `/ws` | 当前 JSON（`ConnectionManager.sendTransportMessage` 的 ObjectMapper 分支） |
| TLS | 可选 | – | 见 `module-postoffice.yml` |

⚠️ WS 当前是 JSON，与 TCP 不一致。新代码不要再加 JSON 路径，统一向 Protobuf 收敛（根 AGENTS 第 6 条）。

## 2. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `ConnectionManager` | `connection/ConnectionManager.java:39` | 6 张本地 `ConcurrentHashMap` 持连接态，`addConnection/removeConnection` 用 `synchronized` |
| `RedisOnlineRouteService` | `service/RedisOnlineRouteService.java:25,35,47` | 在线路由注册/刷新/踢下线（**非原子 RMW**） |
| `OnlineDispatcherImpl` | `api/OnlineDispatcherImpl.java:67` | Dubbo 投递入口，仅本地 dispatch |
| `KickoffCommandServiceImpl` | `kickoff/KickoffCommandServiceImpl.java:8` | Dubbo 踢下线接口 |
| `HeartbeatMessageHandler` | `handler/HeartbeatMessageHandler.java:42` | 心跳处理 |
| `TcpEnvelopeEncoder` / `Decoder` | `codec/` | Protobuf wire 编解码 |

## 3. 路由表契约

- 存储：Redis hash key `RouteSnapshot` list + 30min TTL
- 使用方：`OnlineDispatcherImpl.java:67` 仅取 `userId` 查本地连接，**`gatewayNode` 字段当前未被任何消费者读取**
- `gatewayNode` 在 `ConnectionManager.java:486` 硬编码 `"postoffice"`，是 P0 阻断性问题（ASSESSMENT P0-1）
- 不要把 `gatewayNode` 改成任意字符串；需配合 postman 路由整体修复

## 4. 多端登录策略

`module-postoffice.yml`：`multiLoginStrategy: SAME_TERMINAL_KICK`、`maxConnectionsPerUser: 10`、`connectionTimeoutMs: 300000`。当前是**每节点**独立计数，跨节点超限不会触发；修复见 ASSESSMENT P4-23。

## 5. 连接状态机

`ConnectionState`：`PENDING → AUTHENTICATED → CLOSING → CLOSED`，本地内存维护，无持久化。

## 6. 投递去重

`ConnectionManager.markDeliveryIfAbsent` 用 `deliveredMessageKeys = ConcurrentHashMap.newKeySet()`，无界本地 Set。**禁止**换另一份本地 Set，必须上 Redis（ASSESSMENT P0-5）。

## 7. 修复优先级（按 ASSESSMENT）

1. P0-1 真实 gatewayNode + Dubbo 服务组路由 / per-node topic 直投
2. P0-3 路由注册改 Lua 原子
3. P1 ConnectionManager 分片去 global lock
4. P1 踢下线跨节点

## 8. 改动评估 checklist

- [ ] 改 `ConnectionManager` 数据结构会同时影响 dispatch / heartbeat / kick 三条链路
- [ ] 改路由表结构需同步 postman 的 `OnlineDispatcher` 调用
- [ ] 改 Protobuf envelope 需评估 sdks/go + CheeseBox 客户端