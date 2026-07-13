# server/AGENTS.md — Java 服务端开发约束

> 本文件是改 CheeseIM Java 服务端的代理（Claude / Codex / Cursor 等）入门必读。
> 由根 `AGENTS.md` 引导而来 +"server/.claude/rules/"（已废止）升级版。
> 改动前先读：根 `AGENTS.md` → 本文件 → 对应模块 `ARCH.md`（事实快照）→ 必要时跳源码。

## 0. 入门 3 步

1. 读根 `AGENTS.md` 的「架构心智模型」。
2. 读 `server/docs/architecture/ASSESSMENT.md` 的「阻断性问题」与「演进路线」。
3. 改某模块前先读该模块的 `ARCH.md`（如 `postmaster/ARCH.md`），不要凭手册凭旧记忆改源码。

## 1. 模块矩阵

```
server/
├── api-server        HTTP 出入口（REST + Facade 编排）
├── authcenter        鉴权：JWT/refresh/ticket/session/踢下线
├── business          用户/好友/黑名单/群/会话/**同步点业务域（JetCache）
├── common-api        跨模块契约：领域模型 + Protobuf + 事件 + 枚举
├── common-core        基础设施：Mongo Repo + 队列 + JetCache + 序列状态 + Idgen
├── config            application-*.yml 配置集合
├── bootstrap-all      all-in-one 单 JVM 联调入口（推荐）
├── postoffice        TCP/WS 网关 + 在线路由 + 心跳 + 踢下线
├── postbox           MessageSender 入口 + ingress event 发布 + 历史查询
├── postmaster        编排核心：seq 分配 + 历史块持久化 + delivery event 发出
└── postman           投递执行 + 离线推送（APNs/FCM/Huawei/Xiaomi/JPush）
```

### 1.1 模块职责事实（与早期 rules 偏差点已修正）

| 模块 | 职责 | 不要做的事 |
| --- | --- | --- |
| `api-server` | Controller + Facade + Auth Principal | Controller 不调 Mongo Repository；Response 模型不下沉 |
| `authcenter` | **完整**鉴权链：登录/刷新/登出/ticket/踢下线/session 状态 | 不只是"轻量 demo 入口"——先看代码再下判断 |
| `business` | 业务域 + 同步点；历史在 postmaster，连接在 postoffice | 不持有消息历史、不操作连接 |
| `postoffice` | 网关接入 + 在线路由 + 连接管理 + 踢下线 | 不做消息存储、不做 seq 分配 |
| `postbox` | 消息发送 RPC（`MessageSender`）+ ingress event 发布 + 历史查询 RPC | 消息真相在 postmaster 落 Mongo，postbox 只发不存 |
| `postmaster` | seq 分配（`ConversationSeqService`）+ history block 持久化 + delivery event 发布 + 策略引擎 | 不接客户端、不做最终在线投递 |
| `postman` | 在线投递执行 + 离线推送 + 厂商适配 | 不分配 seq、不写历史 |
| `common-api` | 领域模型（`domain/`）+ Protobuf（`proto/` + 生成 `protocol/`）+ 事件（`event/`）+ 枚举 + DTO | **不写业务逻辑**、**不依赖 Spring Data** |
| `common-core` | Mongo Repository 接口与 impl + `QueueAdapter` + `MultiLevelCacheService` + 序列/会话状态机 + 推送发送 | 业务模块不绕过这里直接调 Mongo |
| `config` | `application-*.yml` + `module-*.yml` + `common.yml` | 不写 Java 代码 |
| `bootstrap-all` | 单 JVM 拉起全部模块，Dubbo injvm + Chronicle | 不与 kafka/远程 Dubbo 混用 |

### 1.2 依赖矩阵

```
bootstrap-all → 所有模块
api-server    → authcenter, business, postbox, common-api, common-core
postoffice    → common-api, common-core, authcenter（嵌入式）
postbox       → common-api, common-core
postmaster    → common-api, common-core, business（Dubbo）
postman       → common-api, common-core
authcenter    → common-api, common-core
business      → common-api, common-core
common-core   → common-api
common-api    → 无其它 Java 业务模块
```

禁止反向依赖（业务模块 → common-api 之外的契约循环）。Gradle 实现层校验见根 `build.gradle`。

## 2. 命名规范

| 类型 | 后缀 / 模式 | 禁止 |
| --- | --- | --- |
| Service 接口 | `UserService` | `UserDubboService` / `UserRpcService` |
| Service 实现 | `UserServiceImpl`（`@DubboService` 或 `@Service`） | `UserDubboServiceImpl` |
| Repository | `UserRepository`（接口）+ `UserRepositoryImpl`（Mongo 实现） | `UserDao` |
| DTO | `UserDTO` | `UserVO` 直接当 Service 返回 |
| Request/Response | `CreateUserRequest` / `UserResponse` | `CreateUserReq` |
| 枚举 | `UserStatusEnum`（含 `code`/`desc`/`fromCode`） | `UserStatus` 直接散值 |
| Mongo Document | `UserDoc` 或 `UserDocument` | `UserEntity` |
| 转换器 | `UserConverter`（`toDomain` / `toDocument`） | 在 Service 内联转换 |
| 异常 | `BusinessException` | raw `RuntimeException` 上抛 |
| 方法名 | 动词开头，语义清晰 | `doIt` / `process` / `handle` / `test` |

