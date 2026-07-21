# CheeseIM Server 设计评审与生产化演进账本

> 状态：权威  
> 评审日期：2026-07-19  
> 评审范围：`server/` 全模块  
> 目标：形成面向百万 DAU 的设计基线、阻断项清单和可持续的小任务执行账本。  
> 维护规则：每完成一个小任务，必须补充“实现复核”，回答合理性、内聚性和后续价值三个问题。

## 1. 综合结论

CheeseIM Server 已经具备可生产化演进的架构骨架，不属于纯 Demo：

- postoffice / postbox / postmaster / postman 的职责方向清晰；
- TCP/WS 已统一为 typed Protobuf；
- 会话 seq 使用 Redis Lua + Mongo 号段预分配；
- 普通群写扩散、超级群读扩散的策略方向合理；
- 历史块、mutation overlay、控制事件 outbox、设备送达高水位均有明确模型；
- all-in-one 保留了较好的本地开发体验。

当前仍不能宣称已经达到百万 DAU 生产质量。主要差距不是功能数量，而是：

1. 安全身份边界尚未闭环；
2. 消息处理缺少端到端幂等和明确的部分失败语义；
3. Redis Cluster、跨节点投递仍有真实阻断项；
4. 模块数据所有权和基础设施装配隔离不足；
5. 生产部署、迁移、可观测、DLT 回放、备份恢复和容量证据不完整。

准确定位应为：**集群化架构骨架 + 部分生产级组件**。

## 2. 容量目标的统一口径

百万 DAU 不等于百万长连接。容量评估必须同时声明：

- DAU；
- 峰值在线连接数及在线率；
- 客户端心跳周期；
- 平均与峰值发送 QPS；
- 单聊/普通群/超级群比例；
- 普通群平均成员数和最大成员数；
- delivery 写放大；
- 消息平均体积和附件元数据体积；
- 历史保留周期；
- 可接受的 ACK、持久化、端到端投递 p95/p99；
- 故障恢复时间和允许的数据语义。

建议首个容量基线：

| 维度 | 阶段目标 |
| --- | --- |
| DAU | 1,000,000 |
| 峰值在线率 | 10% |
| 峰值连接数 | 100,000 |
| 峰值发送 QPS | 10,000-20,000 |
| 峰值 delivery QPS | 按群扩散模型单独测量，不能由发送 QPS 直接替代 |
| broker ACK p99 | ≤ 200ms |
| 已接受消息 | 零不可解释丢失、零业务重复 |
| 故障恢复 | Redis/Kafka/Mongo/Postoffice 故障解除后 backlog 回到基线 |

100 万并发连接属于下一档目标，不能与百万 DAU 混用。

## 3. P0 阻断项

### P0-1 身份认证与接口授权仍是 Demo 级

现状：

- ~~登录只提交 userId/platform/device，没有密码、验证码、OAuth 或可信业务系统 assertion；~~
  **A-05B 已修复：登录主体只取可信业务系统短期签名 assertion 的 sub，客户端 userId 不再构成身份依据**；
- ~~logout、kickoffDevice、kickoffAll 没有统一的调用者身份约束；~~ **A-05A 已修复：只能操作当前 principal 所属 session/user**；
- ~~refresh token 签发新 token 后没有原子消费旧 token；~~ **A-04 已修复：单 key 原子 rotate、token family 与复用检测已接入**；
- ~~API 是否鉴权依赖 Controller 参数是否声明 `SessionPrincipal`，不是默认拒绝模型。~~ **A-05A 已修复：`/api/**` 默认鉴权，仅显式 `@PublicApi` 放行**。

目标：

- 接入真实身份源，或只接受可信业务服务签名的登录断言；
- Spring Security 默认拒绝；
- 用户只能管理自己的 session/device；
- 管理操作进入独立 RBAC 与审计链路；
- refresh token 原子 rotate，并支持 token family/reuse detection。

### P0-2 消息入口和消费链缺少端到端幂等

现状：

- 每次发送重试都会生成新的 serverMsgId；
- `(senderId, conversationId, clientMsgId)` 没有 inbox 真相；
- Kafka poll batch 按 key 逐组执行，后组失败会导致已成功组整体重放；
- 重放会再次申请 seq、再次发布 HISTORY/DELIVERY；
- HISTORY 与 DELIVERY 是两个独立发布边界。

目标：

- postbox 建立发送 inbox，重复 clientMsgId 返回首次 ACK；
- postmaster 建立 ingress event inbox；
- 统一 routed-message 事实事件，或使用 Kafka consume-transform-produce 事务；
- Mongo 写仍按 eventId/clientMsgId 幂等；
- 所有故障点均能通过重放测试证明语义。

### P0-3 Redis Cluster 多 key Lua 不满足同槽约束

现状：

- ~~节点队列 ready/processing/leases/dead key 没有共同 hash tag；~~ **B-01 已修复**；
- ~~read/max/unread 多 key Lua 没有共同 hash tag；~~ **B-01 已修复**；
- ~~`redis-cluster` profile 下会触发 `CROSSSLOT`。~~ **静态跨槽点已关闭，真实三主三从集成验证仍待补齐**。

目标：

- 同一原子状态机的 key 使用同一 Redis Cluster hash tag；
- 增加真实 Redis Cluster 集成验证；
- 对全仓 Lua 脚本建立 keys 同槽审计清单。

### P0-4 群消息发送权限缺失

状态：**已于 2026-07-19 完成 A-03 核心闭环**。

- business 提供批量群发送权限契约，一次返回群状态、成员、禁言和 groupType；
- postbox 在写 ingress 前拒绝非法发送，postmaster 在 seq/历史前按批内唯一 sender 防御校验；
- 网关清除客户端伪造的 source/options/服务端字段并限制用户可发送 ContentType；
- 尚未完成 membership version/签名授权断言，权限判定采用 2 秒共享快照窗口。

### P0-5 在线投递成功语义不可靠

现状：

- ~~Netty `writeAndFlush` 后未等待 ChannelFuture 即提交去重；~~ **A-06 已修复：future 成功才 commit，失败 abort，超时异步收口**；
- ~~同一节点多连接只要任一成功就 ACK 整个节点消息；~~ **A-06 已修复：逐连接分类，部分失败保留节点 claim 重试**；
- 节点入队基础设施异常会被转换为“离线推送兜底”；
- 节点 dead queue 没有统一补偿。

目标状态：

```text
QUEUED -> SOCKET_WRITTEN -> CLIENT_DELIVERED
                    \-> RETRY / OFFLINE_FALLBACK / DEAD
```

- ChannelFuture 成功后才能 commit delivery claim；
- connection/device 维度独立终态；
- 基础设施故障与用户离线必须区分；
- dead 进入统一补偿与告警。

## 4. P1 高优先级能力

### 4.1 网关与在线链路

- 心跳不再每次执行两次同步 SessionQuery RPC；
- session 使用本地短租约，revoke/kick 事件主动失效；
- 路由心跳按时间窗口聚合批刷 Redis；
- NodeDeliveryPoller 支持分片、批 claim、批 ACK 和可配置并行度；
- ~~配置 Netty write-buffer watermark 和 `channel.isWritable()` 背压；~~ **C-04 已完成节点级写背压**；
- ~~`maxConnectionsPerUser`、multi-login 策略跨节点一致。~~ **B-04B 已完成 rollout-controlled
  Redis 全局 login lease；需全节点升级并 drain 旧连接后显式开启 enforce**。

### 4.2 群扩散

- 普通群 fanout 从 ingress consumer 拆到独立 fanout topic/worker；
- ~~群成员按游标分页，不在热路径一次性 materialize 全量成员；~~ **C-05A 已完成版本化 epoch 分页**；
- ~~fanout event 携带 membership version；~~ **C-05A 已完成，版本来自发送权限强读上下文**；
- 超级群补在线订阅广播或新消息信号，读扩散不能等同于完全无实时通知；
- 按成员数和活跃度动态选择写扩散/读扩散。

### 4.3 存储与同步

- userMaxSeq/readSeq/deliveredSeq 使用 unordered bulk `$max`；
- 消息历史 upsert 显式携带 shard key；
- 恢复并版本化 Mongo sharding/index migration；
- 建立消息冷热分层、归档和删除策略；
- write-behind 不使用可能阻塞自身 consumer 的回退队列写法；
- History 权限缓存改为有 maximumSize 的 Caffeine。

### 4.4 可靠性和运维

- DLT 有查询、告警、审计、redrive、长期保留和 runbook；
- 每个模块接入 Actuator，但管理端口只在内网暴露；
- readiness 检查自身关键依赖，liveness 不被中间件短暂故障拖死；
- 接入 OpenTelemetry，贯通 requestId/eventId/clientMsgId；
- 建立 Mongo backup/PITR/restore drill；
- 提供 Dockerfile、Helm/Kubernetes、滚动升级与配置 preflight。

## 5. 模块隔离评审

### 5.1 当前合理之处

- feature 模块没有直接 import 其它 feature 的实现包；
- common-api 未依赖 Spring Data；
- 跨模块契约基本集中在 common-api；
- seq、QueueAdapter、CacheStore、控制事件 outbox 等抽象方向合理。

### 5.2 当前不合理之处

`common-core` 在评审时同时承载 Mongo、Redis、Kafka、Chronicle、RocksDB、Web、Dubbo 和 Micrometer；
截至 2026-07-21，Kafka/Chronicle 已迁入 infra-queue，历史/业务 Mongo 已迁入 storage 模块，
剩余主要耦合是 Redis/RocksDB state/cache 与 Dubbo/通知客户端。

建议演进为：

```text
common-kernel
common-contract
infra-queue
infra-state
storage-business
storage-history
storage-auth
```

约束：

- Repository port 不返回 `*Doc`；history port 已由 D-06A 完成，其他新增 port 必须保持同一边界；
- postbox 只依赖 history read port；
- postmaster 只依赖 history write/mutation port；
- authcenter、business 各自拥有数据 adapter；
- 各进程显式 import 所需 starter，不扫描全部 common 基础设施。

## 6. 代码与配置一致性

待统一项：

- 写 Dubbo 调用必须显式 `retries=0`，查询才允许有限重试；
- Kafka consumer group/concurrency/ack-mode 只能有一个权威配置入口；
- `ConversationIdUtil` 移入纯契约/内核模块，文档、queue key、Mongo id、SDK 共用契约测试；
- 删除仍使用 `group:` 的旧 GroupChatPolicy；
- 统一 BusinessException + ErrorCode + HTTP 状态映射；
- 禁止 HTTP 返回内部异常 message；
- GroupController 改批量查询，禁止 N+1 和吞异常返回空列表；
- 高频路径不打印 INFO 级逐消息日志，禁止打印 deviceToken 等敏感字段；
- Gradle 拆分 library/application convention，启用依赖锁定、verification 和重复资源失败。

## 7. 基建缺口

- 已补 B-05A/B 可执行 Mongo 分片 migration 与 conversation 反向偏好读模型，但真实集群 smoke 和备份恢复仍未完成；
- 没有完整 CI workflow；
- 生产 OCI Dockerfile 已落地，Helm/Kubernetes 与镜像供应链验收仍缺；
- 没有 dependency locking、SBOM、SCA、secret scan；
- DLT 查询、审计和受控单条 redrive 已落地，真实 Kafka 故障演练与告警阈值仍缺；
- 没有备份恢复证据；
- 没有百万 DAU/目标连接规模的测试 artifacts；
- 文档中部分“已完成”项与仓库代码不一致。

完成项以后必须满足：

```text
代码 + 自动化验证 + 可运行配置/部署产物 + 文档
```

不能仅以代码类或配置键存在作为“已完成”。

## 8. 分阶段演进计划

### 阶段 A：安全与正确性冻结

1. 统一写 RPC 重试语义；
2. 发送 inbox/clientMsgId 幂等；
3. 群发送权限聚合；
4. refresh token 原子 rotate；
5. AuthController 身份与授权边界；
6. 在线投递 ChannelFuture/多设备终态。

### 阶段 B：集群语义闭环

1. Redis Cluster hash tag；
2. 节点投递失败分类和 dead 补偿；
3. 全局多端策略；
4. Mongo shard migration 与真实集群 smoke；
5. Kafka consumer 配置统一和 DLT redrive。

### 阶段 C：热点拆分

1. 心跳本地租约与批刷；
2. 群 fanout worker；
3. 节点投递分片与批处理；
4. Mongo bulk `$max`；
5. 有界缓存和历史冷热分层。

### 阶段 D：生产交付和容量验收

1. Actuator/OTel/Grafana/告警；
2. Docker/Helm/Kubernetes；
3. CI/SCA/SBOM；
4. 备份恢复；
5. C10K -> C50K -> C100K -> 多节点目标容量；
6. 故障注入和零不可解释丢失/重复验收。

## 9. 小任务执行规则

每个任务只解决一个可独立 review 的关注点。完成后记录：

1. **实现摘要**：改了什么，未改什么；
2. **合理性**：故障语义和边界是否正确；
3. **内聚性**：逻辑是否放在拥有该职责的模块；
4. **一致性**：是否形成或复用唯一入口；
5. **后续价值**：是否为下一阶段提供稳定 seam；
6. **遗留风险**：当前任务不能解决什么；
7. **验证证据**：编译、测试、集成或人工审计结果。

## 10. 执行账本

| ID | 小任务 | 状态 | 模块 | 价值 |
| --- | --- | --- | --- | --- |
| A-01 | 统一关键写 Dubbo RPC 为 `retries=0` | 已完成 | postoffice/authcenter | 在 inbox 完成前先阻断框架级重复副作用 |
| A-02 | postbox 发送 inbox/clientMsgId 幂等 | 已完成 | postbox/common-core | 为稳定 ACK、消费重放和客户端重试建立唯一事实 |
| A-02B | postmaster ingress inbox/稳定 ID 去重 | 已完成 | postmaster/common-core | 关闭 broker ACK 后重发导致重复 seq 的窗口，并让下游重放复用稳定身份 |
| A-03 | 群发送权限聚合 | 已完成 | common-api/business/postbox/postmaster/postoffice | 封闭非成员发送、禁言、群状态和客户端系统消息伪造边界 |
| A-04 | refresh token 原子 rotate | 已完成 | authcenter/common-core/common-api | 消除旧 refresh token 重放并统一 session 生命周期 |
| A-05A | HTTP 默认拒绝与自助 session 所有权 | 已完成 | api-server | 消除漏写 principal 即匿名、任意 user/session 踢下线 |
| A-05B | 登录可信身份源 | 已完成 | api-server/authcenter/common-api | 从直接信任 userId 升级到短期签名 assertion + 一次性 jti seam |
| A-06 | ChannelFuture 驱动 delivery claim | 已完成 | common-api/postoffice | 修复在线投递假成功与多设备部分成功误 ACK |
| B-01 | Redis Cluster hash tag 统一 | 已完成 | common-core/postoffice/postman | 关闭已知多 key Lua/MGET/DEL CROSSSLOT |
| B-02 | 节点投递结果聚合与 dead/超时补偿 | 已完成 | common-api/common-core/postman/postoffice | 以真实节点终态统一在线失败和离线补偿语义 |
| B-03 | 队列 consumer 配置单一事实源 | 已完成 | common-core/config/postman/postmaster | 消除 Kafka 伪配置、后端重试语义与吞吐参数漂移 |
| B-04A | 精确连接替换契约 | 已完成 | common-api/postoffice | 为跨节点多端策略提供不误踢新连接的 connectionId 终态 |
| B-04B | Redis 全局 login lease | 已完成（受发布开关控制） | common-core/postoffice/config | 原子执行跨节点多端策略、全局限额与 generation fencing |
| B-05A | Mongo shard-key 写路径与安全 migration | 已完成（待真实集群执行） | common-core/distro/docs | 让已就绪集合可幂等分片，并阻止不安全 DDL |
| B-05B | conversation 反向偏好读模型与分片 | 已完成（待真实集群执行） | business/common-core/distro | 消除 conversationId scatter-gather 并使 owner 当前态可分片 |
| B-06 | Kafka DLT 查询、审计与受控 redrive | 已完成（待真实 Kafka 验收） | common-api/common-core/ops-cli/config/distro | 将死信处置从手工改写 topic 升级为可校验、可审计、非破坏性操作 |
| D-01 | 生产服务统一 OCI 镜像 | 已完成（待镜像引擎验收） | server build/docs | 形成七个独立服务一致、非 root、容器内存感知的发布产物 |
| D-02A | common-core Web/Prometheus 依赖去外溢 | 已完成 | common-core/六个服务 | 非 Web 服务不再隐式携带并启动 Tomcat，管理依赖回归可执行模块所有 |
| D-02B | 七服务显式管理端口与健康探针 | 已完成（待运行环境验收） | 七个服务/config/docs | 统一可抓取指标、进程存活与依赖就绪语义，为 Kubernetes 探针提供稳定契约 |
| D-03A | 独立 api-server 生产入口与真实 cluster overlay | 已完成（待运行环境验收） | api-server/config/bootstrap/Docker | 补齐集群 HTTP 控制面，并修复自定义 config name 导致 cluster 配置未加载 |
| D-03B | API Redis Lua 限流实现复原 | 已完成（待编译/运行验收） | api-server/common-core/config | 恢复多副本入口保护，统一可信代理、429 和 Redis 故障语义 |
| D-04A | 七服务 Helm 工作负载基线 | 已完成（待 Helm/集群验收） | distro/helm/docs | 把镜像、探针、资源、安全、PDB、拓扑和网络入口形成一致部署契约 |
| D-04B | Kafka topic DDL 与启动校验解耦 | 已完成（待真实 broker 验收） | common-core/config/distro/docs | 让业务 Pod 去 DDL 权限，同时保证主/DLT topic 契约在启动时强校验 |
| D-05A | 分级灾备恢复 Runbook 与 RPO/RTO | 已完成（待真实演练验收） | docs/全服务状态边界 | 禁止不一致快照复活旧状态，形成 Mongo/Redis/Kafka 恢复顺序与证据契约 |
| D-05B | write-behind RPO 可观测与停机 drain | 已完成（待编译/演练验收） | common-core/business/postmaster/Helm/docs | 让热水位积压、卡死批次和停机丢失从隐式窗口变成可告警事实 |
| D-05C | Prometheus Operator 采集与告警交付 | 已完成（待 Helm/集群验收） | Helm/observability/docs | 把指标 endpoint、抓取发现、告警规则和 NetworkPolicy 边界连成可选生产闭环 |
| D-06A | history port 与 Mongo Document 解耦 | 已完成（待编译验收） | common-core/postbox/postmaster/docs | 关闭持久化模型和 BSON 类型向历史查询/撤回业务泄漏，为 storage-history 物理拆分建立边界 |
| D-06B | storage-history 物理模块拆分 | 已完成（待编译/装配验收） | storage-history/common-core/postbox/postmaster/build/docs | 历史 Mongo adapter 不再由所有 common-core 消费者携带源码类型，形成显式存储所有权与构建门禁 |
| D-06C | infra-queue 物理模块拆分 | 已完成（待编译/装配验收） | infra-queue/common-core/queue feature/build/docs | 队列 port 与 Kafka/Chronicle runtime 分离，按后端条件装配并以构建门禁阻止实现泄漏 |
| D-06D | Queue Subscription 生命周期收口 | 已完成（待编译/运行验收） | infra-queue | context shutdown 统一停止 Kafka container/Chronicle poller，避免滚动发布残留线程与无界停机 |
| D-06E | storage-business 物理模块拆分 | 已完成（待编译/装配验收） | storage-business/common-core/auth/business/postmaster/postman/ops | 业务 Mongo adapter 与 port 分离，无关进程不再因 common-core 携带 Mongo driver |
| C-01A | session 本地复核租约 | 已完成 | postoffice/authcenter | 消除每次心跳两次重复 RPC，并保留撤销兜底 SLA |
| C-01B | 路由心跳合并与批刷 | 已完成 | postoffice | 降低在线连接对 Redis 的写入和 RTT 放大 |
| C-02 | 群 fanout worker 化 | 已完成 | common-api/common-core/postmaster | 隔离热点群成员枚举与写扩散，释放 ingress consumer |
| C-03 | 水位 writer bulk `$max` | 已完成 | postmaster/common-core | 保证多副本单调并降低 Mongo 写放大 |
| C-04 | 网关节点连接准入与写背压 | 已完成 | postoffice/config | 让节点容量配置真实生效并阻断慢消费者堆外缓冲失控 |
| C-05A | 群成员版本化 epoch 快照 | 已完成 | common-api/common-core/business/postmaster | 使退群、重入和 fanout 重试读取同一成员集合 |
| C-05B | 群成员统一版本化 mutation | 已完成 | common-api/common-core/business | 统一当前态、历史 epoch、群版本的事务边界 |
| C-05C | fanout job 游标 checkpoint 与消息体分片 | 已完成 | common-core/postmaster/config | 将大群重试重复范围限制到一页并限制 broker 单事件大小 |
| C-05D | fanout completed retention 启动守卫 | 已完成 | common-core/config | 阻止 Kafka 合法重放早于 job 幂等终态过期 |

