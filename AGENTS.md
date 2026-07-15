# AGENTS.md — CheeseIM 跨 Agent 开发约束

> 本文件是所有 AI 代理（Claude / Codex / Cursor / Cline / Continue 等）进入 CheeseIM 仓库的**唯一入口**。
> 目的：用最少 token 让代理理解仓库全貌、命名分层约束、跨模块契约，避免凭旧记忆乱改代码。
> 与代码冲突时以代码为准，并在文末「勘误记录」追加ault。

## 0. 入门 3 步（每个 session 开始必做）

1. **读 `docs/INDEX.md`**：全仓文档地图，知道哪些是权威/草案/废弃，别把废弃 doc 当事实。
2. **按工作子目录读对应 AGENTS.md**：
   - 改 Java 服务端 → `server/AGENTS.md` + 对应模块 `ARCH.md`
   - 改 Go SDK → `sdks/go/AGENTS.md`
   - 改 CheeseBox → `apps/CheeseBox/AGENTS.md`
3. **做架构级改动前**读 `server/docs/architecture/ASSESSMENT.md`，了解阻断性问题与演进路线，不要触碰未在路线图里的新设计。

**核心原则：不要泛读全仓**。文档已分层，按需加载即可省 token。

---

## 1. 仓库形态

CheeseIM 是**多语言混合 Monorepo**，三块独立工程：

```
server/      Java 17 + Spring Boot 3 + Dubbo 3 + Gradle 多模块（11 个子模块）
sdks/go      通用 IM Client SDK（Go 1.24.2）
apps/        CheeseBox（Go TUI 主联调客户端）
```

- 三块工程**不共享构建**，分别用 `./gradlew` / `go test` / `pnpm` 等。
- **不要给 Go 代码引入 Java 依赖**，反之亦然。
- `server/common-api` 是所有 Java 模块的共享契约（领域模型 + Protobuf + 事件 + 枚举）；任何字段变更需评估对 postoffice/postbox/postmaster/postman/business/authcenter 的影响。

## 2. 架构心智模型（一句话版）

```
Client ──TCP/WS──> postoffice ──> postbox ──ingress event──> postmaster ──delivery event──> postman ──> postoffice ──> Client
                                          │                       │                          │
                                          └──> Mongo history      └──> seq allocate         └──> APNs/FCM/Huawei/Xiaomi/JPush
```

详细链路、模块边界、阻断性问题见 `server/docs/architecture/ASSESSMENT.md`。

## 3. 全局硬约束（不分语言）

1. **不要新增魔法值**：状态/类型/来源/开关必须枚举；Java 枚举需含 `code`/`desc`/`fromCode`。
2. **中文注释优先**：公共类/接口/公共方法/枚举/重要字段必须中文注释，优先解释「为什么这样设计」。
3. **领域与持久化分离**：领域对象（domain）不得依赖 Mongo `Document` / Spring Data MongoDB 反向：领域对象不得 import `org.springframework.data.*`。
4. **构造器注入**，禁止字段注入（`@Autowired` 字段注入）。
5. **不要把 HTTP Request/Response 下沉**：HTTP DTO 只允许存在于 `api-server` Controller 层，下层 Service 返回领域对象或基础结果。
6. **不要降级到 JSON 命令体**：TCP/WS 协议以 `message_protocol.proto` 为准；两者当前均使用 typed Protobuf envelope，WS 使用 Binary Frame。节点内部 Redis 队列 JSON 不属于客户端命令协议。
7. **不要写无意义注释**、不要重复样板注释、不要用注释替代 commit message。
8. **不要尝试单测里硬连真实 Mongo/Redis**：用嵌入式或 mock，CI 不可依赖外部中间件。
9. **会话 seq 只走 `ConversationSeqAllocator`**（Lua + Mongo `$inc`）：禁止用普通 `INCRBY` 或新增第二条分配路径，见 `server/AGENTS.md`。
10. **不要新增 `mongodb://localhost` 等单机配置到默认 profile**：默认 profile 是 `all-in-one`，生产 profile 待落地。新中间件地址优先走环境变量。

## 4. 跨语言约定

### 4.1 命名

| 语言 | 类类型 → 后缀示例 |
| --- | --- |
| Java | `*Service` / `*ServiceImpl` / `*Repository` / `*DTO` / `*Request` / `*Response` / `*Enum` / `*Document` / `*Converter` / `*Exception` |
| Java Dubbo | **禁止** `*DubboService` / `*RpcService` / `doIt` / `process` / `handle` / `test` |
| Go | `CamelCase` 导出，包名小写；接口不加 `I` 前缀 |
| React | `PascalCase` 组件，`camelCase` props |

### 4.2 错误码与异常

- Java：统一 `BusinessException` + `ErrorCodeEnum`（含 `code`/`desc`/`fromCode`），技术异常不要原样暴露到上层。
- Go：返回 `error`，业务错误用 `errors.New` 或 sentinel 错误变量。
- 跨进程：错误码集中在 `common-api`，新错误码先加进枚举再用。