## 3. 枚举规范

- 业务状态/类型/来源/开关**必须枚举**，禁止散落 `public static final String/Integer`。
- 每个枚举值含 `code` + `desc`，并提供 `fromCode` 反查。
- ≥1B 使用频次的内/外部码统一用 `int code`（如 `ChatType.PRIVATE(1)`），便于 Protobuf `int32` 编码。
- 枚举在 Mongo 持久化优先存 `code`，便于跨语言可读。
- 枚举定义集中在 `common-api/enums/`，禁止业务模块新增枚举到自己的包。

## 4. 分层与依赖方向

```
api-server Controller  ──HTTP──> Facade ──Dubbo──> business / postbox / authcenter Service
                                                          │
                                                          └──> common-core Repository 接口
                                                                   │
                                                                   └──> common-core Repository Impl（Mongo）
                                                                   └──> common-core Cache / Queue / StateStore
```

- **领域对象**（`common-api/business/domain/`）不得 `import org.springframework.data.*`。
- **Document**（`common-core/.../document/`）只在 `common-core/.../mongo/impl/` 出现。
- 转换集中在 `Converter`，禁止 Service 层内联 `toDomain`/`toDocument`。
- HTTP Request/Response 只许在 `api-server/.../controller/` 出现；下层返回领域对象或基础结果。

## 5. Dubbo 规范

- 接口在 `api` 模块（`api-server` 自身接口或 `common-api`）；实现在 `impl` 包，标 `@DubboService`。
- **命名禁带** `Dubbo` / `Rpc`（接口、实现、注入对象三类都禁）。
- `<dubbo:reference>` 用 `@DubboReference(check=false)`，timeout 默认 5000ms，retries 默认 2。
- **幂等接口可重试，写接口必须 `retries=0`** 或显式幂等 key。
- common-api 是 Dubbo 契约唯一锚点；新增 Dubbo 接口需先评估对调用方的兼容影响。
- all-in-one 走 injvm，不开放固定 Dubbo 端口；分模块部署端口见 `server/config/application-*.yml`。

## 6. MongoDB 持久层规范

- 持久化对象统一后缀 `Doc`，标 `@Document("collection_name")` + `@CompoundIndexes`。
- 复合 `_id` 用拼接字符串（如 `{ownerUserId}:{conversationId}`），shard-friendly。
- 查询走 `MongoTemplate` 或 `MongoRepository`，禁止拼接 BSON 字符串。
- 批量写用 `bulkOps`（unordered 默认）；**不要**在循环里逐条 `save`。
- 写热点文档（如 `conversation_sequence`）必须用 `findAndModify $inc` 原子化，禁止 read-modify-write。
- 历史块 (`message_block`) 按 `blockNo = seq / blockSize` 分桶，避免单文档无限增长。
- 索引在 `@CompoundIndexes` 中声明，DDL 变更需在 PR 描述中标注。

## 7. 序列与状态机（重点）

- 会话 seq **必须**走 `ConversationSeqAllocator`（`common-core/.../store/sequence/conversation/`）：
  - Redis Lua 状态机（ALLOCATED/MISS/EXHAUSTED/LOCKED）+ Mongo `findAndModify $inc` 段预分配。
  - 启动时 `ConversationSeqAllocatorConfigurer` 在 `CLUSTER` 模式强制 Redis 守卫。
  - 允许 seq 空洞，禁止 seq 重复/回退。
- **禁止**用通用 `SequenceIdGenerator` / `INCRBY` 替代会话 seq。
- 旧版 `ConversationSequenceAllocator`（process-wide synchronized）仅遗留，新代码不要引用。
- 同一模式可复用做消息 id、通知 id、批次 id（参考 P0 演进项）。

## 8. 队列与缓存

### 8.1 队列（`QueueAdapter`）

- 后端切换：`cheeseim.queue.type=chronicle`（默认，单机文件）→ `=kafka`（集群）。
- **Chronicle 仅 dev/单机**，禁止生产使用。
- Kafka 路径 P0-6 已修复；集群部署用 `application-cluster.yml` 通过环境变量启用 `cheeseim.queue.type=kafka` 与 `spring.kafka.bootstrap-servers`，默认 all-in-one 仍保持 Chronicle。
- Topic 命名集中在 `TopicNames`；`buildQueueKey` 用 `ConversationIdUtil`，保证同会话同一 Kafka 分区。
- 消费者用 `@QueueListener`，批量消费 `batch=true` 优先。

### 8.2 缓存（`MultiLevelCacheService` = L1 Caffeine + L2 Redis/RocksDB）

- 热点读 L1 → L2 → loader → 回填。
- **写操作**：先 DB 后缓存，缓存删除放在 `@Transactional` 的 `afterCommit`，保证不脏读。
- L1 当前**无跨节点失效广播**，已知缺陷（见 ASSESSMENT），写后最长 `localExpireSeconds` 才全集群一致。
- 新增缓存优先 `CacheType.REMOTE`（避免 L1 跨节点脏），多读热点才升级 `BOTH`。