## 11. 勘误记录

- 2026-07-19：首次全量评审。确认现有 `ASSESSMENT.md` 中 RateLimiter、Actuator、Mongo sharding script 等完成度描述与当前仓库事实存在漂移，后续应在对应任务完成后同步修正。

## 12. 已完成任务复核

### A-01 统一关键写 RPC 重试语义

实现摘要：

- postoffice 调用 `MessageSender.sendMessage` 显式设置 `retries=0`；
- postoffice 调用一次性 `ConnectionAuthService.authenticateWsTicket` 显式设置 `retries=0`；
- authcenter 调用跨模块 `KickoffCommandService` 显式设置 `retries=0`；
- `SessionLifecycleService` 与 `SessionRevocationServiceImpl` 同属 authcenter，删除模块内 Dubbo 自调用，改为构造器注入；
- 查询类 RPC 保留全局有限重试，本任务没有扩大到无副作用读路径。

合理性：

- 消息发送当前还没有 clientMsgId inbox，框架自动重试会生成第二个 serverMsgId，必须禁用；
- WS ticket 是原子一次性消费，首调成功但响应丢失时自动重试只会得到 ticket invalid，必须由上层重连/重新签票；
- revoke/kickoff 是 command 语义，不应依赖透明 RPC 重试制造未知执行结果。

内聚性：

- session 生命周期与 session revoke 都属于 authcenter，同进程业务编排改为本地构造器依赖；
- 只有真正跨 postoffice 边界的 kickoff 保留 Dubbo；
- 重试决策仍位于 consumer 端，符合 Dubbo 调用语义。

后续价值：

- 为 A-02 发送 inbox 提供清晰边界：当前先保证“一次 RPC 只尝试一次”，后续再由业务幂等支持客户端显式重试；
- 为 refresh rotate、kickoff outbox 等任务避免隐藏的框架级重复副作用；
- 形成“查询可有限重试，command 必须 retries=0 或有业务幂等键”的统一规则实例。

遗留风险：

- `retries=0` 只能阻止 Dubbo 透明重试，不能解决客户端重试、Kafka 重放和响应丢失；
- kickoff 当前仍缺少可靠 command outbox；
- 规则尚未形成静态扫描门禁，后续新增写 RPC 仍可能遗漏。

验证证据：

- 本任务按阶段要求未执行单元测试；
- `./gradlew :authcenter:compileJava :postoffice:compileJava` 通过；
- 完成源码 diff 与剩余 DubboReference 人工分类审计。

### A-02 postbox 发送 inbox/clientMsgId 幂等

实现摘要：

- 新增专用 `MessageSendInboxStore`，提供 `ACQUIRED / IN_PROGRESS / ACCEPTED / CONFLICT` claim 结果；
- cluster/独立部署使用单 Redis HASH + Lua 原子迁移，all-in-one 使用 RocksDB 同步状态机；
- `(senderId, conversationId, clientMsgId)` 使用长度分隔后 SHA-256 形成紧凑身份 key，避免原始 ID 分隔符歧义和超长 key；
- 首次 claim 固定 `serverMsgId/createTime`，并发请求由 30 秒短租约互斥；默认保留首次 ACK 7 天；
- Protobuf 确定性序列化形成载荷指纹，服务端生成字段不参与冲突判定；
- broker ACK 后才标记 `ACCEPTED`；明确发布失败释放 owner 租约但保留稳定消息 ID；
- 已确认的客户端重试在动态权限检查前直接返回首次 ACK，避免权限状态变化破坏幂等响应。
- 首次有效的 `needOfflinePush` 决策也写入 inbox；模糊重试复用该结果，避免同一 `serverMsgId` 因免打扰配置变化产生不同 ingress 载荷。

合理性：

- 发送成功的判断边界选择 broker ACK，而不是“调用过 publisher”，与现有异步链路的可观察事实一致；
- ACK 前失败可以安全释放；ACK 后 inbox 确认失败属于未知结果，不能立即释放并用新 ID 重试，因此保留租约并继续复用原 ID；
- 相同 clientMsgId 携带不同归一化载荷会返回冲突，不会静默复用旧 ACK；
- `ACCEPTED` 重试跳过动态权限，但新请求和租约恢复仍检查权限，兼顾首次授权边界与幂等响应稳定性。

内聚性：

- postbox 负责客户端发送接入与 ACK，因此编排位于 `MessageSenderImpl`；
- Redis/RocksDB、TTL、Lua 和状态记录位于 common-core 基础设施 seam，业务层只依赖状态机接口；
- 未把多状态发送语义塞进通用 `IdempotencyStore`，避免简单去重抽象被 message 特例污染。

一致性与后续价值：

- Redis 与 RocksDB 暴露同一 claim/accept/release 语义，all-in-one 与 cluster 不再走两套业务流程；
- 稳定 `serverMsgId` 为 A-02B postmaster ingress 去重、Mongo 幂等写和端到端重放测试提供唯一关联键；
- 状态机模式可供后续 refresh rotate、command outbox 参考，但不会通过泛型基类强行复用不同领域状态。

遗留风险：

- broker 已 ACK、postbox 尚未 `markAccepted` 时崩溃仍可能重发同一 `serverMsgId`；A-02B 已保证 postmaster 复用原 seq，但跨 topic 仍非原子 exactly-once；
- `SendMessageResp` 已在 A-03 补齐结构化 ErrorCode，但旧调用方仍需验证是否展示具体拒绝原因；
- 7 天 TTL 是工程默认值，需结合客户端最长离线重试窗口和 Redis 容量模型校准；
- 本任务没有解决 Kafka batch 后组失败导致前组重放，也没有建立 consume-transform-produce 事务。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-core:compileJava :postbox:compileJava :postbox:compileTestJava` 通过；
- 完成 Redis 单 key Lua 同槽审计、ACK 前后故障窗口审计与源码 diff 检查。

### A-02B postmaster ingress inbox 与稳定 seq

实现摘要：

- 新增专用 `IngressMessageInboxStore`，状态为 `ACQUIRED / IN_PROGRESS / COMPLETED / CONFLICT`；
- 每个稳定 `serverMsgId` 对应独立 inbox key，保存载荷指纹、owner、租约、处理状态和 `assignedSeq`；
- Redis 使用 pipeline 批量执行单 key Lua，避免 500 条 ingress 退化成 500 次串行 RTT，同时满足 Redis Cluster 跨槽限制；
- all-in-one 使用 RocksDB 实现相同批量接口和状态迁移；
- postmaster 只为尚未绑定 seq 的消息批量申请新段，并在 history、会话创建、delivery 等外部副作用前固定 seq；
- Kafka/Chronicle 重放遇到 `COMPLETED` 直接跳过，遇到崩溃残留租约等待过期后恢复；
- 明确处理异常释放租约但保留 seq，使队列的快速重试不会被 inbox 错误吞掉；
- 批内重复 serverMsgId 先本地合并，不同载荷则拒绝，避免无意义 Redis 和下游放大。

合理性：

- 没有使用消费前 `SET NX`：这种简单去重在置位后崩溃会永久丢失消息；
- seq 绑定早于任何外部副作用，绑定失败最多产生允许的 seq 空洞，不会让一个消息获得两个可见 seq；
- inbox 只在 HISTORY/DELIVERY broker ACK 全部返回后完成；后组处理失败导致前组 Kafka 重放时，已完成组可直接跳过；
- 进程崩溃无法主动 release 时，下一消费者在 handler 内等待短租约恢复，不会因 Kafka 默认三次快速重试直接进入 DLT。

内聚性：

- 消费编排、策略分类和 seq 绑定仍由 postmaster `IngressEventListener` 负责；
- Redis pipeline、Lua、RocksDB 和 TTL 位于 common-core 状态 seam；
- 状态接口只表达 ingress 生命周期，没有把 Message、HistoryEvent 或 postmaster service 类型下沉到基础设施模块。

一致性与后续价值：

- postbox 固定 `serverMsgId/createTime/effectiveOfflinePush`，postmaster 固定 `seq`，形成从客户端重试到消费重放的连续稳定身份；
- history Mongo 继续按确定性 mapping/block upsert，online delivery 继续按稳定 deliveryId claim/commit，现有下游幂等现在有可靠输入；
- pipeline 批量语义保留了原 500 条消费批次的吞吐模型，为后续 Kafka consume-transform-produce 事务或 routed-message outbox 提供明确替换 seam。

遗留风险：

- 本任务保证“重复处理复用相同 seq”，不保证多个下游 topic 原子 exactly-once；副作用完成后、inbox 完成前崩溃仍会重复发布相同 ID/seq；
- 普通群扩散分多个 Kafka 事务批发送，中途失败仍可能重复已发送成员批次，依赖 postoffice delivery dedup；
- Redis pipeline 需要真实 Redis Cluster 压测和故障注入验证，本阶段只完成编译与静态同槽审计；
- 30 秒租约必须持续小于 Kafka `max.poll.interval.ms` 且大于正常批处理耗时；群 fanout worker 化后应引入续租而不是单纯放大租约；
- inbox TTL 当前默认 7 天，仍需与 Kafka retention、DLT 最长回放窗口和 Redis 容量模型统一。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-core:compileJava :postmaster:compileJava :postmaster:compileTestJava` 通过；
- 完成 Kafka/Chronicle batch 确认边界、显式异常、进程崩溃、部分 completion、重复下游发布和 Redis Cluster 单 key Lua 人工审计。

### A-03 群消息发送权限聚合

实现摘要：

- common-api 新增 `GroupMessageSendPermissionService` 批量契约和稳定 `GroupSendPermissionCode`；
- business provider 一次读取群资料，并按同群 senderIds 利用 compound index 批量读取成员，判断群不存在/解散/封禁/非成员/禁言；
- 权限结果使用 2 秒 Redis 快照，允许与拒绝均缓存；同群 key 共用 Redis hash tag，批量 MGET/pipeline 保持同槽；
- postbox 每个首次群发送只调用一次权限 provider，拒绝后不进入 ingress；
- postmaster 在 seq 分配和历史写入前按同群唯一 senderIds 再做一次防御校验，并复用 groupType；
- 普通群首次建会话与 fanout 复用成员快照；超级群不再在首条消息时枚举全量成员创建用户会话；
- postoffice 将客户端 source 固定为 USER，清除 options/serverMsgId/time/status/seq，只允许用户消息 ContentType；
- 删除未使用且群前缀错误的 `GroupChatPolicy`，同时删除被聚合权限替代的 `DirectChatPolicy`，避免平行授权入口；
- `SendMessageResp` 增加稳定错误码，网关透传群不存在、群不可用、非成员、禁言、幂等冲突与处理中语义。

合理性：

- 权限真相位于拥有 Group/GroupMember repository 的 business，不在 postbox/postmaster 复制 Mongo 判断；
- postbox 是正常发送的前置拒绝点，postmaster 是绕过网关/直接投 ingress 时的 fail-closed 防线；
- postmaster 批量检查唯一 sender，而非逐消息 Dubbo；返回 groupType 替代原独立群类型 RPC，正常群 RPC 数没有增加；
- 2 秒共享缓存让 postbox 刚完成的判断可被 postmaster 复用，同时为踢出/禁言变化给出明确而较短的陈旧上界。

内聚性与一致性：

- common-api 只放跨模块 DTO/枚举/接口；business 负责领域判断和缓存；postbox/postmaster 只消费结果；
- 客户端与系统通知的信任边界在 postoffice 收口，不能靠可伪造的 `MessageSource.SYSTEM` 绕过；
- 权限拒绝、幂等拒绝统一进入 `SendMessageResp.errorCode`，不再由各层拼字符串或全部映射为 1004。

后续价值：

- 批量契约可直接增加 membershipVersion/permissionVersion，而不改变 postmaster 每消息调用模型；
- 群管理写服务落地后，可对 `group:send-permission` 做事件失效，把 2 秒 TTL 从正确性兜底变成灾备兜底；
- groupType 与 sender 权限一次返回，为 C-02 群 fanout worker 化提供完整的任务上下文；
- RedisCacheStore 的 `putAll` 改为 pipeline，现有用户/会话批量 cache fill 同样减少串行 RTT。

遗留风险：

- 权限结果尚无 membership version 或签名授权断言；postbox 与 postmaster 间超过 2 秒时仍可能因成员状态变化产生授权竞态；
- 群管理写链当前不完整，无法在踢人/禁言/解散事务提交后立即精确失效权限缓存；
- senderNickName/senderAvatar 仍可由客户端携带，可信资料快照需要独立任务从用户域补齐；
- 超级群如何为成员建立会话索引应由群成员同步/读扩散方案统一，当前仅移除了首消息 O(N) 建会话；
- 本任务没有实现群级全员禁言、按消息类型发言权限或管理员例外，因为当前领域模型没有这些字段。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :common-core:compileJava :business:compileJava :business:compileTestJava :postbox:compileJava :postbox:compileTestJava :postmaster:compileJava :postmaster:compileTestJava :postoffice:compileJava :postoffice:compileTestJava` 通过；
- 完成客户端伪造字段、非成员 ingress、群状态变化、超级群 O(N) 枚举、Redis Cluster 同槽与热路径 RPC 数人工审计。

### A-04 refresh token 原子 rotate

实现摘要：

- common-core 新增 `RefreshTokenStateStore`，Redis 使用一个 token family 对应一个 HASH，inspect/rotate/revoke 均为单 key Lua；all-in-one 使用 RocksDB 同步状态机；
- refresh token 改为 `rt.<familyId>.<secret>`，存储层只保存 SHA-256；familyId 只负责定位状态，授权强度来自随机 secret；
- 每次登录创建独立 family，并把 `refreshTokenFamilyId/refreshTokenExpireAt` 写入 `SessionPrincipal`；
- refresh 先检查 session active、ban、tokenVersion 和 family 绑定，再原子消费当前 token并签发下一代；
- 已消费 token 再次出现会将 family 标为 compromised，并撤销 session、触发连接踢出；logout/kickoff 同步撤销 family；
- session TTL 从 access token 的 24 小时修正为 refresh family 的 14 天绝对有效期，轮换不会滑动延长；
- 带 sid 的 access token 在服务端找不到 session 时直接拒绝，不再从 JWT 临时重建 session 绕过服务端撤销。

合理性：

- token family 的 current、used、generation、status 与 sessionId 放在同一个 Redis key，避免 Redis Cluster 跨槽事务与 service 层 read-delete-write 竞态；
- rotate 只有一个并发请求能成功，失败请求不会再获得第二个有效 token；旧 token 重放触发整族失效，符合高安全 refresh rotation 的泄漏检测语义；
- 使用绝对 family 期限，避免攻击者或长期在线客户端通过持续刷新无限延长凭证生命周期；
- 登录过程中 family 创建后的异常会显式撤销，避免留下可用的孤儿 refresh token。

内聚性与一致性：

- token 状态与原子性位于 common-core 存储 seam，authcenter 只负责编排用户安全状态、JWT 与 session；
- Redis/RocksDB 共享同一状态接口，cluster 与 all-in-one 不再使用不同 refresh 业务模型；
- `SessionRepository.save(session)` 根据 session 自身的 refresh 绝对期限计算 TTL，WS ticket 签发、revoke 和登录不再各自选择不同 session TTL；
- session 创建使用完整 save 维护 user/device 索引，撤销使用 `updateSession` 只改主记录，避免旧设备会话被撤销时反向覆盖新 session 的设备索引；
- 所有 session 撤销入口统一联动 `revokeFamily`，没有保留旧的 raw-token 普通缓存旁路。

后续价值：

- generation、used hash 与 family 状态为设备风险审计、异常刷新告警、强制重新认证和 token 家族可视化提供稳定数据模型；
- 原子状态 seam 可增加短时幂等结果或有约束的 grace window，而不需要改动 Controller/JWT 契约；
- 服务端 session 已成为 access/refresh 的共同撤销真相，为 A-05 默认拒绝授权和用户只能管理自身 session 奠定基础。

遗留风险：

- 当前采用严格复用策略：同一客户端并发刷新，或 rotate 已成功但响应丢失后用旧 token 重试，也会被视为泄漏并撤销 session；生产体验需要在不削弱复用检测的前提下设计短时、设备绑定的幂等结果；
- family 状态尚未发送安全审计事件，也没有 reuse/rotate/revoke 指标和告警；
- 旧版本 `cheese_im:refresh_token:*` 数据不会迁移，新版本发布后存量 refresh token 需要重新登录，旧 key 依其原 TTL 自然清理；
- 尚未执行真实 Redis Cluster 并发刷新与故障注入，Lua 原子性和同槽规则目前只有编译与静态审计证据。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :common-core:compileJava :authcenter:compileJava :authcenter:compileTestJava :api-server:compileJava` 通过；
- `git diff --check` 通过，并完成人为审计：并发 rotate、旧 token 重放、登出联动、session 丢失、ban/tokenVersion 变化和绝对 TTL 不滑动。

### A-05A HTTP 默认拒绝与自助 session 所有权

实现摘要：

- api-server 新增 `ApiAuthenticationInterceptor`，所有 `/api/**` Controller 方法默认解析有效 Bearer session；
- 仅 login、refresh 使用显式 `@PublicApi` 放行，新增 Controller 即使遗漏 `SessionPrincipal` 参数也不会意外变成匿名接口；
- principal 存入 request attribute，Controller 参数解析器与幂等拦截器共享同一次解析结果；
- 新增独立认证/授权异常，缺失或无效凭证统一返回 401，已认证但资源所有权不匹配统一返回 403；
- logout 的 sessionId、kickoff device/all 的 userId 改为以当前 principal 为授权真相；旧请求字段/路径只作兼容和一致性比对，不能指定其他用户。

