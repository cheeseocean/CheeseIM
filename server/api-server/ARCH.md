# api-server/ARCH.md — HTTP 出入口事实快照

> Controller + Facade + Auth Principal，不下沉 Response 模型。

生产入口为 `ApiServerApplication`，加载 `application-api-server.yml`，HTTP 18079、management 19079。
它只扫描 `com.cheeseocean.im.apiserver`，通过 non-transitive common-core/infra-state 依赖分别复用幂等 port
和最小 Redis adapter，并设置 `cheeseim.state.auto-config-enabled=false`；禁止改回扫描
`com.cheeseocean.im.common` 或启用完整 state runtime，否则会把 session/conversation/seq/RocksDB 装入无状态 API。

## 1. Controller 总览

| Controller | 基路径 | 端点 |
| --- | --- | --- |
| `AuthController` | `/api/auth` | login / refresh / logout / kickoff device / kickoff-all |
| `WsTicketController` | `/api/im/ws-ticket` | 申请长连接 ticket |
| `UserController` | `/api/im/user` | settings GET/PUT |
| `FriendController` | `/api/im/friends` | list / requests(in/out) / send / accept / reject / cancel |
| `BlacklistController` | `/api/im/blacklist` | list / POST / DELETE |
| `ConversationController` | `/api/im/conversations` | list / all / batch / ids / ids/hash / sync/incremental / max-seqs / read-snapshots / not-notify / pinned / PUT / DELETE / sync/pull / read-seq / messages |
| `GroupController` | `/api/im/groups` | list（**当前吞异常返回空**，line 49，掩盖失败） |

## 2. Facade 编排

| Facade | 文件 | 说明 |
| --- | --- | --- |
| `FriendFacade` | | 友链编排 |
| `ConversationFacade` | `ConversationFacade.java`（353 行） | 编排 ConversationService + ConversationSyncService + PermissionService + HistoryQueryService + UserInfoService |
| `UserFacade` | | 用户设置 |

## 3. 鉴权与 Principal

- `AccessTokenSessionResolver`（`auth/AccessTokenSessionResolver.java:13`）：解析 `Authorization: Bearer <token>`，Dubbo 调 `sessionQueryService.getByAccessToken`
- `ApiAuthenticationInterceptor`：覆盖 `/api/**`，除显式 `@PublicApi` 外默认要求有效 session，并把 principal 缓存在 request attribute
- `CurrentPrincipalArgumentResolver`：解析 `SessionPrincipal` 参数
- `ApiExceptionHandler`：统一异常处理
- `ApiRateLimitFilter`：`/api/**` 入口 Redis 固定窗口限流，默认每来源 120 请求/60 秒；使用 Lua 原子设置计数与 TTL，Redis 不可用时按可用性策略放行。可通过 `CHEESEIM_API_RATE_LIMIT_ENABLED`、`CHEESEIM_API_RATE_LIMIT_REQUESTS_PER_WINDOW`、`CHEESEIM_API_RATE_LIMIT_WINDOW_SECONDS` 覆盖。
- 限流来源默认只取 socket peer；只有受控代理会覆盖转发头时才设置
  `CHEESEIM_API_RATE_LIMIT_TRUSTED_PROXY_HOPS`。Redis 故障有节点本地短熔断，避免每个请求等待 Redis timeout。
- `ApiIdempotencyInterceptor`：携带 `Idempotency-Key` 的、声明 `SessionPrincipal` 的 POST/PUT/DELETE 以 `userId + method + path + key 指纹` 做跨副本 SETNX 占位，重复请求返回 409；默认 TTL 300 秒，可通过 `CHEESEIM_API_IDEMPOTENCY_ENABLED`、`CHEESEIM_API_IDEMPOTENCY_TTL_SECONDS` 覆盖。当前不缓存首次响应。
- 当前只有 `/api/auth/login`、`/api/auth/refresh` 标记为 `@PublicApi`；新增公开端点必须显式标注并单独安全评审
- logout、kickoff device、kickoff-all 的目标 user/session 从当前 principal 推导；兼容请求中的 ID 只用于所有权比对，不能作为授权主体

⚠️ 每次 HTTP 都 Dubbo 一次查 session。理论上 JWT 可本地校验，`getByAccessToken` 缓存命中即可避免 Dubbo（`SessionQueryServiceImpl.java:32-39`），但目前仍走 Dubbo。

## 4. 不变量

- **HTTP Request/Response 只在本模块**，下层 Service 返回领域对象或基础结果（根 AGENTS 第 5 条）
- Controller 不调 Mongo Repository（违反分层）
- Controller 不做业务逻辑，业务逻辑在 Facade → Dubbo Service
- `/api/**` 采用默认拒绝；禁止以“Controller 恰好声明了 `SessionPrincipal`”作为接口是否鉴权的开关
- 应用限流是边缘防护后的第二道保护；不能替代 LB/Ingress 的连接、带宽、body 大小和 DDoS 限制

## 5. 已知缺陷

| 缺陷 | 位置 | 说明 |
| --- | --- | --- |
| `GroupController.list` 吞异常返回空 | `GroupController.java:49` | 掩盖 Dubbo/Mongo 失败，应区分降级与失败 |
| `GroupController.list` N 次 Dubbo + N 次 Mongo | | 无批量，O(n) 调用 |
| 幂等不缓存首次响应 | 全模块 | 当前重复 `Idempotency-Key` 返回 409；如客户端需要断线重试时重放原响应，需扩展为状态/响应缓存协议 |
| 账户签发端待接入 | `AuthController.login` | 服务端已强制可信 identity assertion；生产账户域仍需实现签发/交换流程 |

## 6. 改动评估 checklist

- [ ] 加新端点：Controller 不下沉 Response，Facade 不下沉 Document
- [ ] 改鉴权逻辑需同步 postoffice WS/TCP ticket 校验
- [ ] 新业务接口应声明 `SessionPrincipal` 以直接使用当前主体；即使遗漏参数，统一拦截器也必须默认鉴权
- [ ] 新增 `@PublicApi` 必须确认该端点确实不依赖 access token，禁止为绕过接入问题临时标注
