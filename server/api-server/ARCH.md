# api-server/ARCH.md — HTTP 出入口事实快照

> Controller + Facade + Auth Principal，不下沉 Response 模型。

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
- `CurrentPrincipalArgumentResolver`：解析 `SessionPrincipal` 参数
- `ApiExceptionHandler`：统一异常处理

⚠️ 每次 HTTP 都 Dubbo 一次查 session。理论上 JWT 可本地校验，`getByAccessToken` 缓存命中即可避免 Dubbo（`SessionQueryServiceImpl.java:32-39`），但目前仍走 Dubbo。

## 4. 不变量

- **HTTP Request/Response 只在本模块**，下层 Service 返回领域对象或基础结果（根 AGENTS 第 5 条）
- Controller 不调 Mongo Repository（违反分层）
- Controller 不做业务逻辑，业务逻辑在 Facade → Dubbo Service

## 5. 已知缺陷

| 缺陷 | 位置 | 说明 |
| --- | --- | --- |
| `GroupController.list` 吞异常返回空 | `GroupController.java:49` | 掩盖 Dubbo/Mongo 失败，应区分降级与失败 |
| `GroupController.list` N 次 Dubbo + N 次 Mongo | | 无批量，O(n) 调用 |
| `GroupController.resolveGroupId` 检查 `c2:` 死分支 | `GroupController.java:55` | `c2:` 已被 `s:` 替换，可删 |
| 无限流、无幂等 key | 全模块 | ASSESSMENT P4-22 修复项 |

## 6. 改动评估 checklist

- [ ] 加新端点：Controller 不下沉 Response，Facade 不下沉 Document
- [ ] 改鉴权逻辑需同步 postoffice WS/TCP ticket 校验
- [ ] 改 GroupController 不要再依赖 `c2:` 前缀
- [ ] 涉及写操作考虑加幂等 key