合理性：

- 鉴权由路径级默认拒绝保证，而不是依赖开发者记得给方法增加某个参数，新增端点的安全默认值从 fail-open 改为 fail-closed；
- 公开端点采用可检索注解白名单，审计时无需维护容易漂移的字符串 exclude 列表；
- 认证与资源所有权分开表达为 401/403，不再由异常处理器根据 URL 猜测同一个 `IllegalStateException` 的语义；
- 自助踢下线只允许作用于当前 user/session，管理员跨用户操作没有混入普通用户 API。

内聚性与一致性：

- HTTP 认证、注解和异常语义全部位于 api-server，authcenter 继续拥有 token/session 真相；
- `AccessTokenSessionResolver` 是唯一 HTTP Bearer 解析入口，拦截器、参数解析器、幂等处理复用 request 缓存；
- AuthController 只做主体到命令的映射，不把所有权判断下沉到 Mongo 或复制 session 查询。

后续价值：

- A-05B 接入密码/OAuth/可信 assertion 时只需替换 login 身份来源，不需要再次改造所有业务 Controller；
- 统一 request principal 可承载 tenant、role、scope 和审计 correlation，后续 RBAC/ABAC 有稳定入口；
- 默认拒绝可进一步由静态扫描验证“只有已审计方法使用 `@PublicApi`”。

遗留风险：

- login 仍直接信任请求 userId，A-05A 只修复 access token 之后的授权边界，不能宣称认证系统已生产化；
- 每个普通 HTTP 请求仍至少一次 api-server → authcenter SessionQuery RPC；百万 DAU 下应在明确撤销 SLA 后评估本地 JWT 校验与短 TTL session snapshot，而不是绕过服务端 session 真相；
- 管理员跨用户 kickoff/RBAC/审计端点尚未设计，当前明确不复用自助接口；
- `@PublicApi` 尚无架构测试或静态扫描门禁。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :api-server:compileJava :api-server:compileTestJava` 通过；
- 完成 Controller 方法清单、公开端点白名单、请求内重复解析、logout/device/user 所有权和 401/403 映射人工审计。

### A-06 ChannelFuture 驱动 delivery claim

实现摘要：

- `ConnectionManager` 新增返回真实 `ChannelFuture` 的写入入口，发送计数只在 future 成功后增加；
- `OnlineDispatcherImpl` 在 future 成功后才 commit delivery claim，写失败 abort；不再把 `writeAndFlush` 未同步抛异常当作投递成功；
- 一次多连接 dispatch 共用默认 1 秒总截止时间，避免最多 10 个设备逐个等待造成 10 倍 RPC 延迟；
- 超出同步期限返回 `WRITE_PENDING`，`DeliveryWriteFinalizer` 通过专用有界线程池在 EventLoop 外完成 Redis commit/abort；
- common-api 新增稳定 `DispatchResultCode`，postoffice 与节点消费者使用同一结果分类；
- `NodeDeliveryPoller` 逐连接检查结果：部分成功不会 ACK 整个节点任务，重试时已完成连接由 dedup 返回 duplicate，只补偿失败连接；
- `WRITE_PENDING/DELIVERY_IN_PROGRESS` 不做快速重试计数，保留 processing claim 等待租约回收，避免在 future 尚未完成时迅速耗尽 5 次重试。

合理性：

- ChannelFuture 是 Netty 本地 socket 写入链路可观察的最早可靠终态；只有它成功后把 dedup 置为 delivered，才能避免编码/连接关闭/写失败被永久去重；
- 客户端 `CHAT_DELIVERY` 仍是独立的设备送达高水位，明确区分 `SOCKET_WRITTEN` 与 `CLIENT_DELIVERED`；
- 超时属于未知结果，立即 abort 会允许并发重发并产生重复，因此保留 claim，由 future 回调最终 commit/abort；
- Redis 状态迁移不在 EventLoop 执行，避免慢 Redis 阻塞同 EventLoop 上的大量长连接。

内聚性与一致性：

- transport write 事实位于 `ConnectionManager`，delivery claim 编排位于 `OnlineDispatcherImpl`，超时后的资源隔离位于 postoffice delivery 组件；
- 跨模块只共享稳定结果枚举和 DTO，没有把 Netty `ChannelFuture` 泄漏到 common-api；
- 节点队列复用每连接 dedup，而不是再建一份节点级成功 Set，重试语义与直接 Dubbo 投递一致。

后续价值：

- 稳定的连接级结果分类为 B-02 节点失败补偿、dead redrive、在线失败转离线推送和告警维度提供基础；
- 总截止时间与有界 completion executor 可直接进入容量压测，能够观察 pending 比率、队列饱和和 Redis commit 延迟；
- 多设备部分成功现在可安全重试，为后续全局多端策略和设备级 delivery SLA 提供正确底座。

遗留风险：

- ChannelFuture 成功只代表写入本地传输链路，不代表客户端收到；最终用户可见送达仍依赖客户端 `CHAT_DELIVERY` ACK；
- 跨节点 `NodeDeliveryService.deliver=true` 目前仅代表成功入队，postman 缺少异步结果回传；路由在入队后失效时无法立即触发离线推送，留给 B-02 统一补偿；
- dedup commit 在 socket 写成功后持续失败时，claim 到期后的重发仍可能造成客户端重复，需通过稳定 message/deliveryId 和客户端去重兜底；
- completion queue 满时选择不阻塞 EventLoop，claim 依赖 TTL 恢复；需增加 queue depth/rejection 指标和告警；
- 本阶段未执行真实慢 Channel、断网、Redis 故障与多设备 chaos。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :postoffice:compileJava :postoffice:compileTestJava :postman:compileJava :postman:compileTestJava` 通过；
- 完成同步写失败、异步失败、写超时、commit 失败、部分设备成功、EventLoop 不阻塞和节点租约恢复人工审计。

### B-01 Redis Cluster hash tag 统一

实现摘要：

- `RedisKeys` 为同一节点的 ready/processing/lease/dead 生成完全相同的 node hash tag；
- 同一 `(userId, conversationId)` 的 read/max/unread/min key 使用同一 `uc` hash tag，`advanceUserMaxSeq` 与 `advanceReadState` 两个多 key Lua 可在 cluster 执行；
- hash tag 不直接拼接外部 ID，而是对长度分隔身份做 URL-safe Base64，避免 userId/nodeId 中的 `{}` 改变 Redis 实际取槽片段；
- `RedisCacheStore.getAll/evictAll` 和会话摘要批量读取从跨槽 MGET/多 key DEL 改为逐 key pipeline；
- 全仓静态扫描确认只有 `NodeQueueRedisScripts` 与 `RedisConversationStateStore` 使用 `KEYS[2+]`，均已具备同槽 key 工厂。

合理性：

- 只有需要原子迁移的同一状态机才强制同槽；任意用户/会话批量缓存不能为了 MGET 被放入一个全局热点槽，因此改用 pipeline 保持分片分布；
- 长度分隔再编码使 `(a, bc)` 与 `(ab, c)` 不会产生相同 tag，也不信任调用方提供 Redis 控制字符；
- pipeline 只用于降低跨节点 RTT，文档明确不把它宣称为跨 key 原子操作。

内聚性与一致性：

- 所有 key 形态仍由 `RedisKeys` 单一入口生成，postoffice/postman 不复制 tag 拼接；
- Lua 脚本继续位于拥有状态机的 common-core，业务模块只传同一 key 工厂生成的 key；
- Redis 与 RocksDB 仍复用相同逻辑 key API；花括号对 RocksDB 只是普通字符，不产生第二套业务分支。

后续价值：

- `redis-cluster` profile 的已知 CROSSSLOT 阻断已从设计层关闭，可进入真实三主三从 smoke、扩缩容和故障转移验证；
- “多 key Lua 必须同 tag、任意批量 key 使用 pipeline”的规则可形成静态扫描门禁；
- 节点队列四态同槽后，B-02 可以在现有脚本上增加补偿状态而不重做 key 拓扑。

遗留风险：

- 本任务是 key 形态破坏性变更：旧 `uc:*` 热状态与无 tag 节点队列不会自动双写迁移；首次启用本版本 cluster 前必须停写、排空旧节点队列，并从 Mongo 真相重建会话热状态；
- 旧 all-in-one RocksDB 状态也会因逻辑 key 变化失效，仅适用于开发环境清理后重建；
- 尚未使用真实 Redis Cluster 执行脚本、pipeline、slot migration 和 failover；静态同槽不能替代集成证据；
- pipeline 对大量跨槽 key 仍可能造成单请求扇出，调用方必须保持批次上限。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-core:compileJava :common-core:compileTestJava :postoffice:compileJava :postoffice:compileTestJava :postman:compileJava :postman:compileTestJava :business:compileJava` 通过；
- `rg "KEYS\\[[2-9]"` 仅命中两个已审计状态机，`multiGet/multi-key delete` 生产代码扫描已清零，`git diff --check` 通过。

### B-02 节点投递结果聚合与 dead/超时补偿

实现摘要：

- `RouteSnapshot` 增加 `deliveryOutcomeVersion` 能力版本；新 postoffice 路由发布 v1，缺失字段的旧路由继续按旧语义运行，避免滚动升级期间等待旧节点不会产生的结果；
- postman 构造 `DispatchMessageReq` 时冻结路由快照中的 connectionId，不再在目标节点按 userId 二次扩张投递范围；
- postoffice 在真实 `ChannelFuture` 终态后发布 `DELIVERY_OUTCOME`；无连接是明确终态，基础设施/写入未知继续保留 processing claim，重试耗尽进入 dead 前发布最终失败；
- postman 以 `(deliveryId, userId)` 建立用户级 attempt，冻结期望节点；每节点结果使用 HASH field 幂等覆盖，任一节点成功即 `DELIVERED`，全部失败或默认 90 秒 deadline 到期才进入 `OFFLINE_READY`；
- attempt 状态与 64 分片 deadline ZSET 使用相同 Redis hash tag，注册、节点结果、超时、发布 claim 和完成迁移均由 Lua 原子执行；
- 离线事件发布使用 30 秒 `PUBLISHING` 租约：先 claim、等待 QueueAdapter/broker ACK，再标记 `PUBLISHED`；发布失败回到 `OFFLINE_READY`，进程崩溃由 deadline 索引在租约后恢复；
- `OfflinePushEvent` 通过既有 attributes 携带内部触发原因；节点失败/超时补偿不会再被仍存在的陈旧路由拦截，进入厂商推送前会移除内部属性；
- timeout scheduler 对单 attempt 隔离异常，避免一条坏状态或一次 broker 故障阻断其余 64 个分片。

合理性：

- “写入节点队列”只是基础设施受理，不是在线投递成功；以 postoffice 的 socket write 终态作为补偿判断依据，语义与 A-06 一致；
- attempt 采用“任一成功、全部失败”聚合，符合多设备用户只要至少一个在线端收到就不应触发离线推送的产品语义；
- 结果先发布、节点 processing 后 ACK，使 processing claim 同时承担可靠结果 outbox：结果发布失败时节点消息仍可由租约恢复；socket 已成功但结果发布前崩溃时，连接级 delivery dedup 会在重放时返回稳定成功；
- 离线发布仍是 at-least-once，broker ACK 与 Redis 完成标记之间崩溃最多产生重复事件；既有 `PushStateStore` 再以 serverMsgId + userId 抑制厂商重复，而不是追求不可实现的跨 Kafka/Redis exactly-once。

内聚性与一致性：

- postoffice 只负责本节点真实执行与结果事实，postman 只负责用户级多节点聚合和在线转离线策略；
- Redis 脚本封装在 `NodeDeliveryPendingStore` 实现，listener/scheduler 不感知 key、HASH field 或 Lua 返回细节；
- 离线事件构造收敛到 `OfflinePushEventFactory`，路由为空、节点失败和超时三条入口使用相同字段；
- outcome code、trigger reason、topic 和 route capability 均位于共享契约/常量层，没有复制跨模块字符串魔法值。

后续价值：

- 节点 dead 不再只是不可消费的审计墓地；它在进入 dead 前已产生最终失败事实，离线补偿不依赖人工 redrive；
- attempt 状态机可直接增加按结果码、deadline、publish lease 的指标和 SLO，支持后续 chaos 与容量验证；
- capability 版本为未来替换节点队列或升级 outcome 协议提供可灰度的兼容 seam；
- 64 槽分片避免百万 DAU 下形成一个全局 deadline 热 key，后续可按 postman 实例租约分配扫描 shard，而不改变状态模型。

遗留风险：

- 当前所有 postman 副本都会扫描 64 个 deadline shard；状态迁移安全，但副本数很大时会放大空扫描 QPS，后续应增加 shard owner lease；
- 默认 90 秒补偿 deadline 以当前 60 秒 processing lease 为依据，必须结合生产 P99、队列积压与移动端体验压测后调整；
- 旧路由在滚动期仍以“成功入队”抑制离线推送，这是兼容性取舍；应观察 v1 路由覆盖率，覆盖完成后删除 legacy 分支；
- 尚未执行 Redis Cluster、Kafka 故障、postoffice 崩溃、结果重复/乱序和 deadline 边界的集成/chaos 测试；
- socket write 成功仍不等于客户端业务 ACK；本任务只决定是否触发离线通知，不改变 `CHAT_DELIVERY` 的最终送达语义。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :common-core:compileJava :postoffice:compileJava :postoffice:compileTestJava :postman:compileJava :postman:compileTestJava` 通过；
- 完成发布前崩溃、broker 拒绝、重复节点结果、迟到成功、全部失败、deadline、publish lease 恢复、混合版本和 Redis Cluster 同槽人工审计；
- `git diff --check` 通过。

### B-03 队列 consumer 配置单一事实源

实现摘要：

- `@QueueListener.group` 继续作为代码中的稳定消费身份，避免环境变量误改 group 导致 offset 重置、全量重放或与旧消费者并行；
- `QueueProperties` 新增全局 consumer 重试和按 group 的 listener 吞吐配置，`QueueListenerBeanPostProcessor` 在订阅时统一解析 concurrency、batchSize、batchInterval；
- Kafka 与 Chronicle 的 `max-attempts` 统一定义为“包含首次处理的总次数”；默认均为 3 次、间隔 1 秒；
- Kafka `FixedBackOff` 从总次数换算为 retry 次数，Chronicle 在相邻 handler 尝试间使用同一间隔；两者 DLT 成功后推进、DLT 失败不确认的边界不变；
- 删除 module-postman/postmaster/postbox 中不会被自研 QueueAdapter 使用的
  `spring.kafka.consumer.group-id`、`spring.kafka.listener.*` 等伪配置；
- postman 三个 group、postmaster ingress/history 的并发与 batch 参数全部迁至
  `cheeseim.queue.listeners.<stable-group>`，支持环境变量调整。

合理性：

- consumer group 是数据消费契约，不应与线程数一样作为任意部署参数；将“身份”和“吞吐旋钮”分开可避免高风险误配置；
- Spring Kafka listener 配置只会影响 `@KafkaListener` 容器，而本项目自行创建
  `ConcurrentMessageListenerContainer`；保留这些配置会造成运维看到的值与运行值不一致；
- retry 总次数采用包含首次执行的定义，避免 Kafka `FixedBackOff(3)` 实际执行 4 次而 Chronicle 执行 3 次。

内聚性与一致性：

- 配置绑定与默认值在 `QueueProperties`，注解处理器只负责把解析结果传给 QueueAdapter；
- KafkaProperties 仍只管理 Kafka transport（bootstrap、fetch、offset、serializer），不承载跨后端业务消费策略；
- 各业务模块只声明自己 group 的容量参数，不复制 ACK、DLT 或 retry 实现。

后续价值：

- 可以按 group 独立扩展 delivery/outcome/offline/ingress/history 的实例内并发，无需改 Java 注解或误用 Spring Kafka 配置；
- Kafka 与 Chronicle 的失败注入结果现在可直接对比，为 QueueAdapter 契约测试和切换后端提供可靠基线；
- 后续可在同一配置模型增加 max in-flight、pause threshold、retry topic，而不向各 listener 散落参数。

遗留风险：

- Chronicle 是单机开发后端，当前每个订阅仍使用单 tailer；配置 concurrency 大于 1 不会获得 Kafka 式分区并行，不能用于生产容量结论；
- Kafka 非 batch listener 的 `max.poll.records/fetch` 继续属于 `spring.kafka.consumer` transport 参数，若要逐 group 调优需引入 per-listener consumer factory 配置；
- 本任务没有验证真实 rebalance、DLT broker 故障和长 handler 对 `max.poll.interval.ms` 的影响。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-core:compileJava :postman:compileJava :postmaster:compileJava :postbox:compileJava` 通过；
- `./gradlew :common-core:compileTestJava :postman:compileTestJava :postmaster:compileTestJava :postbox:compileTestJava` 通过；
- 配置静态扫描确认 module 配置中已无 `spring.kafka.consumer.group-id`、`spring.kafka.listener.concurrency/ack-mode`；
- `git diff --check` 通过。

### C-01A session 本地复核租约

实现摘要：

- 连接通过一次性 WS ticket 认证成功时，在 `ConnectionContext` 记录最近服务端校验成功时间；
- `ConnectionSessionGuard` 使用默认 60 秒、下限 5 秒的本地正向租约；租约内只校验连接本地认证上下文；
- 租约到期后同一连接通过同步区只允许一个业务线程回源 authcenter；
- 删除每次复核的第二次 `matchesTokenVersion` RPC，因为 `isSessionValid` 已同时校验 session active、ban 和当前 tokenVersion；
- Dubbo 复核显式 `retries=0`，避免 authcenter 短暂故障时由每个连接产生框架级重试风暴；
- 租约配置收口到 `cheeseim.postoffice.session-validation.interval-ms`。

合理性：

- 主动 session 撤销仍通过既有跨节点 kickoff 立即关闭连接，本地租约不是撤销主通道；
- 周期复核只负责 kickoff 丢失、节点短暂隔离等异常兜底，因此允许明确的最长失效窗口；
- authcenter 基础设施异常不会被当作“session 已撤销”强制断开；当前业务请求失败，下一次到期检查继续回源。

内聚性与一致性：

- 校验时间属于连接本地生命周期，保存在 `ConnectionContext`，没有把瞬时租约写入 Redis 或 session 领域对象；
- 安全真相仍由 authcenter 的 `isSessionValid` 提供，postoffice 不复制 ban/tokenVersion 判定；
- 所有调用 `ConnectionSessionGuard` 的聊天、已读、撤回、输入中、delivery ACK 与心跳共享同一租约，而非只优化某一个 handler。

后续价值：

- 以 30 秒心跳、60 秒复核为例，单连接从每分钟 4 次 Dubbo 调用降到最多 1 次，且其它高频命令共享结果；
- 复核间隔成为可压测、可按安全 SLA 调整的显式参数，为后续本地签名校验或撤销版本广播保留 seam；
- C-01B 可独立优化 Redis 路由写入，不再与 session RPC 语义耦合。

遗留风险：

- kickoff 失败时，已撤销连接最长可在租约期内继续使用；生产必须把该参数纳入安全 SLA 和告警；
- 当前未记录 lease hit/miss/remote failure 指标，容量验证前应补齐；
- 多连接属于不同本地 lease，不按 session 共享；这是为了避免全局缓存失效复杂度，极端多端用户仍会各自周期回源。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :postoffice:compileJava :postoffice:compileTestJava` 通过；
- 完成认证后首次心跳、租约命中、并发到期、真实撤销、authcenter 故障与 kickoff 丢失窗口人工审计。