## 9. 配置矩阵（端口/中间件事实）

| 模块 | profile | 配置文件 | 暴露端口 | Dubbo 端口 |
| --- | --- | --- | --- | --- |
| all-in-one | `application-all.yml` | + `module-*` | HTTP 18079 / WS 5147 / TCP 5148 | injvm |
| authcenter | `application-authcenter.yml` | `module-authcenter.yml` | – | 20884 |
| business | `application-business.yml` | `module-business.yml` | HTTP 18085 | 20885 |
| postoffice | `application-postoffice.yml` | `module-postoffice.yml` | WS 5147 / TCP 5148 | 20880 |
| postbox | `application-postbox.yml` | `module-postbox.yml` | – + actuator | 20882 |
| postmaster | `application-postmaster.yml` | `module-postmaster.yml` | – | 20881 |
| postman | `application-postman.yml` | `module-postman.yml` | – + actuator | 20883 |
| api-server | 嵌入 bootstrap-all | – | HTTP 18079 | – |
| cluster overlay | `application-cluster.yml` | 与分模块 profile 叠加 | 沿用模块端口 | 沿用模块端口 |

中间件事实：
- `all-in-one`：Chronicle + injvm Dubbo；Redis 在 P0-1（节点投递队列）/P0-3（路由表）/P0-5（投递去重）之后已是**在线投递链路的硬依赖**——详见 `postoffice/ARCH.md` §3、§6，部署时必须启动。Mongo 仍可选（无消息历史时长会话 × 首次会话查询会失败）。
- 分模块 standalone：Nacos 注册 + 配置中心 + Dubbo 远程；保留既有 `localhost` 便捷默认，仅用于本地单机拆模块联调。
- authcenter 单独启动需要 Mongo 支撑 `user_security_state`；standalone 保留本地 `mongodb://localhost:27017/cheese_im` 便捷默认，cluster 必须由 `AUTHCENTER_MONGODB_URI` 或 `MONGODB_URI` 覆盖。
- 分模块 cluster：在模块 profile 外叠加 `cluster`，并按 Redis 形态追加 `redis-sentinel` 或 `redis-cluster`。`application-cluster.yml` 不提供 `localhost` 默认值，必须通过 `MONGODB_URI`、`KAFKA_BOOTSTRAP_SERVERS`、`NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`REDIS_SENTINEL_*` 或 `REDIS_CLUSTER_*` 配置；JetCache 远端缓存另需 `JETCACHE_REDIS_HOST` / `JETCACHE_REDIS_PORT` 指向可达 Redis endpoint。该 profile 默认 `CHEESEIM_QUEUE_TYPE=kafka`、`cheeseim.conversation-seq.deployment-mode=cluster`。
- 任何新的生产/集群配置不得写死 `localhost:27017` / `localhost:6379`，应走环境变量。

## 10. Protobuf 协议

- 协议源在 `server/common-api/src/main/proto/message_protocol.proto`。
- 改完必须 `./gradlew :common-api:generateProto` 重生成；不要手改 `protocol/` 目录下生成代码。
- 新字段用类型化 nested message，不要用 `int32` 套复杂结构。
- `CHAT_READ/CHAT_REVOKE/FORCE_LOGOUT` 均已有 typed payload；`CHAT_READ` 与 `CHAT_REVOKE` 已接通，撤回历史必须 merge mutation overlay，禁止物理删除原消息块。
- TCP/WS 当前均使用 typed Protobuf envelope，WS 为 Binary Frame；新代码禁止重新引入 JSON 客户端命令路径。

## 11. 测试

- `./gradlew compileJava`：编译校验。
- `./gradlew :{模块}:test`：单模块测试，禁止硬连真实 Mongo/Redis。
- 嵌入式 Mongo（`de.bwaldvogel:mongo-java-server`）或 Mockito mock。
- 队列测试用 `ChronicleQueueAdapter` 的内存实现或 mock `QueueAdapter`。
- 单测覆盖：seq 分配状态机、ConversationIdUtil、策略引擎、Converter；这些是纯逻辑无 IO 的重点。

## 12. 代码风格补丁

- 构造器注入；字段注入禁止。
- `Optional<T>` 不作字段、不作入参，只作返回值。
- 公共方法参数校验优先 `jakarta.validation`，业务层补防御式。
- 日志参数化，禁字符串拼接；不打印 token/password/手机号等敏感信息。
- Lombok `@Data`/`@Builder` 可用，领域对象避免 `@Slf4j`。

## 13. 验证命令速查

```bash
cd server
./gradlew compileJava                                # 编译
./gradlew :common-api:generateProto                  # 协议改动后必跑
./gradlew :api-server:test
./gradlew :business:test
./gradlew :postoffice:test
./gradlew :postmaster:test
./gradlew :postbox:test
./gradlew :postman:test
./gradlew :bootstrap-all:bootRun                    # 联调拉起
```

## 14. 勘误记录

（暂无）