### 4.3 日志与校验

- **参数化日志**，禁止字符串拼接；不打印敏感信息。
- 优先 `jakarta.validation`（Java）或 SDK 自带校验（Go），禁止完全依赖前端校验。
- 记录关键业务节点（鉴权、seq 分配、消息持久化、推送回执）。

## 5. 改动协作流程

1. **小步提交**：一次只改一个模块或一个关注点；跨模块改动按模块分别提交，便于 review。
2. **不主动 commit/push**：除非用户明确要求；commit message 风格见 `git log --oneline -20`。
3. **不修改 `server/.claude/settings.local.json` 与 `.claude/settings.local.json`**：这些是本地权限配置。
4. **跑测试**：完成改动后按 `server/AGENTS.md` / `sdks/go/AGENTS.md` 的「验证」章节跑对应命令，证据先于断言。
5. **不修改协议文件**未与人对齐前：`server/common-api/src/main/proto/message_protocol.proto` 任何字段增删需要先评估所有消费方；改完必须 `./gradlew :common-api:generateProto` 重生成。

## 6. 文档维护协议

- 改动涉及**架构/模块边界/链路流向**：同步更新 `server/docs/architecture/ASSESSMENT.md`。
- 改动涉及**新文件/新文档**：在 `docs/INDEX.md` 登记。
- 改动涉及**协议字段**：同步更新 `server/postoffice/docs/TCP_PROTOCOL.md`。
- 改动涉及**部署端口/中间件**：同步更新 `server/AGENTS.md` 配置矩阵。
- **过时文档**不要直接删，先在 `docs/INDEX.md` 标记状态降级（草案/过程/废弃），下季度评估是否删除。

## 7. 节省 Token 实践

- **不要贴整文件**给 agent 阅读用，先用 `grep`/`glob`/task explore 精确定位再 `read`。
- 改一个模块前，先读该模块 `ARCH.md` 事实快照（如果存在），避免读全部源码。
- 跨模块改动用 `task` 子代理并行探查，主代理只汇总。
- 评估类长文档（ASSESSMENT）只需读章节标题与结论，深入时再按 file:line 跳源码。

## 8. 当前已知「**不要做**」清单

| 行为 | 原因 |
| --- | --- |
| ~~修改 `GatewayNode` 为任意字符串~~ | **已修复 2026-07-07**：`NodeIdentityProvider` 写入真实节点 ID，见 ASSESSMENT P0-1 |
| ~~在 postman 启用群投递但未接通 `GroupFanoutPlanner`~~ | **已修复 2026-07-06**：群扩散闭环见 ASSESSMENT P0-2 |
| ~~用 `RedisOnlineRouteService.register` 增删路由但未改为 Lua~~ | **已修复 2026-07-06**：路由表原子化见 ASSESSMENT P0-3 |
| ~~把 `deliveredMessageKeys` 换成另一份本地 Set~~ | **已修复 2026-07-06**：必须上 Redis，见 ASSESSMENT P0-5 |
| ~~在 `common.yml` 启用 Kafka 又不改 `QueueAdapter` 序列化~~ | **已修复 2026-07-07**：`KafkaQueueAdapter` 反序列化对齐 Chronicle + `byteKafkaTemplate`，见 ASSESSMENT P0-6 / postman ARCH §7 |
| 新增 `ConversationId` 前缀 | 仅允许 `s:/g:/n:/ng:`；不得新增其它前缀 |
| 在 `api-server` Controller 调用 Mongo Repository | 违反分层 |
| 给新消息字段在 `message_protocol.proto` 用 `int32` 装复杂结构 | 用类型化 nested message |
| 在 `MessageSender` 同步链路新增 Dubbo 调用 | 当前已有 3 次，新增需合并 |
| 在 `DeliveryEventListener` 直连 `KafkaTemplate.send` | 必须经 `OfflinePushEventProducer` → `QueueAdapter.send`（见 ASSESSMENT P0-6） |
| 不读 `RouteSnapshot.gatewayNode` 就调 `OnlineDispatcher.dispatchMessage` | 跨节点在线投递需按 node 路由（见 ASSESSMENT P0-1） |

## 9. 验证命令速查

```bash
# Java 服务端编译
cd server && ./gradlew compileJava

# Java 服务端单模块测试
./gradlew :api-server:test
./gradlew :business:test
./gradlew :postoffice:test
./gradlew :postmaster:test
./gradlew :postbox:test
./gradlew :postman:test

# Go SDK
cd sdks/go && go test ./...

# CheeseBox
cd apps/CheeseBox && go test ./... && go run ./cmd/cheesebox

# 端到端联调
# 1. 启动 MongoDB + Redis
# 2. cd server && ./gradlew :bootstrap-all:bootRun
# 3. 两个 CheeseBox 客户端登录不同用户验证
```

## 10. 勘误记录

（暂无）