### C-01B 路由心跳合并与批刷

实现摘要：

- `HeartbeatMessageHandler` 不再同步访问 Redis，只把连接最新心跳覆盖写入节点本地
  `RouteHeartbeatBuffer`；
- 同一 connection 默认 60 秒最多形成一条持久化心跳，调度器每秒扫描到期项，单批默认上限 20000；
- `RedisOnlineRouteService.refreshBatch` 先 pipeline 执行用户路由单 key Lua，再只对用户路由仍匹配的连接
  pipeline 刷新 session 反向索引，避免孤儿 session 路由被续期；
- 用户路由和 session 索引都增加独立 `connection:*` field；迟到心跳只有 connectionId 仍匹配时才能刷新；
- unregister 从 Java 先读后无条件删，改为 Redis 内 compare-and-delete；旧连接断开不再删除同设备的新连接；
- session 索引采用 route/heartbeat/connection 三类 field，查询时合并最新 heartbeat，避免每次心跳重写整份 JSON；
- 批刷失败保留本地最新值并退避 5 秒，下一周期重试；同一连接不会积累心跳队列。

合理性：

- 本地连接表是节点内即时存活真相，Redis 路由只需满足跨节点发现 SLA，无需跟随每个客户端 heartbeat 同频写入；
- 60 秒持久化间隔远小于当前 30 分钟 route TTL，也小于 stale 路由容忍窗口，Redis 短故障不会立即误判离线；
- pipeline 只降低网络 RTT，不宣称跨用户/跨槽原子；每条 Lua 仍是单 key，兼容 Redis Cluster；
- 用户路由先成功、session 索引后刷新，宁可短时缺少反向索引，也不允许用户主路由已失效时续活孤儿 session。

内聚性与一致性：

- buffer 只做本地合并/调度，writer 端口只表达批量持久化，Redis key 和 Lua 仍全部位于 `RedisOnlineRouteService`；
- connectionId 同时保护 heartbeat、unregister 和 stale cleanup，三条状态迁移不再各自实现不同竞态规则；
- 心跳业务 handler 回归 transport 语义，不承担 Redis 结构、序列化和错误恢复。

后续价值：

- 30 秒客户端心跳、60 秒持久化时，Redis 路由写频率至少减半，并从每连接同步多 RTT 变为批量两阶段 pipeline；
- buffer 大小受节点连接数约束而不是心跳次数约束，适合进入 10 万级单节点长连接压测；
- `OnlineRouteHeartbeatWriter` seam 可继续替换为分片 worker、Redis Stream 或节点 lease，而不修改协议 handler。

遗留风险：

- 调度扫描当前遍历 pending map；单节点百万连接时需要以时间轮/分桶替代 O(n) 扫描，目标节点容量应先控制在 5–20 万连接；
- 默认单批 20000 会形成较大的 pipeline 返回列表，必须通过 Redis 延迟、堆占用和 scheduler duration 指标校准；
- postoffice 非优雅崩溃后路由仍依赖 30 分钟 stale/TTL 清理；B-02 会补偿投递失败，但更快的节点 lease 清理仍值得单独设计；
- 新增 `connection:*` field 属于 Redis 路由结构升级，滚动期间旧节点不会写该字段；新节点对旧 field 采取保守不刷新/不误删策略，部署完成后旧路由自然过期；
- 尚未执行 Redis Cluster pipeline、重连风暴、旧连接迟到心跳和百万连接内存压测。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :postoffice:compileJava :postoffice:compileTestJava` 通过；
- 完成同连接覆盖、写失败退避、旧/新连接交错、主路由缺失、session 索引失败和 cluster 单 key Lua 人工审计。

### C-02 群 fanout worker 化

实现摘要：

- 新增内部队列契约 `GroupFanoutEvent` 与 `group-fanout` topic，任务携带稳定 jobId、groupId、
  conversationId、是否首会话和已分配 seq 的消息模板；
- ingress 对普通群只发布按 groupId 分区的紧凑 fanout job，不再同步查询成员、逐成员生成 delivery、
  推进用户水位或批量创建首会话；
- `GroupFanoutEventListener` 独立消费任务，查询一次成员快照，按既有 planner 切片，批量发布 per-member
  DeliveryEvent，再单调推进成员 maxSeq/unread 与异步 Mongo 水位；
- 超级群仍保持读扩散，不产生 fanout job；
- fanout group 并发由 `cheeseim.queue.listeners.postmaster-group-fanout.concurrency` 独立配置，默认 3。

合理性：

- ingress consumer 的职责收敛到校验、稳定 seq、历史与任务可靠入队，耗时不再随普通群成员数线性增长；
- fanout job broker ACK 后 ingress 才能完成，崩溃重放可能产生重复 job，但不会静默丢失扩散任务；
- worker 部分成功后重放是安全的：在线投递由 serverMsgId/user/connection 去重，用户 maxSeq/unread Lua
  只在 requested seq 增大时计算 delta，Mongo writer 使用最大水位；
- 同 groupId 作为 partition key，单群任务保持顺序；不同群可由 topic 分区与独立 consumer 并行。

内聚性与一致性：

- `IngressEventListener` 不再拥有成员切片执行逻辑；`GroupFanoutPlanner` 只负责 partition key 与批次规划；
- fanout event 是 common-api 的跨进程 DTO，topic 由 TopicNames/KafkaTopicConfiguration 统一登记；
- DeliveryEvent 仍使用原 Message protobuf，不为 worker 新建第二套客户端投递协议。

后续价值：

- 热点普通群只占用 fanout consumer，不再拖慢单聊 seq/history ingress；
- fanout topic lag、成员数、批次耗时和 DLT 可形成独立 SLO，并可单独扩容 postmaster worker；
- C-03 可以在 worker 边界把逐成员 Redis/Mongo 水位推进替换为 bulk writer，无需再次改 ingress。

遗留风险：

- ~~worker 查询执行时当前成员，缺少 membership version/as-of。~~ **C-05A 已修复**：事件携带权限阶段版本，
  worker 读取不可变成员 epoch；
- 普通群没有强制成员上限，超大群会在单 Kafka partition 上形成长任务；必须通过产品阈值自动升级为 SUPER_GROUP；
- worker 到 DLT 后不会自动 redrive，成员仍可经 history/sync 补消息，但实时投递 SLO 需要告警和受控重放工具；
- 逐成员 Redis 水位推进仍是 O(N)，Mongo 落库已由 C-03 bulk 化；Redis pipeline/bulk 仍待压测优化；
- 尚未执行热点群、成员变更竞态、Kafka 重放和 worker 崩溃 chaos。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :common-core:compileJava :postmaster:compileJava :postmaster:compileTestJava` 通过；
- 更新 ingress 测试源码，使普通群断言紧凑 fanout job、首会话标记和 ingress 不查询成员；
- 完成 broker ACK、部分批次失败、重复 job、同群排序、超级群旁路和成员快照漂移人工审计。

### C-03 用户 maxSeq writer bulk `$max`

实现摘要：

- `UserConversationSyncPointRepository` 新增批量 `MaxSeqUpdate` 契约，默认实现保留非 Mongo 适配器兼容；
- Mongo 实现使用一次 unordered `BulkOperations.upsert` 提交批内所有用户-会话水位；
- maxSeq 更新从 `$set` 改为 `$max`，字段缺失时由 `$max` 创建，其余 minSeq/readSeq 只在 insert 时补默认值；
- `UserMaxSeqPersistenceWriter` 继续按 `(userId, conversationId)` 聚合批内最大值，但一次调用 batch repository；
- bulk 失败时整批进入原有有界 fallback queue；部分已成功项重试由 `$max` 安全吸收。

合理性：

- “批量 drain”不等于数据库批量写；只有 Mongo driver bulk 才能减少网络往返和 write command 放大；
- maxSeq 是单调水位，多 postmaster 副本、fallback 优先级和异常重排都要求存储层以 `$max` 最终兜底；
- unordered bulk 允许不同用户文档并行执行；单条失败不需要维持跨用户顺序。

内聚性与一致性：

- writer 负责合并、队列、重试与背压，repository 负责 Mongo bulk 和原子更新算子；
- 单条 `updateMaxSeq` 复用 batch 实现，避免单条路径仍保留 `$set` 的第二套语义；
- C-02 worker 与 direct ingress 继续调用同一个 writer，不感知 Mongo API。

后续价值：

- 普通群 fanout 的 N 个成员水位可按 writer drain batch 合并为少量 Mongo bulk command；
- `$max` 使跨副本水平扩展无需依赖 JVM 内顺序，为调整 workerCount 和分区归属提供正确底座；
- 同一批量契约可后续推广到 readSeq/deliverySeq，但应分别审计其字段与设备维度。

遗留风险：

- Redis `advanceUserMaxSeq` 仍由 fanout worker 逐成员调用，C-03 只 bulk 化 Mongo 持久层；
- writer 默认 drain batch 200 为代码常量，尚未与 Mongo maxWriteBatchSize、文档大小和延迟压测联动；
- bulk 异常当前按整批重试，无法区分不可重试 schema/validation 错误，需按 Mongo error label 分类；
- shutdown 最终 flush 失败只记录 exhausted 指标，进程退出时仍可能只保留 Redis 热状态；
- 尚未执行真实 Mongo replica stepdown、partial bulk error 与多副本乱序集成测试。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-core:compileJava :common-core:compileTestJava :postmaster:compileJava :postmaster:compileTestJava` 通过；
- 完成旧值大于请求、bulk 部分成功、fallback 重排、跨副本乱序和 shutdown flush 人工审计。

### A-05B 登录可信身份源

实现摘要：

- HTTP 登录请求与跨模块 `AuthenticationCommand` 增加 `identityAssertion`，`userId` 仅保留为可选一致性校验；
- authcenter 新增 `LoginIdentityVerifier` 端口和签名 assertion 适配器，session 生命周期只接收
  `VerifiedLoginIdentity`，不再直接信任请求 userId；
- assertion 使用独立于 IM access token 的 HS256 密钥，强制校验 `sub/iss/aud/iat/exp/jti`、最大
  60 秒生命周期和有限时钟偏差；
- jti 经 SHA-256 后使用 Redis `SET NX` 消费至 assertion 过期；重复使用拒绝，Redis 不可用时
  fail-closed；
- 功能默认关闭，未显式启用时所有登录均拒绝；启用但密钥不足 32 字节或 issuer/audience/期限不合法时
  Spring 配置校验直接阻止启动；
- 新增稳定 `AUTHENTICATION_FAILED(1009)` 与跨模块 `BusinessException`，签名错误、重放和用户禁用统一
  返回 401，不泄露具体身份状态；
- 设备和平台上下文在一次性 assertion 消费前校验，参数错误不会消耗合法凭据。

合理性：

- IM 系统不应重复建设账户密码、短信验证码和 OAuth 生命周期；由账户域签发短期、定向 audience 的
  登录断言，使 authcenter 只负责把可信身份交换为 IM session；
- 默认关闭即拒绝避免“未接身份源时临时信任 userId”在生产遗留；身份源或 Redis 故障时不可用优于
  fail-open 签发越权 session；
- assertion 一次性消费关闭截获后的有效期内重放；最大生命周期限制降低共享密钥泄漏和日志误采集的影响面；
- 请求 userId 不参与身份判定，最多与 sub 比对，阻断客户端替换 userId 冒充其他用户。

内聚性与一致性：

- API Controller 仍只做 HTTP DTO 到领域命令映射；签名、claim 规则和 replay 状态全部归 authcenter；
- `LoginIdentityVerifier` 是唯一身份验证入口，后续密码、OIDC/JWKS 或多租户 identity provider 不修改
  `SessionLifecycleService`；
- replay key 由 common-core `RedisKeys` 统一命名，authcenter 显式声明 Redis 依赖，不依赖 Gradle
  `implementation` 的偶然传递。

后续价值：

- 账户服务可独立演进密码、短信、OAuth、风控和 MFA，IM 只消费统一 assertion；
- 可将当前单 HMAC verifier 替换为按 issuer/kid 选择 JWKS 公钥的 verifier，session/refresh/WS ticket
  链路无需变更；
- 稳定 `BusinessException + ErrorCode` 为逐步收敛现有 `IllegalStateException` 和 HTTP 路径猜错提供起点。

遗留风险：

- 仓库当前没有账户服务或 assertion 签发端；上线前必须由可信业务系统实现签发并安全注入
  `CHEESEIM_LOGIN_ASSERTION_SECRET`；
- Go SDK 的 `Login(userID, password, ...)` 目前甚至没有把 password 发到 HTTP，且尚未改为先从账户系统
  获取 assertion；启用本功能后旧客户端登录会得到 401，这是有意的 fail-closed 迁移断点；
- 当前使用对称 HS256，issuer 与 authcenter 共享密钥；多身份源和无共享密钥场景应升级为带 kid 的
  非对称签名/JWKS，并支持密钥轮换重叠窗口；
- jti 去重依赖 Redis 强一致可用性；Redis 故障会阻断新登录，需要独立登录可用性告警，不能改成放行；
- 尚未增加 assertion 签发/验证集成 fixture、Redis 重放和时钟偏差测试，本阶段仅完成编译与人工审计。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :common-core:compileJava :authcenter:compileJava :api-server:compileJava
  :authcenter:compileTestJava :api-server:compileTestJava` 通过；
- 完成缺字段、错误 issuer/audience、超长生命周期、未来 iat、过期、userId/sub 不一致、jti 重放、
  Redis 不可用和被禁用户的故障语义人工审计。

### C-04 网关节点连接准入与写背压

实现摘要：

- `ConnectionManager` 构造时读取统一的 connection 配置，`multiLoginStrategy`、`timeoutMs`、
  `maxConnections` 和 `maxConnectionsPerUser` 不再是未使用配置；
- 节点总连接数以 CAS 在 pending 注册前抢占，TCP 与 WS 共用一个节点预算且包含未认证连接，达到上限
  立即拒绝并关闭新 channel；
- 认证提升在 user 分片锁内应用 multi-login 踢线策略，再按踢线后的剩余连接检查单用户节点上限；
- TCP/WS channel 统一设置 Netty `WRITE_BUFFER_WATER_MARK`，写入前检查 `channel.isWritable()`；
  不可写返回明确失败，让现有 delivery claim abort/retry，而不是继续积累待发送字节；
- 连接管理器改为自身 `@PostConstruct/@PreDestroy` 管理调度器生命周期，TCP-only 模式也会初始化，
  WS server 不再越界销毁共享 manager；
- 暴露节点拒绝连接数和 unwritable write 次数 getter，并纳入周期统计日志。

合理性：

- 总连接预算必须在 pending 阶段生效，否则未认证连接洪泛仍可绕开 authenticated connection 限额；
- CAS 使不同 connection lock 分片并发注册时不会共同越过节点硬上限；
- `isWritable=false` 表示 Netty outbound buffer 已超过高水位，此时继续写会将慢客户端转换为堆外内存风险；
- 在线消息写失败沿用 A-06/B-02 的 claim abort、节点重试和离线补偿语义，不新增“丢弃但报成功”旁路。

内聚性与一致性：

- 准入计数、用户连接索引和写入口仍只由 `ConnectionManager` 拥有，各协议 handler 只负责建连/关连；
- TCP 与 WS 各自配置 transport watermark，但共享节点连接预算，避免两个 server 各自宣称可容纳完整上限；
- manager 自己拥有 scheduler 生命周期，transport server 不再控制另一个组件的初始化销毁。

后续价值：

- C10K/C50K/C100K 压测现在有真实容量阀门，能通过拒绝数和 unwritable 次数区分连接饱和与慢消费者；
- watermark 和 max connection 都可按节点规格从环境变量校准，无需改代码；
- 现有单用户本地策略为后续 Redis 全局 login lease 提供清晰 seam，本地索引仍作为最终 channel 执行层。

遗留风险：

- `maxConnectionsPerUser` 与 multi-login 仍是单节点决策；同用户连接散落多个 postoffice 时可能超过全局上限，
  后续必须用 Redis 单槽 Lua 维护 user/device login lease，并按 gatewayNode 踢线；
- `isWritable=false` 当前拒绝本次写但不立即关闭 channel，持续慢消费者依赖重试上限、超时和客户端恢复；
  应在压测后确定连续背压阈值及主动断连策略；
- 默认 32/64 KiB watermark 对聊天小包偏保守，但需要以 direct memory、NIC 吞吐和弱网分布校准；
- 周期统计尚未注册 Micrometer counter；D 阶段应补 rejected/unwritable/current/max gauge 与告警；
- 按阶段约定未执行重连风暴、慢读客户端和 TCP-only 启动测试。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :postoffice:compileJava :postoffice:compileTestJava` 通过；
- 完成并发抢占最后一个 slot、pending 重复注册、认证提升超限、踢旧换新、unwritable delivery、
  WS-disabled/TCP-only 生命周期和关闭后计数回收人工审计。

### C-05A 群成员版本化 epoch 快照

实现摘要：

- `group.membershipVersion` 成为成员集合的单调版本；发送权限结果携带校验时观察到的权威版本，
  ingress 原样写入 `GroupFanoutEvent`，不再以消息时间近似成员快照；
- 新增 `group_member_epoch` 历史事实表，以
  `[joinedVersion,leftVersionExclusive)` 表达成员生命周期；退群再入群产生新 epoch，旧记录不删除；
- worker 以 `(joinedVersion,userId,epochId)` 三元 keyset 分页，单页最多 2000 人，内存不再随群规模线性增长；
- 存量群首次进入权限链路时先幂等写入确定性版本 1 epoch，再 CAS 发布 `membershipVersion=1`；
  未完成基线的版本 0 任务 fail-closed，不回退当前成员集合；
- 发送者权限缓存 key 纳入 membershipVersion；群元数据使用独立短缓存，成员 mutation 可按 groupId 精确失效。

合理性：

- `joinTime <= messageTime` 只能排除后来加入者，无法恢复已退出成员；半开版本区间才是可重放的 as-of 快照；
- 三元游标避免同版本批量入群以及同用户多次入群时跳项或重复翻页；
- 基线采用“先写事实、后发布版本”，本地无事务模式也不会让读路径先看到可用版本再读到空集合；
- 权限校验与扩散共用同一版本，避免 ingress 再发一次群 RPC 产生 TOCTOU。

内聚性与一致性：

- `group_member` 保持当前成员资料读模型，`group_member_epoch` 只保存历史成员关系，两类职责没有混入同一文档；
- epoch 仓储不向业务层泄漏 Mongo Document；跨模块仍使用 common-api 领域对象和 typed Dubbo 契约；
- 旧 joinTime 分页契约已删除，避免两套“快照”语义长期并存。

后续价值：

- fanout 消费重试、积压恢复和人工 redrive 都能读取任务创建时的同一成员集合；
- epoch 模型可复用到群历史可见性、成员审计和按加入版本补会话，而不扫描操作日志还原状态；
- keyset 分页为百万成员超级群的后台迁移/审计提供稳定 seam，尽管实时消息仍应走读扩散。

遗留风险：

- epoch 集合索引必须纳入正式 Mongo migration；仅依赖 Spring 注解不能替代生产变更流程；
- 首次访问的惰性基线会读取一次全群成员，大群迁移应在发布前离线预热，不能把首次尖峰留给在线请求；
- 元数据缓存失效不在 Mongo 事务/outbox 内，进程在提交后、evict 前崩溃会保留最多 2 秒旧版本；
- 本任务尚未保存 fanout job 分页进度，worker 失败仍从第一页重放；
- `GroupFanoutEvent.messages` 尚未按序号范围/字节上限拆分，500 条大消息可能超过 Kafka 单条限制。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :common-core:compileJava :business:compileJava :postmaster:compileJava`
  通过；
