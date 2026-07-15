# authcenter/ARCH.md — 鉴权层事实快照

> JWT + refresh ticket + WS/TCP ticket + session + 踢下线。
> 早期 rules 把它定位为"轻量 demo 入口"已过时，当前是完整鉴权链。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `SessionLifecycleService` | `session/SessionLifecycleService.java:48` | login / refresh / logout，签发 JWT + UUID refresh |
| `SessionIssueServiceImpl` | `session/SessionIssueServiceImpl.java:29` | WS/TCP ticket 签发 + 原子 consume |
| `SessionRevocationServiceImpl` | `session/SessionRevocationServiceImpl.java:27` | session revoke + 触发 KickoffCommand |
| `ConnectionAuthServiceImpl` | `session/ConnectionAuthServiceImpl.java:33` | 长连接 ticket 鉴权 + session 状态校验 |
| `JwtTokenIssuer` | `auth/JwtTokenIssuer.java:25` | HS256 签发 |
| `AccessTokenService` | `auth/AccessTokenService.java:26` | jjwt 校验 |
| `SessionStateValidator` | `session/SessionStateValidator.java` | active / ban / `tokenVersion` 校验 |

## 2. token 模型

- **access token** = JWT（HS256，stateless，跨节点共享验证）；签名密钥只由 `CHEESEIM_AUTH_JWT_SECRET` 注入 authcenter，启动时必填且至少 32 个字符，其他模块不复制密钥
- **refresh token** = 不透明 UUID，server-side state in Redis（L1+L2 cache），rotation：每次 refresh 后旧 token 失效
- TTL 默认：access 24h，refresh 14d，WS ticket 60s（见 `AuthCenterConfig`）

## 3. 多存储后端

`StateStoreAutoConfigurer`：
- 有 `spring.redis.host` 或 `spring.data.redis.*` → Redis 实现（多节点共享；cluster sentinel/cluster profile 也会命中）
- 无 Redis → RocksDB（**仅单机 dev**）

| 状态 | Redis Key |
| --- | --- |
| session | `userSession:<sid>` / `userSessions:<uid>` set / `deviceSession:<uid>:<did>` |
| WS ticket | `wsTicket:<ticket>` |
| refresh token | `cheese_im:refresh_token:<token>` |
| user security | `UserSecurityRepository` via typed `CacheStore` + Mongo `user_security_state` |

## 4. 已知缺陷

| 缺陷 | 位置 | 影响 |
| --- | --- | --- |
| ~~WS ticket consume 非原子（read-then-write）~~ | ~~`SessionIssueServiceImpl.java:40-51`~~ | **已修复 2026-07-08**：consume 委托 `SessionStateStore.consumeWsTicket`，Redis Lua / RocksDB 同步删除保证一次性 |
| ~~`tokenVersion` 硬编码 `1L`~~ | ~~`SessionTicketService.java:21`~~ | **已修复 2026-07-08**：登录、JWT、WS ticket、session 校验统一使用用户级 `tokenVersion` |
| ~~ban flag 仅存缓存，`loader=null`~~ | ~~`UserSecurityRepository.java:16`~~ | **已修复 2026-07-08**：封禁状态落 Mongo `user_security_state`，缓存 flush 后可回源 |
| session + refresh 写无事务 | `SessionLifecycleService.java:48` | 部分失败留孤儿 |
| `findByUserId` O(session_count) RTT | `RedisSessionStateStore.java:42-55` | kickoffAll 风暴延迟 |

均见 ASSESSMENT §3.2，修复优先级 P3-P4。

## 5. 边界

- authcenter 拥有令牌 / ticket / session / 鉴权，**不持有连接态**（连接在 postoffice）
- postoffice 嵌入式依赖 authcenter 做 ticket 校验
- 踢下线通过 Dubbo `KickoffCommandService` 调 postoffice，跨节点可靠性见 postoffice/ARCH.md §3

## 6. 改动评估 checklist

- [ ] 改 JWT claims 需同步 api-server `AccessTokenSessionResolver` 与 sdks/go
- [ ] 改 ticket 结构需同步 postoffice `ConnectionAuthServiceImpl`
- [x] `tokenVersion` 真正 bump 前不要依赖版本号踢下线语义（2026-07-08 已接入用户级版本）
- [x] 加 ban 持久化必须放 Mongo，不要只存 Redis（2026-07-08 已落 `user_security_state`）
- [x] WS/TCP ticket consume 必须走存储层原子 consume，禁止在 service 层读后再标记 used（2026-07-08 已完成）
