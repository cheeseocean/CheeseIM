# authcenter/ARCH.md — 鉴权层事实快照

> JWT + refresh ticket + WS/TCP ticket + session + 踢下线。
> 早期 rules 把它定位为"轻量 demo 入口"已过时，当前是完整鉴权链。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `SessionLifecycleService` | `session/SessionLifecycleService.java` | login / refresh / logout，编排 session 与 refresh token family |
| `SessionIssueServiceImpl` | `session/SessionIssueServiceImpl.java:29` | WS/TCP ticket 签发 + 原子 consume |
| `SessionRevocationServiceImpl` | `session/SessionRevocationServiceImpl.java:27` | session revoke + 触发 KickoffCommand |
| `ConnectionAuthServiceImpl` | `session/ConnectionAuthServiceImpl.java:33` | 长连接 ticket 鉴权 + session 状态校验 |
| `JwtTokenIssuer` | `auth/JwtTokenIssuer.java:25` | HS256 签发 |
| `AccessTokenService` | `auth/AccessTokenService.java:26` | jjwt 校验 |
| `SessionStateValidator` | `session/SessionStateValidator.java` | active / ban / `tokenVersion` 校验 |
| `LoginIdentityVerifier` | `identity/LoginIdentityVerifier.java` | 把外部身份凭据收敛为可信登录主体 |
| `SignedAssertionLoginIdentityVerifier` | `identity/SignedAssertionLoginIdentityVerifier.java` | 校验短期签名 assertion 并一次性消费 jti |

## 2. token 模型

- **access token** = JWT（HS256，stateless，跨节点共享验证）；签名密钥只由 `CHEESEIM_AUTH_JWT_SECRET` 注入 authcenter，启动时必填且至少 32 个字符，其他模块不复制密钥
- **refresh token** = `rt.<familyId>.<secret>` 不透明凭证；Redis/RocksDB 只保存 SHA-256，不保存原 token
- 每个登录 session 只绑定一个 token family；refresh 通过单 key 原子 rotate 一次性消费当前 token，旧 token 再次出现会将整族标记为 compromised 并撤销 session
- refresh 采用 14d 绝对过期时间，轮换不会把有效期继续向后滑动；session 与 family 使用相同绝对期限
- TTL 默认：access 24h，refresh 14d，WS ticket 60s（见 `AuthCenterConfig`）
- **login assertion** = 可信账户域签发的短期 HS256 JWT；必须含 sub/iss/aud/iat/exp/jti，
  默认最大 60 秒且 jti 只可消费一次。密钥独立于 access token，功能默认关闭即拒绝登录

## 3. 多存储后端

`StateStoreAutoConfigurer`：
- 有 `spring.redis.host` 或 `spring.data.redis.*` → Redis 实现（多节点共享；cluster sentinel/cluster profile 也会命中）
- 无 Redis → RocksDB（**仅单机 dev**）

| 状态 | Redis Key |
| --- | --- |
| session | `userSession:<sid>` / `userSessions:<uid>` set / `deviceSession:<uid>:<did>` |
| WS ticket | `wsTicket:<ticket>` |
| refresh token family | `cheese_im:refresh_family:{<familyId>}` HASH，保存 session、当前 hash、已用 hash、generation、状态与绝对过期时间 |
| user security | `UserSecurityRepository` via typed `CacheStore` + Mongo `user_security_state` |
| login assertion replay | `cheese_im:login_assertion:<sha256(jti)>` String，TTL 截止 assertion 过期 |

## 4. 已知缺陷

| 缺陷 | 位置 | 影响 |
| --- | --- | --- |
| ~~WS ticket consume 非原子（read-then-write）~~ | ~~`SessionIssueServiceImpl.java:40-51`~~ | **已修复 2026-07-08**：consume 委托 `SessionStateStore.consumeWsTicket`，Redis Lua / RocksDB 同步删除保证一次性 |
| ~~`tokenVersion` 硬编码 `1L`~~ | ~~`SessionTicketService.java:21`~~ | **已修复 2026-07-08**：登录、JWT、WS ticket、session 校验统一使用用户级 `tokenVersion` |
| ~~ban flag 仅存缓存，`loader=null`~~ | ~~`UserSecurityRepository.java:16`~~ | **已修复 2026-07-08**：封禁状态落 Mongo `user_security_state`，缓存 flush 后可回源 |
| ~~session + refresh 写无事务~~ | ~~`SessionLifecycleService`~~ | **已修复 2026-07-19**：先创建 family，再保存 session；后续失败显式 revoke family，session 与 family 共享绝对期限 |
| refresh 严格复用策略无宽限窗口 | `SessionLifecycleService.refresh` | 并发刷新或服务端成功但响应丢失后用旧 token 重试，会按疑似泄漏撤销整个 session；后续可引入短时、绑定请求指纹的幂等结果 |
| `findByUserId` O(session_count) RTT | `RedisSessionStateStore.java:42-55` | kickoffAll 风暴延迟 |
| assertion 当前为单 HS256 密钥 | `SignedAssertionLoginIdentityVerifier` | 多 issuer/kid 与无共享密钥部署需升级 JWKS/公钥轮换 |
| 仓库内无 assertion 签发端 | 外部账户域 | 生产启用前必须完成账户系统签发与客户端交换流程 |

均见 ASSESSMENT §3.2，修复优先级 P3-P4。

## 5. 边界

- authcenter 拥有令牌 / ticket / session / 鉴权，**不持有连接态**（连接在 postoffice）
- authcenter 不拥有账户密码、验证码、OAuth 和 MFA；它只通过 `LoginIdentityVerifier` 消费账户域的可信证明
- authcenter 内部 session 生命周期与 revoke 直接使用构造器注入，不通过 Dubbo 自调用
- refresh token 状态机属于 common-core 存储 seam；token 签发、session 安全校验与复用处置只在 authcenter 编排
- postoffice 嵌入式依赖 authcenter 做 ticket 校验
- 踢下线通过 Dubbo `KickoffCommandService` 调 postoffice，consumer 显式 `retries=0`；跨节点可靠性见 postoffice/ARCH.md §3

## 6. 改动评估 checklist

- [ ] 改 JWT claims 需同步 api-server `AccessTokenSessionResolver` 与 sdks/go
- [ ] 改 ticket 结构需同步 postoffice `ConnectionAuthServiceImpl`
- [x] `tokenVersion` 真正 bump 前不要依赖版本号踢下线语义（2026-07-08 已接入用户级版本）
- [x] 加 ban 持久化必须放 Mongo，不要只存 Redis（2026-07-08 已落 `user_security_state`）
- [x] WS/TCP ticket consume 必须走存储层原子 consume，禁止在 service 层读后再标记 used（2026-07-08 已完成）
- [x] refresh 必须走 `RefreshTokenStateStore.rotate`，禁止恢复为 token 到 session 的普通缓存映射（2026-07-19 已完成）
- [x] login 必须先经 `LoginIdentityVerifier`，禁止从 HTTP/Dubbo command 的 userId 直接签发 session
- [ ] assertion 切换多 issuer 或非对称密钥时必须设计 kid 轮换窗口和旧 key 下线时序