- `compileTestJava` 同步通过，证明已有测试夹具未因构造器和契约变化失编；
- 完成退群后重试、退群再入群、同版本批量入群、空群基线、版本 0 失败和游标不前进人工审计。

### C-05B 群成员统一版本化 mutation

实现摘要：

- 新增 `GroupMembershipCommandService`，批量入群/退群共享一个 membershipVersion；
- cluster profile 下通过现有 `PersistenceTransactionExecutor` 在同一 Mongo 事务内递增群版本、
  打开/关闭 epoch 并更新当前成员表；
- mutation 在事务内再次读取当前成员，过滤重复加入和重复移除，空变更不消耗版本；
- epoch 使用确定性 ID，并用 `(groupId,userId,joinedVersion)` 与
  `(groupId,userId,leftVersionExclusive)` 唯一索引阻断重复生命周期；
- 提交后精确失效群权限元数据；旧发送者缓存因 key 含旧版本而自然隔离。

合理性与内聚性：

- 群版本必须与成员事实共同提交；让 Controller 或不同业务服务分别调用三个仓储会产生不可恢复的中间态；
- command service 负责业务原子性，仓储只负责单一集合持久化，符合业务层拥有事务边界的分层；
- 当前态表服务低延迟权限/成员查询，epoch 服务历史快照，各自索引围绕自己的访问模式设计。

后续价值：

- 后续建群、审批入群、邀请、退群、踢人和批量导入只需复用一个入口；
- membershipVersion 可作为成员变更事件、缓存失效和 fanout checkpoint 的 fencing token；
- 批变更共享版本，避免大批导入为每个用户制造一次群级缓存抖动。

遗留风险：

- 仓库目前尚无完整群管理 HTTP/业务用例，后续新增时必须调用 command service，禁止直接调用
  `GroupMemberRepository.saveAll/removeAll`；
- all-in-one 默认关闭 Mongo 事务，仅适合本地联调；生产必须使用 cluster profile、副本集/分片集群和事务；
- Mongo transient transaction error 尚未做有界重试，冲突时会失败返回，不能由 Dubbo 自动重试写 RPC；
- cache evict 仍应升级为事务 outbox/CDC 失效事件，以消除提交后进程崩溃窗口；
- 尚未提供存量群离线批迁移、索引 preflight 和回滚脚本。

验证证据：

- 按阶段约定未执行单元测试；
- 主代码和 `:common-api/:common-core/:business/:postmaster:compileTestJava` 均通过；
- 完成并发重复加入、重复移除、退群再入群、空变更不升版本、群不存在和本地非事务故障窗口人工审计。

### C-05C fanout job 游标 checkpoint 与消息体分片

实现摘要：

- ingress 用 `GroupFanoutPlanner.partitionMessages` 同时按消息数和保守字节估算拆 job，默认最多 50 条 /
  512 KiB 估算；`MessageProducer` 再以 768 KiB 实际 JSON wire 大小做硬拒绝；
- 默认成员页和 delivery batch 都收敛为 200 人，使单 job 单页默认最多生成约 1 万条 delivery，
  将一次 lease 内的工作量限制在可压测范围；
- 超过一页的大群使用 `group_fanout_job` 保存 owner、lease、generation、权威 membershipVersion 和
  `(joinedVersion,userId,epochId)` checkpoint；
- 每页 delivery 取得 broker ACK、Redis 水位推进且 Mongo writer 入队后才 CAS checkpoint；
  lease/generation 丢失时不得提交旧 worker 进度；
- 正常异常会释放当前 lease；进程崩溃后新 worker 最多等待 60 秒接管；
- 完成记录保留默认 8 天并使用 TTL index 清理，高于默认 Kafka 7 天 retention；
- 不超过一页的小群不创建 job 文档，重试最多重放一页并复用既有 delivery 去重和 `$max` 水位幂等，
  避免海量小群在 Mongo 形成每消息批一条的任务写放大。

合理性：

- checkpoint 必须位于 broker ACK 之后，否则崩溃会越过未投递成员；位于 ACK 之后则最坏只重复当前页；
- generation 是 worker fencing token，单靠 owner 字符串或过期时间无法阻止暂停后恢复的旧进程覆盖新进度；
- membershipVersion 首次 claim 后固化在 job 状态中；同一 ingress 消息因权限重算产生的新 event
  不能把已启动 job 切换到另一成员快照；
- 小群一页重放的成本有界，复用主链路幂等比为每个小群事件保存 8 天 job 状态更符合百万 DAU 成本模型；
- 实际 wire hard limit 与估算拆分双保险，避免 Jackson 字段/转义开销使估算低于真实 Kafka record。

内聚性与一致性：

- `GroupFanoutJobStore` 只负责租约、fencing 和游标，不保存消息 payload；消息事实仍由 broker/history 持有；
- Mongo 实现位于 common-core，postmaster worker 只依赖 store 端口；
- job status 使用稳定 `code/desc/fromCode`，本地 claim status 明确限定为节点流程枚举；
- ingress 仍只负责可靠发布紧凑任务，worker 仍拥有成员枚举和 per-user 扩散，没有职责回流。

后续价值：

- 热点群 worker 崩溃或 rebalance 后可从最近完成页继续，重放量从 O(全群成员) 降为 O(page size)；
- job generation/lease 可继续用于管理面查询、卡死任务接管和受控 redrive；
- job 字节/消息/成员页三个上限提供明确压测旋钮，可据 broker RTT 和 producer transaction 时长校准。

遗留风险：

- 单页处理期间没有独立 lease heartbeat；若 200 × 50 的默认工作量仍超过 60 秒，rebalance 时可能并发重放一页，
  generation 会阻止旧 worker checkpoint，但需要以压测决定降低 job/page 或增加处理内续租；
- `group_fanout_job` 与 epoch 新索引仍需生产 migration/preflight，Mongo 注解不能替代部署脚本；
- completed TTL 必须始终大于实际 GROUP_FANOUT topic retention；当前只是默认 8 天对默认 7 天，
  尚未做启动期跨配置校验；
- 小群选择有界重放而非 job 完成去重，正确性依赖现有 delivery dedup、Redis 单调水位和 Mongo `$max`；
- DLT 查询、告警、redrive 和 job 管理 API 尚未建设。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :common-core:compileJava :business:compileJava :postmaster:compileJava
  :postmaster:compileTestJava :bootstrap-all:compileJava` 通过；
- 完成 broker ACK 前失败、页后 checkpoint 失败、worker 崩溃接管、旧 generation 迟到提交、重复 event、
  小群无 job 写放大、超大 wire event 和完成 TTL 人工审计。

### C-05D fanout completed retention 启动守卫

实现摘要与复核：

- Kafka topic contract validator 同时读取 fanout completed retention；
- completed retention 不严格大于 topic retention 时启动失败，不再依赖运维记住文档约束；
- 校验使用秒级下界比较，避免毫秒乘法溢出；Chronicle 本地模式不加载 Kafka validator。

合理性、内聚性与后续价值：

- topic retention 的权威配置已集中在 `KafkaTopicConfiguration`，跨 topic/job 生命周期约束也应在同一启动守卫；
- 失败发生在节点接流量前，优于事件重放时才发现 completed 记录已被 TTL 清理；
- 该 seam 后续可继续校验 ingress inbox TTL、DLT retention 和 replay window。

遗留风险：

- 当前只比较声明配置；真实 topic retention 由已有 cluster validator 读取验证，但尚无真实 Kafka 启动证据；
- max wire bytes 与 broker `max.message.bytes` / producer `max.request.size` 仍未形成同类跨配置校验。

验证证据：

- 按约定未运行单元测试；
- `:common-core/:business/:postmaster/:bootstrap-all` 相关编译链通过。

### B-04A 精确连接替换契约

实现摘要：

- `KickoffCommand` 增加可选 `connectionId`；节点队列消费端和本地 kickoff 执行端都按
  connectionId → device → session → user 的顺序选择目标；
- `ConnectionManager.kickConnectionById` 只操作完全匹配的本地连接，目标已离线时 NOOP，不做范围降级；
- `RouteSnapshot` 增加稳定 `platformId`，连接注册时写入 `PlatformType.code`，为跨节点同终端/
  同类别策略提供无需解析展示名称的判定字段；
- 旧 KickoffCommand 和旧 RouteSnapshot 缺少新增字段时保持原语义。

合理性与内聚性：

- 重连替换必须瞄准原 connectionId；若按 deviceId 踢线，命令延迟到达时可能把已取代旧路由的新连接踢掉；
- RouteSnapshot 是跨节点路由事实，平台 code 属于 admission 所需的最小路由元数据，不应从 deviceId
  或 platform 展示字符串猜测；
- 目标选择规则统一在本地执行边界，节点队列只传 typed command，不创建第二套控制消息。

后续价值：

- B-04B Redis 原子 admission 可以返回被替换的 RouteSnapshot，并可靠发布精确踢线命令；
- 精确 connection 终态也可复用于重复登录风控、节点 drain 和单连接运维诊断。

遗留风险：

- 本任务只建立契约，尚未让 Redis 原子选择全局 victim；
- 节点命令发布失败的补偿/回滚必须在 B-04B 状态机中定义，不能仅记录日志；
- 旧节点不会识别 connectionId；滚动升级期间必须先升级消费端，再启用全局 admission。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :postoffice:compileJava :postoffice:compileTestJava` 通过；
- 完成迟到精确命令、旧连接已消失、旧命令反序列化和新增 route 字段缺失的人工审计。

### B-04B Redis 全局 login lease

实现摘要：

- 新增 `LoginLeaseStore` 端口与 Redis 实现；每个 `(tenantId,userId)` 使用安全 fingerprint hash tag 下
  的 active ZSET + metadata HASH，两 key 在 Redis Cluster 同槽；
- CLAIM Lua 一次完成：清理过期 lease、强制同 device 单 owner、执行四种 `MultiLoginStrategy`、
  检查全局 `maxConnectionsPerUser`、递增 generation、逻辑 fencing 冲突连接并注册新 lease；
- lease 按物理 connection 计数，不再错误地用按 device 折叠的 route field 数估算连接数；
- RENEW/RELEASE 都比较 `(connectionId,generation)`；旧连接迟到续租、注销或 kickoff 不能影响新 owner；
- claim 返回 victim 的 node/connection/generation，当前节点精确关闭，远端复用可靠 per-node queue；
- 服务端每 60 秒主动扫描本地已认证连接并 pipeline 续租，不依赖客户端心跳；FENCED/MISSING 连接立即关闭；
- 默认 lease 180 秒，要求至少为 renew interval 两倍；优雅停机批量 CAS release，崩溃后由 TTL 收敛；
- RouteSnapshot 发布 `loginLeaseVersion/generation`，KickoffCommand 携带 generation fencing；
- enforce 模式下 Redis/claim 异常 fail-closed；功能默认关闭，支持先部署代码再集中启用；
- Redis Cluster profile 显式强制 database 0，避免继承 standalone 的 DB 1 后执行不受支持的 SELECT。

合理性：

- 用户级 active/meta 同槽 Lua 是全局限额和冲突替换的唯一权威；JVM user lock 只能作为本地索引保护；
- 同 device 无论策略如何都只能有一个 owner，关闭“本地多个 connection、Redis 只保留一个 route”的语义分叉；
- 逻辑 fencing 先于物理踢线，因此旧节点宕机不会阻塞新 owner；generation 让迟到命令安全 NOOP；
- 服务端主动续租确保不发 heartbeat 的恶意客户端也会在最多一个 renew 周期内发现 lease 丢失；
- Redis 长故障期间不签发新 lease；现有连接保留待续租项，恢复后缺失 lease 会被关闭，避免静默 split-brain。

内聚性与一致性：

- `LoginLeaseStore` 只拥有登录所有权状态机；`ConnectionManager` 仍拥有本地 channel/index 和物理关闭；
- v2 在线路由继续负责投递发现，v3 lease 只负责登录 policy，不让可重建 session 辅助索引参与正确性判定；
- 节点替换继续复用 typed `KickoffCommand` 和可靠 NodeQueue，不增加 fire-and-forget 控制通道；
- key 统一由 `RedisKeys` 生成，tenant/user 原值不会进入 Redis hash tag。

后续价值：

- 多 postoffice 副本可以真正执行全局连接上限和同终端策略，为水平扩容与重连风暴测试建立正确底座；
- login lease generation 可继续用于节点 drain、连接迁移和路由读侧过滤陈旧 victim；
- active ZSET 能直接提供全局在线连接事实，后续可派生运维查询而不扫描所有 gateway JVM。

遗留风险：

- 安全发布必须两阶段：先所有节点部署识别 generation/精确 kickoff 的版本并 drain 或重启旧连接，再集中设置
  `CHEESEIM_POSTOFFICE_LOGIN_LEASE_ENFORCE=true`；旧节点混部时不能开启；
- claim 已逻辑移除 victim，但跨槽 node queue 发布无法与 claim 同事务；发布失败时新连接会被拒绝并释放，
  victim 保持 fenced、依赖服务端续租发现后关闭，可能造成用户短时全部离线但不会放大权限；
