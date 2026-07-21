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
├── business          用户/好友/黑名单/群/会话/**同步点业务域（CacheStore）
├── common-api        跨模块契约：领域模型 + Protobuf + 事件 + 枚举
├── common-core        共享 port/model + 业务 Mongo Repo + CacheStore + 序列状态 + Idgen
├── infra-queue        队列运行时：Kafka/Chronicle adapter + listener 装配 + DLT 实现
├── storage-history    消息历史 Mongo adapter：Document + bulk/query + port 转换
├── storage-business   用户/群/会话等业务 Mongo adapter + Document + transaction
├── config            application-*.yml 配置集合
├── bootstrap-all      all-in-one 单 JVM 联调入口（推荐）
├── ops-cli            DLT 等独立运维命令，不开放业务端口
├── postoffice        TCP/WS 网关 + 在线路由 + 心跳 + 踢下线
├── postbox           MessageSender 入口 + ingress event 发布 + 历史查询
├── postmaster        编排核心：seq 分配 + 历史块持久化 + delivery event 发出
└── postman           投递执行 + 离线推送（APNs/FCM/Huawei/Xiaomi/JPush）
```

### 1.1 模块职责事实（与早期 rules 偏差点已修正）

| 模块 | 职责 | 不要做的事 |
| --- | --- | --- |
| `api-server` | 独立 HTTP 入口 + Controller + Facade + Auth Principal | Controller 不调 Mongo Repository；Response 模型不下沉；不扫描 common-core 全量实现 |
| `authcenter` | **完整**鉴权链：登录/刷新/登出/ticket/踢下线/session 状态 | 不只是"轻量 demo 入口"——先看代码再下判断 |
| `business` | 业务域 + 同步点；历史在 postmaster，连接在 postoffice | 不持有消息历史、不操作连接 |
| `postoffice` | 网关接入 + 在线路由 + 连接管理 + 踢下线 | 不做消息存储、不做 seq 分配 |
| `postbox` | 消息发送 RPC（`MessageSender`）+ ingress event 发布 + 历史查询 RPC | 消息真相在 postmaster 落 Mongo，postbox 只发不存 |
| `postmaster` | seq 分配（`ConversationSeqService`）+ history block 持久化 + delivery event 发布 + 策略引擎 | 不接客户端、不做最终在线投递 |
| `postman` | 在线投递执行 + 离线推送 + 厂商适配 | 不分配 seq、不写历史 |
| `common-api` | 领域模型（`domain/`）+ Protobuf（`proto/` + 生成 `protocol/`）+ 事件（`event/`）+ 枚举 + DTO | **不写业务逻辑**、**不依赖 Spring Data** |
| `common-core` | Repository/store port + 队列 port/model + `CacheStore` + 序列/会话状态机 + 推送发送 | 不依赖 Mongo/Kafka/Chronicle adapter 实现 |
| `infra-queue` | Kafka/Chronicle adapter、listener runtime、topic 校验与 Kafka DLT 实现 | 不放业务 listener；不被 feature 源码直接 import |
| `storage-history` | `MessageHistoryRepository` 的 Mongo 实现、历史 Document 与转换 | 不放历史业务规则；不向 feature 暴露 Document/BSON |
| `storage-business` | 业务 Repository/store 的 Mongo 实现、Document 与事务装配 | 不放领域服务；不向 feature 暴露 Document/MongoTemplate |
| `config` | `application-*.yml` + `module-*.yml` + `common.yml` | 不写 Java 代码 |
| `bootstrap-all` | 单 JVM 拉起全部模块，Dubbo injvm + Chronicle | 不与 kafka/远程 Dubbo 混用 |
| `ops-cli` | Kafka DLT 摘要查询、受控单条 redrive 与 Mongo 审计 | 不开放业务 HTTP/Dubbo；不输出 payload；不做无界批量重放 |

### 1.2 依赖矩阵

```
bootstrap-all → 所有模块
ops-cli       → common-api, common-core, infra-queue, storage-business, config
api-server    → authcenter, business, postbox, common-api, common-core
postoffice    → common-api, common-core, infra-queue, authcenter（嵌入式）
postbox       → common-api, common-core, infra-queue, storage-history
postmaster    → common-api, common-core, infra-queue, storage-history, storage-business, business（Dubbo）
postman       → common-api, common-core, infra-queue, storage-business
authcenter    → common-api, common-core, storage-business
business      → common-api, common-core, infra-queue, storage-business
infra-queue   → common-api, common-core
common-core   → common-api
storage-history → common-api, common-core
storage-business → common-api, common-core
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
- 持久化、wire、跨进程及业务状态枚举的每个值含稳定 `code` + `desc`，并提供 `fromCode` 反查。
- 仅用于 HTTP 展示或节点本地生命周期、且没有按 code 序列化消费方的枚举可只含 `desc`；必须在类注释明确“展示/本地枚举，不作为持久化或 wire code”，禁止后续调用 ordinal 充当编码。
- ≥1B 使用频次的内/外部码统一用 `int code`（如 `ChatType.PRIVATE(1)`），便于 Protobuf `int32` 编码。
- 枚举在 Mongo 持久化优先存 `code`，便于跨语言可读。
- 枚举定义集中在 `common-api/enums/`，禁止业务模块新增枚举到自己的包。

## 4. 分层与依赖方向

```
api-server Controller  ──HTTP──> Facade ──Dubbo──> business / postbox / authcenter Service
                                                          │
                                                          └──> common-core Repository 接口
                                                                   │
                                                                   ├──> storage-business 业务 Repository Impl（Mongo）
                                                                   ├──> storage-history 历史 Repository Impl（Mongo）
                                                                   └──> common-core Cache / Queue / StateStore port
```

- **领域对象**（`common-api/business/domain/`）不得 `import org.springframework.data.*`。
- **Document** 只在所属 Mongo adapter 内出现：历史归 `storage-history`，业务域归 `storage-business`。
  feature 模块禁止 import Document/BSON/MongoTemplate。
- 转换集中在 `Converter`，禁止 Service 层内联 `toDomain`/`toDocument`。
- HTTP Request/Response 只许在 `api-server/.../controller/` 出现；下层返回领域对象或基础结果。

## 5. Dubbo 规范

- 接口在 `api` 模块（`api-server` 自身接口或 `common-api`）；实现在 `impl` 包，标 `@DubboService`。
- **命名禁带** `Dubbo` / `Rpc`（接口、实现、注入对象三类都禁）。
- `<dubbo:reference>` 必须显式 `@DubboReference(check=false)`；timeout 默认 5000ms 由全局 consumer 配置统一提供。幂等查询可沿用全局 `retries=2`，写调用必须显式 `retries=0` 或提供幂等 key。Dubbo 3.2 的引用代理是框架注入例外，统一使用 private `@DubboReference` 字段；不要伪装成不生效的构造器参数。
- **幂等接口可重试，写接口必须 `retries=0`** 或显式幂等 key。
- common-api 是 Dubbo 契约唯一锚点；新增 Dubbo 接口需先评估对调用方的兼容影响。
- all-in-one 走 injvm，不开放固定 Dubbo 端口；分模块部署端口见 `server/config/application-*.yml`。

## 6. MongoDB 持久层规范

- 持久化对象统一后缀 `Doc`，标 `@Document("collection_name")` + `@CompoundIndexes`。
- 复合 `_id` 用拼接字符串（如 `{ownerUserId}:{conversationId}`），shard-friendly。
- 查询走 `MongoTemplate` 或 `MongoRepository`，禁止拼接 BSON 字符串。
- 已分片或计划分片集合的 upsert 必须在 Query 中显式携带完整 shard key；不能只把字段写在 Update 中。
- 批量写用 `bulkOps`（unordered 默认）；**不要**在循环里逐条 `save`。
- 写热点文档（如 `conversation_sequence`）必须用 `findAndModify $inc` 原子化，禁止 read-modify-write。
- 历史块 (`message_block`) 按 `blockNo = seq / blockSize` 分桶，避免单文档无限增长。
- 索引在 `@CompoundIndexes` 中声明，DDL 变更需在 PR 描述中标注。

## 7. 序列与状态机（重点）

- 会话 seq **必须**走 `ConversationSeqAllocator`（`common-core/.../store/sequence/conversation/`）：
  - Redis Lua 状态机（ALLOCATED/MISS/EXHAUSTED/LOCKED）+ Mongo `findAndModify $inc` 段预分配。
  - 启动时 `ConversationSeqAllocatorConfigurer` 在 `CLUSTER` 模式强制 Redis 守卫。
  - 允许 seq 空洞，禁止 seq 重复/回退。
- **禁止**用通用自增器 / `INCRBY` 替代会话 seq。
- 会话 seq 只有这一条分配路径；禁止新增通用或本地序列分配器。
- 同一模式可复用做消息 id、通知 id、批次 id（参考 P0 演进项）。

## 8. 队列与缓存

### 8.1 队列（`QueueAdapter`）

- 后端切换：`cheeseim.queue.type=chronicle`（默认，单机文件）→ `=kafka`（集群）。
- **Chronicle 仅 dev/单机**，禁止生产使用。
- Kafka 路径 P0-6 已修复；集群部署用 `application-cluster.yml` 通过环境变量启用 `cheeseim.queue.type=kafka` 与 `spring.kafka.bootstrap-servers`，默认 all-in-one 仍保持 Chronicle。
- Kafka 主 topic（ingress/history/delivery/delivery-outcome/group-fanout/offlinepush）及各自 `.DLT`
  由统一 topic 契约声明；cluster 强制校验，生产默认不授予应用 DDL 权限，由 migration 预创建。
  默认 12 分区、3 副本、minISR 2、retention 7 天，可通过 `KAFKA_TOPIC_*` 覆盖。
- Kafka 批量发送使用事务；`transaction-id-prefix` 必须包含节点唯一部分，多副本不得共用固定 prefix。
- Topic 命名集中在 `TopicNames`；`buildQueueKey` 用 `ConversationIdUtil`，保证同会话同一 Kafka 分区。
- 消费者用 `@QueueListener`，批量消费 `batch=true` 优先。

### 8.2 缓存（`CacheStore` / `CacheRegion`）

- 业务缓存只可经 `CacheStore` 创建类型固定的 `CacheRegion`；底层统一 `StringRedisTemplate` + 显式 JSON 类型。
- **写操作**：先 DB 后缓存，缓存删除放在 `@Transactional` 的 `afterCommit`，保证不脏读。
- 默认只使用 Redis 远端缓存；本地缓存须有明确一致性证明，不能作为通用开关。
- `SessionStateStore`、`ConversationStateStore`、`IdempotencyStore`、seq Lua 等原子状态仍使用专用 Store，不得套入通用缓存。

## 9. 配置矩阵（端口/中间件事实）

| 模块 | profile | 配置文件 | 暴露端口 | Dubbo 端口 |
| --- | --- | --- | --- | --- |
| all-in-one | `application-all.yml` | + `module-*` | HTTP 18079 / WS 5147 / TCP 5148 | injvm |
| authcenter | `application-authcenter.yml` | `module-authcenter.yml` | management 19084 | 20884 |
| business | `application-business.yml` | `module-business.yml` | HTTP 18085 / management 19085 | 20885 |
| postoffice | `application-postoffice.yml` | `module-postoffice.yml` | WS 5147 / TCP 5148 / management 19080 | 20880 |
| postbox | `application-postbox.yml` | `module-postbox.yml` | management 19082 | 20882 |
| postmaster | `application-postmaster.yml` | `module-postmaster.yml` | management 19081 | 20881 |
| postman | `application-postman.yml` | `module-postman.yml` | management 19083 | 20883 |
| api-server | `application-api-server.yml` | `module-api-server.yml` | HTTP 18079 / management 19079 | consumer only |
| ops-cli | `application-ops.yml` | 独立命令进程 | – | – |
| cluster overlay | `application-cluster.yml` | 与分模块 profile 叠加 | 沿用模块端口 | 沿用模块端口 |

中间件事实：
- `all-in-one`：Chronicle + injvm Dubbo；Redis 在 P0-1（节点投递队列）/P0-3（路由表）/P0-5（投递去重）之后已是**在线投递链路的硬依赖**——详见 `postoffice/ARCH.md` §3、§6，部署时必须启动。Mongo 仍可选（无消息历史时长会话 × 首次会话查询会失败）。
- 分模块 standalone：Nacos 注册 + 配置中心 + Dubbo 远程；保留既有 `localhost` 便捷默认，仅用于本地单机拆模块联调。
- authcenter 单独启动需要 Mongo 支撑 `user_security_state`；standalone 保留本地 `mongodb://localhost:27017/cheese_im` 便捷默认，cluster 必须由 `AUTHCENTER_MONGODB_URI` 或 `MONGODB_URI` 覆盖。
- JWT 签名密钥只由 authcenter 的 `CHEESEIM_AUTH_JWT_SECRET` 注入，且必须至少 32 个字符；任何其它模块不得配置或复制 JWT 密钥。all-in-one 与 authcenter standalone 启动前均必须提供该环境变量。
- 分模块 cluster：在模块 profile 外叠加 `cluster`，并按 Redis 形态追加 `redis-sentinel` 或 `redis-cluster`。`application-cluster.yml` 不提供 `localhost` 默认值，必须通过 `MONGODB_URI`、`KAFKA_BOOTSTRAP_SERVERS`、`NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`REDIS_SENTINEL_*` 或 `REDIS_CLUSTER_*` 配置；该 profile 默认 `CHEESEIM_QUEUE_TYPE=kafka`、`cheeseim.conversation-seq.deployment-mode=cluster`。
- Redis Cluster profile 强制使用 database 0；Cluster 不支持 SELECT，命名空间隔离必须依赖 key 前缀/ACL。
- 全局多端策略通过 `CHEESEIM_POSTOFFICE_LOGIN_LEASE_ENFORCE` 启用。发布时必须先升级所有 postoffice
  并 drain 旧连接，再集中设为 true；混部期间禁止开启。
- GROUP_FANOUT 默认 Kafka retention 7 天，完成 job 默认保留 8 天；
  `CHEESEIM_DELIVERY_GROUP_FANOUT_COMPLETED_RETENTION_SECONDS` 必须始终大于实际 topic retention，
  Kafka 模式启动守卫会拒绝不满足该关系的配置。
- DLT 查询/redrive 只允许通过 `ops-cli`；查询不提交 offset，redrive 必须提供 operationId、checksum、
  operatorId 和 reason，并长期写入 `dlt_redrive_audit`。详见 `docs/dlt-runbook.md`。
- 七个独立生产进程显式拥有 Actuator/Prometheus，管理端口固定为 19079–19085，可由
  `CHEESEIM_*_MANAGEMENT_PORT` 覆盖。只暴露 health/info/prometheus；liveness 不包含中间件，
  readiness 包含该模块的 Mongo/Redis。管理端口必须只对 kubelet/Prometheus/运维网开放。
- 所有 `application-{module}.yml` 必须显式 import 带 `on-profile: cluster` 的
  `application-cluster.yml`；仅设置 `spring.profiles.active=cluster` 不会绕过自定义
  `spring.config.name` 自动找到该文件。
- 任何新的生产/集群配置不得写死 `localhost:27017` / `localhost:6379`，应走环境变量。

## 10. Protobuf 协议

- 协议源在 `server/common-api/src/main/proto/message_protocol.proto`。
- 改完必须 `./gradlew :common-api:generateProto` 重生成；不要手改 `protocol/` 目录下生成代码。
- 新字段用类型化 nested message，不要用 `int32` 套复杂结构。
- `CHAT_READ/CHAT_REVOKE/FORCE_LOGOUT` 均已有 typed payload；`CHAT_READ` 与 `CHAT_REVOKE` 均已收敛到核心服务，撤回历史必须 merge mutation overlay，禁止物理删除原消息块。跨节点实时控制通知仍待经统一 postman control dispatch 接通。
- TCP/WS 当前均使用 typed Protobuf envelope，WS 为 Binary Frame；新代码禁止重新引入 JSON 客户端命令路径。

## 11. 测试

- `./gradlew compileJava`：编译校验。
- `./gradlew :{模块}:test`：单模块测试，禁止硬连真实 Mongo/Redis。
- 嵌入式 Mongo（`de.bwaldvogel:mongo-java-server`）或 Mockito mock。
- 队列测试用 `ChronicleQueueAdapter` 的内存实现或 mock `QueueAdapter`。
- 单测覆盖：seq 分配状态机、ConversationIdUtil、策略引擎、Converter；这些是纯逻辑无 IO 的重点。

## 12. 代码风格补丁

- Spring Bean 一律构造器注入，禁止 `@Autowired` / `@Resource` 字段注入。`@DubboReference` 是 Dubbo 代理注入例外，按第 5 节统一处理。
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