- v2 路由与 v3 lease 是双写；session reverse index仍是可重建辅助索引，故障时可能短暂残留；
- victim 物理关闭为异步节点命令，claim 后极短窗口仍可能收到在线投递；需要以 node queue ACK/续租周期定义 SLA；
- 当前未增加真实 Redis Cluster/Testcontainers Lua 测试、双节点并发 claim 和节点 kill 集成证据；
- metrics 尚缺 claim/reject/fenced/renew-failure/active lease 指标。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :common-api:compileJava :common-core:compileJava :postoffice:compileJava
  :postoffice:compileTestJava` 通过；
- 完成同 device、四策略矩阵、全局上限、双 claim 顺序化、过期 prune、旧 generation renew/release、
  Redis 故障恢复、部分 victim 发布失败、节点崩溃 TTL 和优雅停机人工审计。

### B-05A Mongo shard-key 写路径与安全 migration

实现摘要：

- 补齐文档已登记但仓库实际缺失的 `distro/mongo/enable-im-sharding.js` 与运行手册；
- migration 强制经 mongos 执行，先对所有目标集合做 shard-key preflight，再创建索引和分片；
  已有不兼容 key、同名异构索引或非 mongos 连接明确失败；
- `message_block` 使用 `{conversationId:"hashed"}`，历史块 upsert/点查显式携带 conversationId；
- `message_id_mapping` 使用 `{serverMsgId:"hashed"}`，mapping upsert 显式携带 serverMsgId；
  同一 client mapping 若试图绑定不同 serverMsgId 会命中 `_id` 冲突而不是静默覆盖；
- `group_member_epoch` 使用 `{groupId:1}`，保留群内 keyset 分页局部性及 group 前缀唯一索引；
- `group_fanout_job` 使用 `{_id:"hashed"}`，所有租约/checkpoint 操作本来就是 jobId 精确查询；
- B-05A 首次落地时明确暂缓 `conversation`；B-05B 完成专用反向读模型后，migration 才增加其复合 shard key。

合理性：

- shard migration 不能只声明 DDL；所有 upsert 必须在 query 中携带完整 shard key，否则非空分片集合会在运行时失败；
- history block 的主要读取是单会话 seq range，按 conversationId 共址比按 `_id` hash 后 scatter-gather 更合理；
- epoch 需要群内有序分页和唯一约束，range groupId 是当前约束下的正确取舍；超级群消息本身不走写扩散，
  普通群单群写热点有产品上限；
- conversation 的主读是 owner 维度；B-05A 没有在反向偏好查询仍存在时强行分片，
  避免把隐藏全扫升级为跨 shard 热点。

内聚性与一致性：

- shard-key 查询由拥有 Mongo 实现的 common-core 维护；业务与 postmaster 不感知 Mongo 路由字段；
- migration 位于统一 `distro/mongo`，并在根文档索引登记，不依赖应用启动时自动建索引；
- 脚本索引名称与 Document 注解一致，避免开发环境与生产 migration 形成两套命名。

后续价值：

- 历史块、ID 映射、成员 epoch 和大群 checkpoint 可在真实 sharded cluster 上做 targeted smoke；
- preflight 为滚动发布建立“先代码携带 shard key、后 DDL”的安全顺序；
- B-05B 获得清晰问题边界：先拆 conversationId → delivery preference 读模型，再分 conversation 当前态。

遗留风险：

- 本阶段没有可用 mongos 环境，脚本只完成 JavaScript 语法校验，没有真实 shardCollection/chunk migration 证据；
- 生产首次迁移仍需备份、停写/在线迁移选择、容量预估、balancer 窗口、回滚和 smoke runbook；
- range `groupId` 的分布依赖群 ID 基数；若存在单调/业务前缀 ID，需要预分片或改造 ID 生成；
- `message_id_mapping` 按 serverMsgId 分片后，按 conversationId 的审计查询会 scatter-gather，但该路径不是消息历史主读；
- 其它当前态集合尚未全部完成访问模式与 shard-key 审计，不能据此宣称 Mongo 全库已生产分片就绪。

验证证据：

- 按阶段约定未执行单元测试或真实 Mongo 集成测试；
- `node --check distro/mongo/enable-im-sharding.js` 通过；
- `./gradlew :common-core:compileJava :common-core:compileTestJava :postmaster:compileJava` 通过；
- 完成 shard key 缺失 upsert、不兼容既有 key、重复执行、conversation 误分片、epoch 唯一索引和 TTL index 人工审计。

### B-05B conversation 反向偏好读模型与分片

实现摘要：

- 新增 `conversation_delivery_preference`，只保存非默认会话接收偏好；
  离线推送的 BLOCK 用户查询不再扫描 owner 维度 `conversation`；
- `ConversationServiceImpl.setConversations` 在现有 Mongo 事务内批量写当前态和偏好读模型，
  恢复 RECEIVE 时删除冗余记录，删除会话时同步删除偏好；
- 偏好批写使用 unordered bulk，同批用户不新增逐条 Mongo RTT；
- BLOCK 用户缓存切换 v2 key，TTL 从通用 12 小时缩短为 30 秒，事务提交后仍精确失效；
- 删除 `UserConversationRepository` 中已无调用方的反向查询，防止新代码回退到 scatter-gather；
- `conversation` 所有 upsert/精确写显式携带 `(ownerUserId,conversationId)`；
- migration 先回填存量 `receiveOpt=BLOCK(1)`，再分片
  `conversation_delivery_preference(conversationId,ownerUserId)` 和
  `conversation(ownerUserId,conversationId)`；
- `UserConversationDoc.chatType` 显式映射既有 BSON 字段 `conversationType`，修复写读字段名漂移。

合理性：

- 同一集合不可能同时为 owner 会话列表和 conversation 反向全体过滤提供最优 shard key；
  以两个读模型分别服务两个访问方向比接受生产 scatter-gather 更合理；
- 当前态与派生偏好在 cluster Mongo 事务内提交，缓存只在 commit 后失效；
- 两个集合都使用范围复合 shard key，使唯一索引可包含完整 shard-key 前缀；
  这里不能盲目改用 hashed key 后仍宣称全局唯一约束有效；
- migration 的回填位于分片前，且要求先部署双写版本，关闭回填期间的新变更丢失窗口。

内聚性与一致性：

- business 决定何时改变会话偏好并拥有事务边界；
- common-core 分别封装 owner 当前态与 conversation 偏好读模型，Document 不泄漏到业务层；
- 离线推送继续经 `ConversationService` 契约查询，不直接依赖新增集合。

后续价值：

- 用户会话列表、详情和设置可按 owner 定向路由；群/单聊离线推送过滤可按 conversation 定向路由；
- 偏好读模型可继续扩展免打扰窗口、mention override 等投递策略，而不污染 owner 会话同步文档；
- 为真实 mongos targeted-query smoke 提供了两条可独立观测的访问路径。

遗留风险：

- all-in-one 默认非事务模式只适合联调；中途写失败可能造成当前态与读模型短暂不一致；
- commit 后进程在 cache evict 前崩溃仍可能产生最多 30 秒陈旧 BLOCK 集合；
  最终应使用事务 outbox/CDC 或将权威集合镜像为 Redis set；
- migration 尚未在大数据量验证 `$merge` 内存、耗时、oplog、balancer 和回滚；
- range shard key 的实际 chunk 分布依赖 owner/conversation ID 分布，需要基于生产样本预分片与热点检查；
- 偏好回填只覆盖 `receiveOpt=1`，执行前 preflight 会拒绝缺 owner/conversation 字段的脏 BLOCK 文档，
  但尚无自动修复工具。

验证证据：

- 按阶段约定未运行单元测试或真实 Mongo 集成测试；
- migration JavaScript 语法校验通过；
- `./gradlew :common-core:compileJava :common-core:compileTestJava :business:compileJava
  :business:compileTestJava :postmaster:compileJava :bootstrap-all:compileJava` 通过；
- 完成 RECEIVE/BLOCK/DND 切换、批量写、删除会话、事务回滚、回填与在线双写交错、缓存崩溃窗口和
  owner/conversation targeted query 人工审计。

### B-06 Kafka DLT 查询、审计与受控 redrive

实现摘要：

- 在 common-core 新增 `DltOperations` 端口及 Kafka 实现；只允许管理 topic，查询使用独立 consumer
  `assign + seek`，不加入 group、不提交 offset，只输出脱敏摘要；
- redrive 精确绑定 `(sourceTopic,partition,offset,checksum)`，每次 copy-not-delete 一条记录；
  不复制旧异常 headers，不沿用旧 CreateTime；
- `dlt_redrive_audit` 以 operationId 做 Mongo lease/generation fencing，永久绑定 operator 和 reason，
  broker ACK 后才完成审计；
- 新增无 HTTP/Dubbo 的 `ops-cli` 和独立 `application-ops.yml`，Kafka/Mongo 凭据与运维主机权限构成授权边界；
- 审计集合纳入 Mongo 分片 migration，新增 DLT 操作计数/耗时指标和运行手册。

合理性：

- 直接启动 consumer group 或提交 DLT offset 会破坏可追溯性；无位点查询与 copy-not-delete 保留原始证据；
- checksum 防止操作者查看后 retention/compact 变化导致重放另一条数据；单条上限控制事故爆炸半径；
- 新发布必须使用当前 CreateTime，否则旧消息可能因 retention 被 broker 立即清理；
- Mongo claim 关闭同 operationId 并发窗口，但不伪称跨 Kafka/Mongo exactly-once。

内聚性与一致性：

- common-core 拥有 Kafka/Mongo 基础设施语义，common-api 只保存跨持久化状态 code；
- ops-cli 只做参数与输出适配，不复制查询、校验、租约或发送逻辑；
- `QueueAutoConfigurer` 仅在 Kafka 且存在审计存储时装配运维端口，普通业务模块不会被运维能力反向绑死；
- topic allowlist 复用 `TopicNames`，避免 CLI、topic 创建器和业务生产者形成三套名称。

后续价值：

- dead letter 已具备定位、审批、精确重放和长期审计 seam，可接工单审批、双人复核和运维门户；
- operationId 与 DLT identity header 可用于链路追踪、重复检测与后续自动化处置策略；
- 固定低基数指标和 runbook 为 DLT backlog/最老年龄告警及 chaos 验收提供入口。

遗留风险：

- Kafka ACK 与 Mongo complete 不能原子提交；ACK 后进程崩溃再重试可能复制两次，依赖主链路稳定消息 ID/inbox 去重；
- 当前授权是运维机器与中间件凭据隔离，尚无企业 RBAC、双人审批或集中 secret broker；
- 审计默认长期保留，归档周期、合规删除和容量预算需由生产制度确定；
- 尚未连接真实 Kafka/Mongo 验证 retention 边界、broker 故障、ACL、重放后端到端结果和告警。

验证证据：

- 按阶段约定未执行单元测试；
- `./gradlew :ops-cli:compileJava :ops-cli:compileTestJava :common-core:compileTestJava
  :bootstrap-all:compileJava` 通过；
- `node --check distro/mongo/enable-im-sharding.js` 通过；
- 完成未知 topic/partition、越界 offset、checksum 漂移、operationId 冲突、并发 lease、broker 失败、
  ACK 后崩溃、DLT retention 与 payload 泄漏的人工设计审计。

### D-01 生产服务统一 OCI 镜像

实现摘要：

- 新增单一 `server/Dockerfile`，通过必填 MODULE 构建七个生产服务，白名单拒绝 all-in-one、ops-cli
  和 library module 被误发布为常驻业务服务；
- builder 固定执行目标模块 `bootJar`，运行镜像只保留 Java 17 JRE；
- Spring Boot jar 拆分为 dependencies/loader/snapshot/application 四层，提升跨版本和跨服务的 registry
  layer 复用率；
- 运行用户固定 UID/GID 10001，只有 `/app/logs`、`/app/data` 可写；默认 cluster profile；
- JVM 使用容器内存比例并在 OOM 时退出，由编排器接管恢复；新增 `.dockerignore` 限制构建上下文。

合理性：

- production 镜像必须与本地 all-in-one/Chronicle 明确隔离，白名单比接受任意 Gradle project 参数更安全；
- 非 root 与小运行时镜像降低权限和攻击面；显式 OOM 退出避免 JVM 半失活继续占用流量；
- 不在通用 Dockerfile 声明健康检查，因为各模块的管理端口、依赖和 drain 时序不相同。

内聚性与一致性：

- 镜像只负责 Java 进程产物与运行用户，不把 Nacos/Kafka/Mongo/Redis 编排复制进服务镜像；
- 六个模块复用同一构建入口和 JVM 基线，消除每模块 Dockerfile 漂移；
- 部署参数继续来自 cluster profile 环境变量，镜像没有第二套配置模板。

后续价值：

- Kubernetes/Helm、SBOM、签名和镜像扫描可以围绕同一 OCI 产物继续建设；
- 分层镜像降低仅业务 class 变化时的推送和拉取成本，有利于多模块频繁独立发布；
- 固定 UID 和可写目录为 read-only root filesystem、Pod Security 和 volume 策略提供稳定契约。

遗留风险：

- 当前基础镜像只固定 tag，尚未由供应链镜像同步流程锁定 digest；
- 尚未生成 SBOM、签名、漏洞扫描和 provenance；
- 当前环境未发现可用 Docker engine/hadolint，未做真实 image build/run；
- 六个 bootJar 为 155–204 MiB，暴露 common-core 传递过多 web/存储/native 依赖的问题，后续应做依赖瘦身；
- 健康探针、preStop/drain、PDB、NetworkPolicy 和资源模型属于后续 Kubernetes 小任务。

验证证据：

- 按阶段约定未执行单元测试；
- 七个生产模块 `bootJar` 均成功，产物主 jar 可执行；
- `postoffice` bootJar 的 layertools 成功列出并解包四个标准 layer；
- `git diff --check` 通过；Dockerfile 真实构建待有容器引擎的环境验收。

### D-02A common-core Web/Prometheus 依赖去外溢

实现摘要：

- common-core 的 `spring-boot-starter-web` 替换为源码实际需要的 `spring-web`；
- 删除 common-core 无专属使用点的 Prometheus registry；`micrometer-core` 保留统一指标 API；
- 明确 common-core 是 library，不拥有 HTTP server 或监控 exporter，可执行模块必须显式选择。

合理性：

- 一个 `RestTemplate` Bean 不应让所有 Dubbo/worker 服务携带并自动启动嵌入式 Tomcat；
- registry 属于进程出口，不属于共享指标定义层；否则未开放 actuator 的模块也携带无效 exporter；
- Chronicle、RocksDB、Dubbo 当前均有 common-core 源码引用，本任务没有通过 Gradle exclude 制造运行时
  `ClassNotFoundException`，而是将其留给 adapter 模块拆分。

内聚性与一致性：

- common-core 只保留编译其公共实现所需的客户端/基础设施依赖；
- postman 因自身显式声明 starter-web 继续保留 Tomcat，证明 Web 能力由 owner 决定；
- authcenter/postoffice 的 runtimeClasspath 已不再出现 Tomcat，模块声明与实际进程形态一致。

后续价值：

- 后续管理端口/Actuator 可以按模块独立定义，不会和业务 HTTP/TCP/WS 端口偶然耦合；
- 为拆出 `queue-chronicle`、`state-rocksdb`、`queue-kafka` adapter 模块提供了第一条明确依赖边界；
- 依赖 SBOM 和漏洞扫描结果会更接近模块真实攻击面。

遗留风险：

- 五个非 postman bootJar 仅减少约 4.7–4.8 MiB，仍为 150–167 MiB；
- common-core 仍同时编译 Mongo、Redis、Kafka、Chronicle、RocksDB 与 Dubbo，native/backend 依赖隔离未完成；
- D-02A 完成时 postbox/postman 的 actuator 声明仍缺依赖；该项已由 D-02B 统一解决；
- 未做进程启动 smoke，只有编译、打包与 dependencyInsight 证据。

验证证据：

- 按阶段约定未执行单元测试；
- 六个生产模块 `bootJar` 与 `:common-core:compileTestJava` 通过；
- `dependencyInsight` 证明 authcenter/postoffice 无 `tomcat-embed-core`，postman 仅通过自身
  `spring-boot-starter-web` 保留；
- bootJar 从 authcenter/business/postoffice/postbox/postmaster 的
  156.1/155.5/155.6/155.5/171.7 MiB 降至 151.3/150.7/150.8/150.7/167.0 MiB，
  postman 因显式 Web 依赖保持 204.3 MiB。

### D-02B 七服务显式管理端口与健康探针

实现摘要：

- 七个独立生产服务分别显式声明 Web runtime、Actuator 和 runtime-only Prometheus registry；
- common.yml 统一只暴露 health/info/prometheus，隐藏健康详情，启用 availability probes 和 graceful shutdown；
- authcenter/postoffice/postbox/postmaster/postman 禁用无意义业务 servlet 端口，分别只开放
  19084/19080/19082/19081/19083；business 保留 18085 并将管理面隔离到 19085；
- liveness 只含 `livenessState`；readiness 按模块加入 Mongo/Redis contributor；
- 文档明确管理端口只能由 kubelet、Prometheus 和运维网络访问。

合理性：

- 中间件故障进入 liveness 会造成全副本重启风暴，因此 liveness 只能反映进程是否需要重启；
- readiness 检查关键同步依赖，故障节点停止接收新连接/请求，同时进程保留以等待依赖恢复；
- exporter 和 management server 是进程级能力，必须由 executable module 显式拥有，不能再次放回 common-core；
- 独立管理端口避免 actuator 与业务 HTTP、Netty TCP/WS 或 Dubbo 共用暴露策略。

内聚性与一致性：

- 共享文件只定义端点集合、健康语义与 shutdown；各模块文件只定义自己的管理端口和依赖集合；
- 端口尾号与既有 Dubbo 端口 20880–20885 对齐，环境变量命名统一；
- `ImMetrics` 仍只依赖 micrometer-core，实际 registry 由六个进程在 runtime 装配。

后续价值：

- Kubernetes Deployment 可直接使用稳定的 liveness/readiness path，不需为每个模块重新发明探针；
- Prometheus 能覆盖全部独立进程，后续可建设统一 ServiceMonitor、SLO 与自动扩容指标；
- graceful shutdown 与 readiness state 为 preStop、连接 drain 和滚动升级提供基础。

遗留风险：

- 当前没有 Kafka Actuator contributor；consumer lag/broker 健康仍依赖 exporter 和业务失败指标；
- readiness 包含 Mongo/Redis 会在依赖故障时摘除全部副本，必须配合多 AZ 中间件和告警，不能替代降级设计；
- 尚未落 Kubernetes Service/NetworkPolicy，管理端口的网络隔离目前是部署约束；
- 未在真实进程验证 `server.port=-1 + management.server.port`、kubelet probe 和 shutdown 时 readiness 翻转。

验证证据：

- 按阶段约定未执行单元测试或进程启动 smoke；
- 六个原有生产模块 `bootJar` 与 common-core 测试源码编译通过；api-server 在 D-03A 补充验证；
- bootJar 人工检查包含 actuator、Prometheus registry 和 Tomcat runtime；
- 全部 YAML 可由解析器加载，仓库 `git diff --check` 通过；
- 配置人工审计确认无 `show-details: always`、无旧版 Prometheus export 路径。

### D-03A 独立 api-server 生产入口与真实 cluster overlay

实现摘要：

- api-server 新增独立 `ApiServerApplication`，加载 `application-api-server.yml`，以 Dubbo consumer
  调用 authcenter/business/postbox 等领域服务；
- 新增 HTTP 18079、management 19079、Tomcat 有界线程/连接配置和 Redis readiness；
- 启动类只扫描 `com.cheeseocean.im.apiserver`，不再借 all-in-one 才能提供客户 HTTP API；
- common-core project dependency 设为 non-transitive，API 只显式装配 `RedisIdempotencyStore`，
  runtime 不携带 Mongo/Kafka/Chronicle/RocksDB；
- Dockerfile 白名单扩展为七个生产服务；all-in-one component scan 显式排除独立 API 启动配置；
- 所有独立应用显式 import `application-cluster.yml`，该 overlay 增加 `on-profile: cluster` 激活条件。

合理性：

- 没有独立 HTTP 入口的“微服务集群”无法完成登录、ticket、会话同步和控制面操作，不能视为可部署拓扑；
- API 是无状态 adapter，只应持有 Redis 入口幂等和 Dubbo client，不应初始化消息存储或本地单机后端；
- `spring.config.name=application-{module}` 会改变配置 base name，仅激活 profile 不能加载另一 base name 文件；
  显式 import + profile activation 才是可验证的 overlay 关系；
- all-in-one 继续扫描 controller，但排除另一个 `@SpringBootApplication`，避免嵌套启动配置污染自动装配。

内聚性与一致性：

- HTTP DTO、认证拦截器和 Facade 仍全部留在 api-server；新增 main 只负责进程组装；
- Redis 幂等 bean 位于 api-server config，明确表达该入口唯一拥有的本地基础设施；
- API 与其它服务复用同一 Dockerfile、管理端点、cluster/Nacos 约定，没有新增第二套发布方式。

后续价值：

- Kubernetes/Helm 现在可以形成真实闭环：外部 HTTP → api-server → Dubbo 领域服务，
  长连接 → postoffice → 消息主链路；
- 74.4 MiB API bootJar 相比 150–205 MiB 全量 common-core 进程显著缩小，证明 adapter 依赖物理隔离有价值；
- non-transitive API 边界可作为拆分 common-core 其它 adapter module 的迁移样板；
- 显式 cluster import 让 Kafka、Redis HA、Mongo replica 和 Nacos namespace 配置终于与文档一致。

遗留风险：

- api-server 仍直接引用 common-core Redis adapter class，最终应拆成小型 `state-redis` adapter 模块；
- Dubbo starter 当前带入宽泛 Netty/Dubbo runtime，仍需依赖锁定和版本收敛；
- cluster overlay 仍包含所有中间件属性；虽然 API 不会绑定无类路径的 Mongo/Kafka配置，
  其它服务的数据所有权和最小权限凭据仍需继续拆分；
- 未在真实 Nacos/Redis 环境启动 API、执行登录/会话 smoke 或验证 readiness；
- API rate-limit 文档与当前源码存在漂移：只有 Redis key 常量而无实际 filter，应作为后续安全小任务修复。

验证证据：

- 按阶段约定未执行单元测试或真实进程 smoke；
- `:api-server:bootJar :api-server:compileTestJava :bootstrap-all:compileJava :config:processResources` 通过；
- API bootJar 为 74.4 MiB，包含 Redis、Actuator、Prometheus，不包含 Mongo/Kafka/Chronicle/RocksDB；
- 全配置 YAML 解析和 `git diff --check` 通过；
- 人工审计确认七个 base application 均显式 import profile-gated cluster overlay。

### D-03B API Redis Lua 限流实现复原

实现摘要：

- 新增覆盖 `/api/**` 的 `ApiRateLimitFilter`，以来源地址 SHA-256 指纹 + 固定窗口 bucket 生成 Redis key；
- 单 key Lua 原子执行 INCR + 首次 PEXPIRE，多 api-server 副本共享计数；
- 超额返回 HTTP 429、稳定错误体和精确到窗口边界的 `Retry-After`；
- 默认只使用 socket peer；显式配置 trusted proxy hops 后才从 X-Forwarded-For 右侧可信链解析客户端；
- Redis 异常 fail-open，并以节点本地 cooldown + 单 recovery probe 避免故障期间每请求等待 Redis timeout；
- 新增 allowed/rejected/unavailable 低基数指标；兜底 500 不再拼接内部异常 message。

合理性：

- 限流必须在 Redis 内原子递增和设置 TTL，Java `get + increment` 无法在多副本/并发下保证上限；
- 直接信任 X-Forwarded-For 等于允许客户端轮换伪造来源；可信跳数必须是显式部署决策；
- fail-open 符合 API 可用性策略，但没有本地 cooldown 会在 Redis 故障时把每个 HTTP 请求放大为超时和异常；
- Redis try/catch 只包围计数调用，不包围 downstream filter chain，避免业务异常被误判后重复执行请求。

内聚性与一致性：

- Filter 位于 HTTP adapter，Redis key 继续由 common-core `RedisKeys` 单一生成；
- 指标复用 `ImMetrics`，配置归 `module-api-server.yml`，不向其它业务模块复制限流实现；
- 边缘限流和应用限流职责明确：前者管理连接/带宽/DDoS，后者保护应用请求预算。

后续价值：

- API 水平扩容后仍共享来源预算，可直接对 rejected/unavailable 建告警和容量观察；
- trusted proxy 模型为未来 Ingress/Service Mesh 接入提供显式安全契约；
- 本地故障 cooldown 可推广到其它可 fail-open 的非关键 Redis 读路径，但不能用于 seq、lease 等正确性状态机。

遗留风险：

- 固定窗口在边界允许瞬时双倍突发；精细产品配额可升级 token bucket，但边缘层仍应先削峰；
- NAT 下多用户共享来源预算，登录/高成本端点还应增加独立规则和已认证 user/device 维度；
- fail-open 期间失去应用层限流，必须依赖边缘防护；
- 当前环境工具额度阻止再次调用 Gradle，新增 Filter 尚只有静态审计/YAML 解析证据，需下一可用窗口补编译；
- 尚未真实验证 Redis Cluster、可信代理链和 429 客户端退避。

验证证据：

- 按阶段约定未执行单元测试；
- 完成 Lua 原子性、TTL、窗口边界、代理头伪造、Redis null/exception、cooldown 并发恢复、
  downstream exception 不重入和敏感错误泄漏人工审计；
- 全配置 YAML 解析与 `git diff --check` 通过；
- Gradle 编译证据待工具额度恢复后补齐，账本状态明确标记“待编译/运行验收”。

### D-04A 七服务 Helm 工作负载基线

实现摘要：

- 新增 `distro/helm/cheeseim`，统一声明七个生产服务的 image、replicas、resources、ports、
  management probes、Secret 和发布策略；
- api-server/authcenter/business/postbox/postmaster/postman 使用 Deployment，
  postoffice 使用 StatefulSet + headless Service，以稳定 ordinal Pod 名作为 gateway nodeId；
- 默认 3 副本、PDB minAvailable 2、主机/可用区双 topology spread；
- 容器使用 UID/GID 10001、read-only root filesystem、drop ALL capabilities、RuntimeDefault seccomp，
  禁止 privilege escalation 和 ServiceAccount token；
- `/tmp`、`/app/logs`、`/app/data` 使用 emptyDir；cluster 模式不把它们当可靠业务存储；
- rollout 对 Deployment 使用 maxUnavailable 0/maxSurge 1，preStop 给 endpoint 传播留窗口，
  postoffice 使用更长 termination grace；
- NetworkPolicy 默认区分同 namespace Dubbo、ingress namespace 外部入口和 monitoring management；
- Chart 不创建 Secret 或中间件，七个 Pod 分别引用独立 Secret。

合理性：

- postoffice nodeId 是路由/节点队列契约，随机 Deployment Pod 名在替换后会遗留旧 node queue；
  StatefulSet 稳定 identity 比仅在环境变量拼随机 UUID 更符合现有故障恢复语义；
- PDB 与 topology spread 同时控制自愿中断和故障域分布，单独设置 replicas=3 不能证明 HA；
- read-only/non-root 必须配可写临时挂载，否则 RocksDB/JVM tmp/log 初始化会在运行时失败；
- 不创建“示例 Secret”避免把假密钥或全局共享密钥带入 Git，也防止 auth JWT 扩散到所有模块；
- 在未列完整依赖地址前不启用 egress default-deny，避免安全模板直接切断 Nacos/Kafka/Mongo/Redis/DNS。

内聚性与一致性：

- Chart 消费 Docker/config/management 已建立的唯一端口和环境变量，不复制 Java 内部配置；
- 所有无状态服务共用一个 workload 模板，只有 postoffice 根据模块身份语义选择 StatefulSet；
- 资源和副本数由 values 管理，模板不散落模块特例；Secret 仍按服务隔离；
- 管理 Service annotations、probes 和 NetworkPolicy 都使用同一 19079–19085 端口表。

后续价值：

- 已具备进入预发布的最小 workload seam，可继续接 GitOps、ServiceMonitor、HPA/KEDA、镜像签名策略；
- postoffice stable identity 为节点 drain、队列接管、分批滚动和连接恢复测试提供可重复对象；
- values 中的 resources/replicas 可被容量测试结果直接回填，避免性能结论停留在文档；
- 独立 Secret 为后续 Mongo collection role、Redis ACL、Kafka topic ACL 最小权限拆分提供承载点。

遗留风险：

- 当前环境没有 helm/kubectl，尚未执行 helm lint/template 或 server-side dry-run；
- 默认资源和 3 副本只是 HA 起点，未经过百万 DAU 容量、GC、连接密度和 consumer lag 标定；
- 没有 HPA/KEDA；postoffice 应优先使用在线连接/事件循环/写背压自定义指标，worker 使用 Kafka lag；
- NetworkPolicy 未限制 egress，ingress/monitoring namespace label 与 CNI node-probe 行为必须按集群验证；
- postoffice 没有显式应用 drain API，preStop 只能等待 endpoint 传播后由 SIGTERM 关闭连接；
- common-core 依赖污染使部分不拥有 Mongo 数据的服务仍需 URI，Secret 最小权限尚未达到最终状态；
- 未创建公网 Ingress/LoadBalancer/TLS，外部流量入口应由平台团队按证书、PROXY protocol 和 DDoS 方案提供。

验证证据：

- 按阶段约定未执行集群测试；
- Chart.yaml/values.yaml 可由 YAML 解析器加载；
- 静态不变量检查确认七个服务、七个不同 Secret、19079–19085 唯一管理端口、
  默认 replicas 不低于 PDB minAvailable；
- 模板人工审计覆盖空 NetworkPolicy ports、postoffice identity、Secret 泄漏、read-only 可写目录、
  probe/liveness 故障语义和 rollout；
- `git diff --check` 通过；原生 Helm/Kubernetes 验证待工具可用后补齐。

### D-04B Kafka topic DDL 与启动校验解耦

实现摘要：

- 删除 cluster 模式“必须 auto-create=true”的错误守卫，保留对实际 topic 的强制启动校验；
- application-cluster、ops-cli 和 Helm 统一默认 `KAFKA_TOPICS_AUTO_CREATE_ENABLED=false`；
- `create-im-topics.sh` 修复 TopicNames 对齐空格导致发现 0 topic 的解析缺陷；
- migration 从六个主 topic 自动展开六个 `.DLT`，统一创建十二个 topic；
- 默认值收敛为 12 partitions、replication 3、minISR 2、retention 7 天；
- helper 新增正整数与 minISR≤replication 校验，并将 retention/minISR 写入 create config；
- shell 契约测试从已不存在的 receipt/retry/dlq 更新为 delivery-outcome/group-fanout/DLT。

合理性：

- “应用是否有 DDL 权限”是安全/发布策略，“topic 是否符合消费契约”是运行正确性，两者不能用一个 bool 绑定；
- 每个业务 Pod 拥有 create/alter topic 权限扩大攻击面，也会在并发启动时把 schema migration 混入业务发布；
- DLT 是错误恢复事实，漏建会让 recoverer 在故障时二次失败，必须与主 topic 同批管理；
- 已有 topic 的 partition/replica 变更涉及扩容与 reassignment，脚本不能用 `--if-not-exists` 假装已收敛，
  应由启动校验拒绝并要求显式 migration。

内聚性与一致性：

- TopicNames 仍是名称单一事实源；Java 配置拥有验证，distro 脚本拥有 DDL，业务 Pod 只拥有 produce/consume；
- cluster、ops-cli、Helm 与 runbook 使用同一默认值，不再出现一处 false、一处强制 true；
- 主 topic 与 DLT 使用同一分区/副本/retention 基线，便于统一容量与故障演练。

后续价值：

- 可为七个服务分别配置最小 Kafka ACL，去除 Create/Alter/DescribeConfigs 写权限；
- topic migration 可继续迁入 Terraform/Strimzi KafkaTopic/GitOps，而不改变应用验证逻辑；
- 启动 fail-fast 能阻止错误 minISR/retention 环境接收生产流量。

遗留风险：

- 脚本只幂等创建，不修正已有 topic；partition expansion、replica reassignment、retention 变更仍需独立 runbook；
- 当前所有 DLT 与主 topic 使用同一 7 天 retention，真实事故调查窗口可能要求 DLT 更长，届时契约需支持 per-class 配置；
- 12 分区是起点而非百万 DAU 证明；提高分区数只能前进，必须基于 key 基数、consumer 并行度和 broker 容量决策；
- 未连接真实 Kafka 验证 ACL、并发执行、已有不兼容 topic 和 validator 错误信息。

验证证据：

- 按阶段约定未执行 shell 测试或真实 Kafka 集成测试；
- 四个 shell 文件 `zsh -n` 语法通过；
- dry-run 成功发现 6 个 base topic 并输出 12 条主/DLT 创建命令，参数均为 12/3/minISR2/7d；
- 完成 auto-create false、已有错误配置、遗漏 DLT、minISR>replication 和 TopicNames 对齐格式人工审计；
- `git diff --check` 通过。

### D-05A 分级灾备恢复 Runbook 与 RPO/RTO

实现摘要：

- 新增 `docs/disaster-recovery.md`，按核心事实、单调序列、同步水位、在线状态、短期幂等、
  Kafka 日志、Nacos 与富媒体对象划分真相源和恢复动作；
- 明确 Mongo 分片集群必须使用覆盖 config server 和全部 shard 的一致 PITR，恢复到隔离环境验证后切流；
- 将“Redis 整体旧快照不可恢复”设为硬约束，区域恢复使用空集群、空逻辑库或新 namespace；
- 形成冻结入口、恢复 Mongo、恢复 Kafka、启动空 Redis、恢复 Nacos、分层启动七服务、灰度切流的顺序；
- 给出架构级 RPO/RTO 目标和季度恢复演练证据清单，并将未落地能力明确列为缺口；
- 在文档索引与部署手册登记灾备入口，避免 Runbook 成为孤立过程文档。

合理性：

- `ConversationSeqAllocator` 先在 Mongo 预留号段上界，再在 Redis 保存号段内 `CURR/LAST`；
  较旧 Redis 快照可能在尚未触发下一次 Mongo 扩段前重新发放灾难前已用 seq，因此不能按普通缓存恢复；
- 空 Redis 会让 allocator 从 Mongo 重新预留，代价是允许的 seq 空洞，收益是守住“不重复、不回退”；
- route/session/login lease/node queue 都绑定已经消失的进程或连接，恢复它们会制造幽灵在线和错误定向投递；
- readSeq/maxSeq/deliveredSeq 是 write-behind 热状态，源码默认约一秒开始 drain，但真实 RPO 由 backlog、
  Mongo 延迟和停机窗口共同决定，手册没有把 poll timeout 伪装成 SLA；
- Kafka offset、event log 与 Redis inbox 是联合语义，未审计幂等窗口前禁止 reset earliest 或批量 redrive。

内聚性与一致性：

- 数据恢复职责仍留在各自基础设施：Mongo 管业务事实、Kafka 管事件时间线、Redis 管可重建热状态，
  Runbook 只编排顺序，不引入跨模块“万能备份服务”；
- DLT 处置复用 B-06 的摘要、checksum、operationId 和 Mongo 审计，不建立第二条手工回放路径；
- 与既有 seq allocator、群 epoch、fanout job、control-event outbox 和健康探针的正确性语义一致；
- 部署手册负责启动/环境契约，灾备手册负责故障时间线和验收，文档边界清晰。

后续价值：

- 可直接把证据清单转成季度演练工单、GitOps pipeline gate 和审计归档模板；
- Redis 状态分类为后续拆分 cache/session/idempotency/online-state 集群提供依据，缩小故障域；
- writer oldest-age/depth 指标、termination grace 和跨区域复制可以用实测 RPO/RTO 反推容量；
- 百万 DAU 规模扩展时，恢复登录洪峰、Mongo 回源风暴和 Kafka backlog 有明确的灰度顺序与限速边界。

遗留风险：

- 仓库没有 Mongo 托管 PITR、真实 mongos restore、Kafka 跨区域复制或 Redis 新 namespace 自动化；
- 附件只有 metadata，没有对象存储、版本化、跨区域复制和 KMS 恢复方案，是富媒体生产阻断项；
- Redis key 尚未按可重建缓存、在线状态和短期正确性拆分故障域；
- write-behind 的 depth/oldest age 与停机等待已由 D-05B 补齐；独立 drain duration 仍缺；
- 文档中的 RPO/RTO 是待业务批准和演练校准的架构目标，不是已实现 SLA。

验证证据：

- 按阶段约定未执行单元、集成或真实恢复测试；
- 人工追踪 seq allocator 的 Mongo reserve、Redis `CURR/LAST` 与 cache miss 安装路径；
- 人工核对 read/max/delivery 三类 write-behind 的有界队列、约 1 秒 poll、重试和 shutdown drain；
- 完成旧 Redis snapshot、空 Redis、Kafka offset rewind、writer 未 drain、旧 route/session 复活、
  DLT 与对象本体缺失场景审计；
- Markdown 相对链接和文档索引静态检查通过，`git diff --check` 通过。

### D-05B write-behind RPO 可观测与停机 drain

实现摘要：

- `ImMetrics` 新增低基数 `writerBacklog(writer,state,depth,oldestQueuedAt)`，输出 queued/inflight
  depth 与动态 oldest-age gauge；
- readSeq、userMaxSeq、deliveredSeq entry 保留首次入队时间，fallback/retry 不重置 age；
- 多 worker 的 in-flight depth 原子聚合，最老时间采用保守值，全部 in-flight 完成后归零；
- queued 扫描最多每秒一次，避免 user maxSeq 消息热路径为每条消息遍历全部分桶；
- 三类 writer 停机先中断 poll，再以共享 30 秒预算等待已取出批次，随后 drain 主/回退队列；
- 修复 delivery writer 停机持久化失败仍重新入已停止 fallback queue、随后静默遗留的缺陷，
  改为明确 `shutdown_drop` 指标和错误日志；
- Grafana 增加 backlog depth/oldest age 面板，文档给出告警查询和故障时禁止自动重启的处置原则；
- business/postmaster Helm 默认调整为 120 秒 termination grace、15 秒 preStop，为 writer join/drain 留预算。

合理性：

- depth 不能发现“单个 Mongo 调用卡死但队列暂空”，所以 queued 与 inflight 必须分开观察；
- oldest age 保存 epoch millis，并在 Prometheus scrape 时计算；若依赖调用卡死，没有新业务事件也会持续增长；
- retry 保留原始入队时间，避免一次失败后 age 被重置、长期故障看起来始终年轻；
- 多 worker 完成顺序下最老 in-flight 时间可能短暂保守偏大，但不会低估 RPO 风险，全部完成即精确归零；
- 停机先 join 再 drain，避免 worker 已取走的 batch 与 shutdown 并发退出；共享 deadline 防止四个分桶各等
  30 秒把总等待放大到 120 秒。

内聚性与一致性：

- writer 仍拥有队列与生命周期，`ImMetrics` 只负责固定标签的 meter 注册，不读取业务对象或仓储；
- 三类水位 writer 使用同一 writer/state/result 词汇，Grafana、告警和灾备手册无需理解各自内部队列；
- Helm 只分配进程终止预算，不通过 hook 触碰 Mongo/Redis 或复制业务 drain 逻辑；
- module ARCH、部署、可观测和灾备文档同步更新，没有形成只存在于实现中的隐式运维语义。

后续价值：

- 可以用 P99 oldest age 而非源码 poll interval 校准 read/delivery 水位 RPO；
- rollout controller 可在 writer oldest age 超阈值时暂停发布，避免滚动升级主动放大数据窗口；
- 真实 drain duration 指标、Mongo timeout 和 KEDA/HPA 可在现有固定标签模型上继续补充；
- writer 模型为后续迁移 Kafka compacted state topic 或独立同步服务提供迁移前吞吐与积压证据。

遗留风险：

- 30/120 秒是安全起点，没有最坏 backlog 与 Mongo stepdown 演练证据；
- `@PreDestroy` 在 30 秒后只能报告仍卡住的 in-flight，无法从另一个线程安全接管已经发出的 Mongo 调用；
- queued gauge 最多约一秒采样延迟；in-flight 最老时间在多 worker 部分完成时可能保守偏大；
- 指标仍缺 shutdown 总耗时 histogram 和成功 drain 数，需在不依赖终止后 scrape 的前提下设计日志/外部证据；
- Java 改动因当前 Gradle 执行额度限制尚未编译，不能把静态检查当成构建通过。

验证证据：

- 按阶段约定未执行单元测试；
- Gradle 编译因已知工具额度限制未执行，任务状态明确保留“待编译/演练验收”；
- Grafana JSON 解析、Helm values YAML 解析以及 business/postmaster 120/15 秒不变量检查通过；
- 人工审计正常写、fallback、retry、批内聚合、多 worker 并发、shutdown join timeout、
  shutdown delivery failure 和 gauge 动态增长路径；
- `git diff --check` 通过。

### D-05C Prometheus Operator 采集与告警交付

实现摘要：

- Helm values 新增默认关闭的 `monitoring.serviceMonitor` 与 `monitoring.prometheusRule`；
- 七个普通 Service 增加 `cheeseim.io/metrics=true`，postoffice headless Service 不带该标签；
- ServiceMonitor 只选择当前 release 的 metrics Service，以命名端口 `management` 抓取
  `/actuator/prometheus`，并写入固定 `cheeseim_service` target label；
- PrometheusRule 提供 writer oldest-age warning/critical 与明确 persistence failure 三条规则；
- 规则查询限定 release namespace，critical 阈值必须为正且大于 warning，否则 Helm render 失败；
- additionalLabels 显式留给 Prometheus Operator 的 serviceMonitor/rule selector，不猜测发行版标签；
- README、部署与可观测文档补齐 CRD、NetworkPolicy 和启用前提。

合理性：

- Service annotation 只能覆盖部分 Prometheus 部署，Operator 环境需要 ServiceMonitor 才有声明式发现；
- CRD 不是 Kubernetes 内建资源，默认启用会让没有 Operator 的集群整个安装失败，因此必须 opt-in；
- ServiceMonitor 按专用 metrics label 选取，避免 headless Service 因同 component label 被误当抓取目标；
- 告警直接使用动态 oldest age，能覆盖“in-flight Mongo 卡死但 depth 很小”，而不是只看 counter 或 queue size；
- 告警查询限制 namespace，避免共享 Prometheus 中一个 CheeseIM release 为另一个环境触发告警。

内聚性与一致性：

- 应用只暴露 Micrometer endpoint，ServiceMonitor 负责发现，PrometheusRule 负责策略，三层职责没有混合；
- Helm 只提供可移植默认规则，阈值仍由 values 配置，不把环境 SLA 写死在 Java；
- metrics Service、NetworkPolicy monitoring selector 与 Operator selector 的配置点都在同一 Chart/Runbook 可见；
- 不在业务模块引入 Prometheus Operator Java 依赖。

后续价值：

- 可接入 Alertmanager/PagerDuty、SLO recording rules 与 rollout gate，而不修改 writer；
- 其它 queue lag、node dead、Kafka publish failure 可按相同模板逐步纳入规则组；
- release/namespace 隔离允许预发布与生产共用 Prometheus 而不串告警。

遗留风险：

- 仓库环境没有 Helm/kubectl/Prometheus Operator，尚未验证 CRD schema、selector 命中和真实 scrape；
- terminating Pod 的 shutdown counter 可能在下一次 scrape 前消失，最终停机证据仍需日志/外部事件补充；
- 默认阈值 5s/30s 是起点，必须依据容量与灾备演练调整；
- additionalLabels 和 monitoring namespace selector 取决于实际 kube-prometheus-stack 安装方式。

验证证据：

- 按阶段约定未执行 Helm 单元或集群测试；
- values YAML 与 Grafana JSON 解析通过；
- 人工审计默认 disabled、headless 排除、命名 management port、release/namespace selector、
  critical≤warning fail-fast 和缺 CRD 场景；
- `git diff --check` 通过；`helm lint/template` 与 server-side dry-run 明确保留待验收。

### D-06A history port 与 Mongo Document 解耦

实现摘要：

- 新增 `history.model` 下 MessageBlock、MessageSlot、MessageIdMapping、MessageMutation、
  AttachmentMetadata 五个无框架模型；
- `MessageHistoryRepository` 的全部读写签名从 `*Doc` 改为 port model；
- postbox 历史页、gap repair、附件点查、预览解析只依赖 model，不再 import Document/BSON；
- postmaster mutation 服务不再构造 `MessageMutationDoc`，撤回业务只操作 MessageMutation；
- Mongo adapter 集中完成 block/mapping/mutation/attachment Document → model 映射；
- Mongo 对 Object content 反序列化出的 BSON Binary 在 adapter 边界规范为 `byte[]`；
- MessageSlot 从 `history.document` 移到 `history.model`，Mongo block document 只把它作为无框架嵌套值对象。

合理性：

- Gradle 物理拆模块前必须先反转类型依赖；否则 storage-history 移出去后，postbox/postmaster 仍需依赖
  Mongo module 才能获得返回类型，拆分只是目录变化；
- Document 的 collection/index/_id 布局是 adapter 决策，撤回窗口、历史页和附件鉴权不应感知这些注解；
- BSON Binary 是 Mongo driver 表示，不应由 postbox 做兼容分支；adapter 规范化后 port 的 content 语义一致；
- 保留现有集合、索引、查询、bulk upsert 与 mutation `$setOnInsert`，本任务只改变边界模型，不改变数据格式。

内聚性与一致性：

- `history.model` 不 import Spring Data 或 BSON，Document 仍集中在 `history.document`；
- 所有转换集中于 MongoMessageHistoryRepository，没有在 postbox/postmaster 复制 converter；
- port model 命名不带 Doc/Entity，符合仓库“领域与持久化分离”硬约束；
- history 与此前 group epoch repository 的“业务只见领域对象”方向统一。

后续价值：

- MongoMessageHistoryRepository 与 Document 的物理迁移已由 D-06B 完成，common-core 只保留 port + model；
- 可在不修改 postbox/postmaster 的情况下增加冷存储、归档读取或双写 adapter；
- adapter contract test 可对 Mongo 与未来对象存储/列存实现复用同一历史查询语义；
- 清除 BSON 依赖后，postbox bootJar 的 Mongo driver 是否保留可以由物理模块依赖决定，而非源码类型绑死。

遗留风险：

- common-core 的 business Mongo 与 Kafka adapter 已由 D-06E/D-06C 迁出，剩余主要是 Redis/RocksDB state/cache；
- MessageHistoryRepository 当前同时含 read/write/mutation，后续 storage-history 拆分时应按消费者能力分 port，
  但不要在未出现第二实现前制造过细接口；
- MessageSlot.content 为兼容历史数据仍是 Object，adapter 目前只规范已知 Binary；其他遗留 BSON 类型需样本验证；
- Java 变更因 Gradle 执行额度限制尚未编译，状态明确为“待编译验收”。

验证证据：

- 按阶段约定未执行单元测试；
- 静态边界检查确认 postbox/postmaster/business 主源码无 `history.document` 或 `org.bson` import；
- 五个 history model 无 Spring Data/BSON import，MessageHistoryRepository 签名无 `*Doc`；
- 人工逐项核对 recent/range/slot/mapping/attachment/revoked/upsert/cursor 的 adapter 转换路径；
- `git diff --check` 通过；Gradle compile 保留待工具额度恢复后执行。

### D-06B storage-history 物理模块拆分

实现摘要：

- 新增 `storage-history` library Gradle module，禁用 bootJar、启用普通 jar；
- MongoMessageHistoryRepository 与 message block/mapping/attachment/mutation 四类 Document 从
  common-core 迁入 `com.cheeseocean.im.storage.history.mongo`；
- common-core 只保留 MessageHistoryRepository port、五个纯 model 与 BlockIndexUtil；
- postbox/postmaster 显式依赖 storage-history，并删除自身直接 Mongo starter 声明；
- 新增 `HistoryMongoAutoConfiguration`，仅在存在 MongoTemplate 且没有自定义 port 实现时装配；
- 自动配置通过 Spring Boot AutoConfiguration.imports 注册，不扩大 feature component scan；
- 根构建新增 `verifyHistoryArchitectureBoundary`，纳入 `check`，阻止 model/feature 持久化泄漏和反向依赖；
- 新增模块 ARCH，并同步根 README、AGENTS、server 依赖矩阵与文档索引。

合理性：

- storage adapter 是可替换实现，不应因 common-core 被所有进程依赖而让无关服务扫描历史 Mongo Bean；
- feature 显式依赖 adapter，运行时能力与模块所有权一致；只依赖 common-core 不会“碰巧”获得历史实现；
- AutoConfiguration 比扩大 `scanBasePackages` 更稳定，装配条件可测试，也允许未来注入冷存储/双写实现覆盖默认 Bean；
- feature 删除 Mongo starter 后，其 compile classpath 不再因直接声明允许随手 import Mongo API，边界更可执行；
- 保留原 collection 名、索引名、shard-key query 和数据格式，物理迁移不触发数据 migration。

内聚性与一致性：

- storage-history 内只有 Mongo adapter、Document、转换与装配，没有历史权限、撤回窗口或消息策略；
- common-core → common-api，storage-history → common-core/common-api，feature → port + adapter，依赖无环；
- Document 包名体现 storage/history/mongo 所有权，构建门禁与 AGENTS 规则使用同一边界；
- postbox/postmaster 两个消费者使用同一个 port 实现，不复制查询或 bulk 写代码。

后续价值：

- 可独立做 storage-history adapter contract test、Mongo 版本升级和查询压测；
- storage-business 与 infra-queue 已复用该 module + auto-config 模板完成迁移；下一目标是 infra-state；
- 未来历史冷存储/分层读取只需新增 adapter 或组合实现，不改变 postbox/postmaster；
- 依赖分析、SBOM 与漏洞处置可把 Mongo driver 的用途归因到明确模块。

遗留风险：

- common-core 的 business Mongo 已由 D-06E 迁出；当前仍直接携带 Redis 与 RocksDB；
- 未在运行环境证明 AutoConfiguration 条件顺序、MongoTemplate 可见性与 all-in-one 单 Bean 装配；
- Spring Data 自动索引发现需在本地/预发布验证；cluster 仍以 `distro/mongo` migration 为权威；
- storage-history 目前是共享 library，不是独立网络服务；名称表示物理代码所有权，不意味着增加 RPC 跳数；
- Gradle 编译受工具额度限制尚未执行。

验证证据：

- 按阶段约定未执行单元测试；
- 静态检查确认 common-core history 目录无 Spring Data/BSON，Mongo adapter/Document 只存在 storage-history；
- postbox/postmaster 主源码无 Document/BSON import，build.gradle 不再直接声明 Mongo starter；
- AutoConfiguration imports、条件 Bean、module dependency DAG、bootJar/jar 边界人工审计通过；
- Ruby 等价边界扫描与 `git diff --check` 通过；Gradle boundary/compile/boot 装配待工具恢复后验证。

### D-06C infra-queue 物理模块拆分

实现摘要：

- 新增 `infra-queue` library，禁用 bootJar、启用普通 jar；
- common-core 只保留 `QueueAdapter`、KeyedMessage/Subscription/handler、生产者接口、监听注解与 DLT port；
- Kafka/Chronicle adapter、QueueProperties、listener BeanPostProcessor、byte producer、topic 契约校验和
  Kafka DLT operations 迁入 `com.cheeseocean.im.infra.queue`；
- postoffice/postbox/postmaster/postman/business/ops-cli 显式依赖 infra-queue，但业务源码继续只 import common-core port；
- 三个运行时配置改由 Spring Boot `AutoConfiguration.imports` 装配，ops-cli 删除对实现配置类的手工 import；
- Kafka producer/topic 配置增加 queue type 条件，默认 Chronicle 模式不再创建无用 Kafka producer/admin；
- common-core 删除 Spring Kafka 与 Chronicle 依赖，feature 删除冗余 Spring Kafka 依赖和 stale `@EnableKafka` import；
- 根构建新增 `verifyQueueArchitectureBoundary` 并挂到 `check`/compileJava，阻止实现依赖回流；
- 原实现测试随代码迁入 infra-queue，避免 common-core test classpath 继续依赖驱动。

合理性：

- QueueAdapter 是业务所需端口，Kafka/Chronicle 是部署选择；两者同模块会让鉴权/API 等无关进程携带 broker
  client 和 native/file queue 依赖，也让 component scan 偶然决定是否获得队列能力；
- 显式 runtime module + 自动配置使依赖图和启动行为一致，并保留 all-in-one Chronicle、cluster Kafka 两种模式；
- `cheeseim.queue.type` 同时控制 adapter、producer 与 topic admin，消除了“选 Chronicle 仍初始化 Kafka client”的装配漂移；
- 拆分不改变 QueueAdapter 方法、topic/key、payload、ACK、重试或 DLT 语义，不影响业务调用路径和 wire contract。

内聚性与一致性：

- infra-queue 只包含 broker/file queue 驱动、监听注册和相应运维实现，不包含任何 feature listener 或业务策略；
- feature → common-core port，infra-queue → common-core/common-api，且 infra-queue 不反向依赖 feature，依赖无环；
- 自动配置方式与 storage-history 一致，调用方可用自定义 QueueAdapter/DltOperations Bean 覆盖默认实现；
- QueueListener group、consumer tuning、Kafka transport 配置继续使用既有唯一配置体系，没有另起第二套入口。

后续价值：

- common-core 的 SBOM、启动扫描和漏洞面不再被 Kafka/Chronicle 强制放大，后续拆 `infra-state` 可复用同一模板；
- 可独立进行 Kafka/Chronicle adapter contract test、rebalance/顺序/批量吞吐压测和驱动版本升级；
- 增加 Pulsar/RocketMQ 等后端时只扩 infra-queue，不修改 feature，也能由门禁防止业务绑定特定 broker；
- 自动配置条件成为 standalone/all-in-one/cluster 装配测试的稳定 seam，减少依赖 component scan 的隐式行为。

遗留风险：

- 所有实际队列 feature 当前都把 infra-queue 声明为 compile dependency，虽然门禁禁止 import 实现；后续可在
  应用组装层改为 runtimeOnly，但需先解决独立 bootJar 与 all-in-one 的传递依赖可见性；
- Kafka/Chronicle adapter 仍共处一个 jar，生产镜像仍携带 Chronicle；若镜像体积或 CVE 管理成为瓶颈，后续再拆
  `infra-queue-kafka` / `infra-queue-chronicle`，当前不提前增加模块数量；
- `@QueueListener` 基于 BeanPostProcessor 在 bean 初始化期订阅，优雅停机与 rebalance 生命周期仍需运行测试证明；
- 自动配置顺序、Kafka 模式单一 QueueAdapter、Chronicle 模式无 Kafka bean 和 all-in-one 单 Bean 尚未实际启动验收；
- Gradle 编译受工具额度限制尚未执行。

验证证据：

- 按阶段约定未执行单元测试；
- 静态检查确认 common-core main/test 无 Spring Kafka、Kafka client、Chronicle 或 infra-queue import；
- feature 主源码无 `com.cheeseocean.im.infra.queue` import，直接 KafkaTemplate/@KafkaListener 路径为零；
- Kafka/Chronicle/运行时配置实现只存在 infra-queue，AutoConfiguration imports 与 queue type 条件人工审计通过；
- Ruby 等价边界扫描与 `git diff --check` 通过；Gradle boundary/compile/context 启动待工具恢复后验证。

### D-06D Queue Subscription 生命周期收口

实现摘要：

- `QueueListenerBeanPostProcessor` 保存每次 subscribe/subscribeBatch 返回的 Subscription；
- Spring context destroy 时按注册逆序 unsubscribe，单个关闭失败记录日志但不阻断其他订阅释放；
- 注册与 destroy 使用同一同步边界，关闭竞态下新建的 subscription 会立即释放并拒绝继续注册；
- Chronicle 三种订阅统一走 stopPoller：先设置 running=false 并 `shutdown`，等待当前 handler/批次最多
  30 秒，超时才 `shutdownNow`；重复 unsubscribe 幂等返回；
- 现有 listener processor 测试增加 context close 后 active subscription 为零的断言（按阶段约定未执行）。

合理性：

- Subscription 已是 QueueAdapter 明确返回的生命周期句柄，创建者不保存它等同于丢弃资源所有权；
- 关闭职责放在 listener runtime，而不是散落到每个业务 listener，符合谁注册谁释放；
- 协作式 Chronicle 停机让正常滚动发布先完成当前 handler，仍以 30 秒上限避免无限阻塞 Pod termination；
- Kafka/Chronicle 都通过同一 Subscription 语义关闭，没有为具体后端增加 feature 分支。

内聚性与一致性：

- 改动只涉及 infra-queue 的监听装配和 Chronicle driver，没有改变 port、业务 listener 或部署配置；
- registry 与反射注册仍在同一个 processor 内，订阅集合不暴露为全局可变 Bean；
- 逆序释放与 Spring 资源栈语义一致，unsubscribe 异常隔离避免一个坏 consumer 阻塞全部关闭。

后续价值：

- 为 consumer lag、rebalance、drain duration 和 shutdown result 指标提供集中埋点位置；
- 后续可把 listener runtime 升级为 SmartLifecycle，实现 readiness 在订阅成功后才置 ready；
- adapter contract test 能统一验证 subscribe/unsubscribe 幂等、线程退出和 offset 不提前提交。

遗留风险：

- processor 仍在 bean 初始化期立即启动订阅，而不是 context fully ready 后启动；若启动失败会中断 context，
  这是当前 fail-fast 语义，但 readiness 与启动阶段仍可进一步显式化；
- Chronicle 超时强制中断时当前消息可能重放，handler 必须继续依赖 ingress/delivery 幂等；
- Kafka container stop 的实际等待上限由 Spring Kafka container properties 控制，尚未与 Pod termination budget
  做运行数据校准；
- 未关闭 ChronicleQueue 文件句柄，当前由进程退出回收；若要支持同 JVM context 反复启停，应在明确 destroy
  顺序后为 adapter 增加 close 生命周期；
- Gradle 编译与运行测试受工具额度限制尚未执行。

验证证据：

- 按阶段约定未执行单元测试；
- 人工审计三条 listener 注册路径均进入 track，三条 Chronicle subscription 均进入 stopPoller；
- stopPoller 有幂等 compareAndSet、30 秒有界等待、超时/中断兜底；destroy 对单订阅异常隔离；
- `git diff --check` 与队列边界等价静态扫描通过，编译/context close 测试待工具恢复后执行。

### D-06E storage-business 物理模块拆分

实现摘要：

- 新增 `storage-business` library，迁入用户、好友、群、会话、控制事件、fanout job、DLT audit 的
  19 类 Document、19 个 Mongo/transaction adapter、持久化配置及 7 个 adapter 测试；
- common-core 只保留业务 Repository/store port、领域/状态模型与 transaction executor 抽象，删除 Mongo starter；
- authcenter/business/postmaster/postman/ops-cli 显式依赖 storage-business；postoffice/api-server 不再因
  common-core 传递获得 Mongo driver，postbox 只保留 storage-history；
- Mongo 持久化由 `AutoConfiguration.imports` 装配，仅在 MongoTemplate 存在时注册 adapter/事务管理器；
- 删除 `@EnableCommonMongoPersistence` 手工开关注解和三处应用入口使用，统一到自动配置；
- 删除指向不存在 `mongo.repository` 包的 `@EnableMongoRepositories`，手写 adapter 继续由受限 ComponentScan 注册；
- 根构建新增 `verifyBusinessStorageArchitectureBoundary`，阻止 Mongo API/Document/impl 回流 common-core/feature；
- DLT operations 新增显式 enabled 条件，默认业务 Pod 不再因共享 DLT audit adapter 获得运维端口，
  只有 application-ops.yml 开启。

合理性：

- Repository port 被多个 feature 复用，但 MongoTemplate、Document、索引与事务管理器是存储实现，不能因
  common-core 被所有进程依赖而扩散到网关/API；
- 物理模块化保留单进程内调用，不引入 Dubbo/RPC；集合、`_id`、shard key、bulk 与事务语义全部不变；
- 自动配置与 storage-history/infra-queue 统一，模块依赖决定能力，MongoTemplate 条件决定是否装配；
- DLT 运维 Bean 改为 capability + property 双门禁，比“只要存在 audit store 就开放”更符合最小权限。

内聚性与一致性：

- storage-business 只拥有业务 Mongo 持久化与事务装配，不包含 business service 或 HTTP/Dubbo adapter；
- feature → common-core port + storage-business runtime，storage-business → common-core/common-api，依赖无环；
- history 与 business 两类 Mongo 所有权现均由独立 storage module 表达，Document 禁止穿越 adapter；
- 构建门禁、AGENTS、ARCH、根 README 和文档索引使用同一依赖事实。

后续价值：

- postoffice/api-server 镜像可移除 Mongo driver/CVE/Secret 面，SBOM 能把 Mongo 用途归因到明确 storage 模块；
- storage-business 可独立做 shard-key contract、transaction、bulk writer 和真实分片集群验证；
- 后续按 bounded context 拆 storage-auth/storage-social 时，先拆 port 再拆 adapter 的 seam 已稳定；
- common-core 下一步拆 infra-state 后可成为真正轻量的 port/kernel 模块，而不是基础设施聚合包。

遗留风险：

- 为控制本次 diff，Java package 暂保留历史前缀 `com.cheeseocean.im.common.core.business.mongo`；Gradle module
  与门禁已强制物理所有权，但包名重命名仍应作为独立机械任务完成；
- storage-business 仍聚合 auth、social、conversation、fanout 与 DLT audit 多个数据域，尚不是最终 bounded context；
- 所有使用任一业务 Mongo port 的进程当前携带整个 adapter jar；需先用镜像/SBOM数据再决定是否继续细拆；
- 自动配置条件顺序、all-in-one 单事务管理器、postman 控制事件 adapter 和 ops DLT Bean 尚未运行验收；
- Gradle 编译受工具额度限制尚未执行。

验证证据：

- 按阶段约定未执行单元测试；
- 静态检查确认 common-core main/test 无 Spring Data Mongo 或 business mongo document/impl/config；
- authcenter/business/postmaster/postman 主源码无 Document/impl import，storage-business 无 feature 反向 import；
- AutoConfiguration imports、MongoTemplate 条件、五个显式 module dependency 与 DLT enabled 双门禁人工审计通过；
- Ruby 等价边界扫描和 `git diff --check` 通过；Gradle boundary/compile/context 装配待工具恢复后验证。
