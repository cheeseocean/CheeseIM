# CheeseIM 灾备与恢复手册

> 状态：**权威（仓库侧基线，待真实灾备演练验收）**。  
> 本文定义 MongoDB、Redis、Kafka、Nacos 与附件对象存储的恢复边界、顺序、RPO/RTO 目标和验收证据。
> 未在隔离环境完成恢复演练并保存证据前，不得宣称生产灾备已就绪。

## 1. 恢复原则

1. **MongoDB 是业务持久状态的主真相源**。消息历史、会话 seq 预留上界、用户/群关系、同步水位、
   控制事件和 DLT redrive 审计都以 Mongo 为准。
2. **Redis 是混合状态设施，不是可整体回灌的缓存快照**。其中既有可丢弃缓存，也有在线租约、
   短期幂等、write-behind 热水位和已预留 seq 号段内的消费位置；不同时间点的 Mongo/Redis 快照
   不能安全拼接。
3. **Kafka retention 是可重放窗口，不是业务备份**。恢复必须保留 topic 配置、consumer group offset
   与事件时间线；不得在未审计幂等窗口时把 offset 重置到 earliest。
4. **恢复到隔离环境，验证后再切流**。不要把未验证的备份直接覆盖当前生产集群。
5. **先停写或 drain，再做最终切换**。业务写入、Kafka 消费和备份时间线无法对齐时，必须明确接受
   对应 RPO，而不是把“命令成功”解释为一致备份。

## 2. 状态分级与恢复策略

| 状态类别 | 代表数据 | 真相源 | 区域灾难恢复策略 |
| --- | --- | --- | --- |
| 核心业务事实 | 用户、安全状态、好友、群/成员 epoch、会话、消息块、消息映射、mutation、附件元数据 | MongoDB | 按 PITR 恢复完整分片集群，再校验索引、分片与引用完整性 |
| 全局单调序列 | `conversation_sequence` | MongoDB | 只从 Mongo 恢复；Redis seq key 必须为空并回源重新预留 |
| 用户同步水位 | `user_conversation_sync_point`、`device_conversation_delivery` | MongoDB + Redis write-behind | Mongo 恢复到备份点；Redis 留空并按 Mongo/lazy read 重建，接受经批准的 write-behind RPO |
| 控制事件 | event、cursor、delivery preference、version log | MongoDB | 与业务 Mongo 同一时间线恢复；TTL 已过数据不承诺恢复 |
| 群扩散任务 | `group_fanout_job`、成员 epoch | MongoDB + Kafka | Mongo 任务状态和 Kafka offset 必须联合审计；稳定 job/message ID 允许幂等续跑 |
| DLT 运维审计 | `dlt_redrive_audit` | MongoDB | 必须恢复；DLT record 本体仍在 Kafka，不以审计集合代替 |
| 在线连接事实 | route、session 反向索引、login lease、node queue/deadline | Redis + 进程连接 | **全部丢弃重建**；客户端重连，禁止复活旧 route/lease/queue |
| 短期幂等 | ingress/message/delivery/API/consumer inbox | Redis/RocksDB | 默认不回灌旧快照；恢复前评估 Kafka 回放窗口和重复副作用 |
| 登录会话/票据 | session、WS ticket、refresh family、assertion replay | Redis/RocksDB，用户封禁版本在 Mongo | Redis 灾难后现有会话失效，要求重新认证；禁止复活旧 ticket/lease |
| 业务缓存 | user/friend/conversation cache、push quota/state、API rate limit | Mongo 或可重算状态 | 留空，按请求回源/自然重建 |
| 事件日志 | 六个主 topic、六个 DLT、consumer offset | Kafka | 同步复制或恢复 broker volume；保留 partition、key、offset 和 timestamp |
| 配置与发现 | Nacos namespace/config/service metadata | Git/部署配置/Secret 管理器 + Nacos | 导出 namespace/config；服务实例由 Pod 重新注册，Secret 不从普通配置备份恢复 |
| 富媒体对象 | 图片、语音、视频、文件本体 | 外部对象存储 | 必须独立版本化/跨区域复制；`attachment_metadata` 不能代替对象本体 |

当前仓库只有附件元数据，没有对象存储部署、复制和校验实现。生产上线前必须明确对象存储供应商、
bucket versioning、跨区域复制、生命周期、KMS 与恢复演练，否则富媒体灾备不闭环。

## 3. Redis 恢复硬约束

### 3.1 禁止整体恢复旧快照

`ConversationSeqAllocator` 先在 Mongo 原子预留一段上界，再在 Redis
`im:seq:conv:{conversationId}` 保存该号段内的 `CURR/LAST`。如果恢复较旧 Redis 快照，
Mongo 仍可能已经预留到同一 `LAST`，但旧 `CURR` 会让系统再次发放灾难前已消费的 seq。
这会导致重复序列，而不是可接受的序列空洞。

因此：

- 区域恢复或跨时间点 PITR 必须连接**空 Redis 集群、空逻辑库或新 key namespace**；
- 不得把 RDB/AOF 与不同时间点 Mongo、Kafka 状态组合；
- 空缓存首次分配会从 Mongo `conversation_sequence` 预留新段，可能产生空洞，但不会重复或回退；
- 只有能证明 Redis、Mongo、Kafka 和所有 writer 在同一 crash-consistent 时间点的底层卷快照，
  才可提出原样恢复评审；普通 RDB/AOF 备份不满足该证明。

### 3.2 空 Redis 的预期后果

- 所有客户端重新认证和重连；旧 route、connection、login lease、WS ticket 不得恢复；
- API 限流、push quota/cache 从空状态重新累计，切流初期需由边缘限流保护；
- 短期 inbox/dedup 消失，Kafka 历史事件重放可能再次触发业务处理；
- readSeq、maxSeq、deliveredSeq 从 Mongo 水位开始恢复，灾难前尚未 drain 的热状态可能丢失；
- node delivery queue/deadline 不恢复，客户端以历史 gap repair、控制事件补拉和重新投递收敛。

Redis Sentinel/Cluster、AOF 和副本仍应启用，它们解决单节点高可用和短故障恢复；它们不改变上述
跨时间点恢复规则。

## 4. Kafka 恢复硬约束

- topic 契约固定为六个主 topic 和对应六个 `.DLT`；恢复后必须验证 partition、replication factor、
  `min.insync.replicas` 与 retention。
- 同区域 broker 故障依靠 replication=3、minISR=2 恢复；区域灾难若要求 RPO 小于备份周期，
  必须部署跨区域复制并监控复制 lag。仓库当前未提供 MirrorMaker 2/集群链接配置。
- consumer offset 与消息必须来自同一 Kafka 时间线。恢复 offset 后先保持消费者缩容为 0，
  对比各 group lag，再逐组放开。
- 不得无审计执行 `--to-earliest`、删除 consumer group 或批量 redrive DLT。Redis inbox 已丢失时，
  回放窗口内的外部副作用尤其需要人工评估。
- 单条毒消息使用 [`dlt-runbook.md`](dlt-runbook.md) 的摘要查询和受控 redrive；不直接删除原 DLT，
  redrive 审计在 Mongo 保留。

## 5. MongoDB 备份与恢复

### 5.1 备份基线

- 生产使用支持 PITR 的 MongoDB 分片集群备份能力，覆盖所有 shard replica set 和 config server；
- 记录 backup/snapshot ID、集群拓扑、FCV、开始/完成时间、PITR 可用时间范围和加密密钥版本；
- 定期导出 `distro/mongo/enable-im-sharding.js` 对应的 sharding/index 事实并比较漂移；
- 大规模持续写入的分片集群不能仅依赖逐集合 `mongodump` 作为一致 PITR 方案；
- 备份账号只读、备份加密、跨账号/跨区域保存，恢复账号与业务账号隔离。

### 5.2 恢复与验证

1. 恢复 config server 与全部 shard 到隔离集群的同一 PITR 时间点。
2. 连接 mongos，核对数据库 primary shard、集合 shard key、balancer 状态、chunk 分布和必要索引。
3. 运行只读一致性检查：
   - `conversation_sequence.maxSeq` 不小于已恢复消息历史的最大 seq；
   - `message_id_mapping` 能定位抽样消息块和 slot；
   - attachment metadata 引用的消息存在，对象本体另行校验；
   - group 当前成员与 `group_member_epoch` 版本区间无非法重叠；
   - fanout job、control event cursor、redrive audit 状态可解释；
   - 用户安全状态、会话与同步水位抽样可读取。
4. 只在校验通过后把应用 Secret/DNS 指向新集群；失败时保留现场，不覆盖原集群。

## 6. 标准区域恢复顺序

1. 宣布事故，冻结发布，记录事故起点、最后健康时间、Kafka offset 与复制 lag。
2. 关闭入口或把 api-server/postoffice 副本缩为 0；停止生产者后再停止消费者，保存 drain 结果。
3. 准备隔离网络、DNS、Secret/Nacos namespace 和全新的 Redis namespace。
4. 按选定 PITR 时间恢复 Mongo，并完成第 5.2 节校验。
5. 恢复/切换 Kafka，执行 topic 契约检查，暂不启动消费者。
6. 启动空 Redis HA 集群，确认不存在旧 route、lease、session、seq 与 node queue。
7. 恢复 Nacos 配置；Secret 从专用 Secret 管理器注入，服务实例由进程重新注册。
8. 按依赖顺序启动：
   `business/authcenter` → `postmaster/postbox/postman` → `api-server` → `postoffice`。
   每步先检查 readiness、错误率、Mongo/Redis 连接和 Kafka lag，再进入下一步。
9. 先放开内部消费者并限速消化 backlog，再灰度 api-server，最后灰度 postoffice 和客户端重连。
10. 验证 seq 单调、历史/gap repair、登录失效、群扩散、已读/送达、控制事件和 DLT；逐步恢复流量。
11. 记录实际 RPO/RTO、丢失/重复范围、人工操作与最终证据，事故后更新本手册。

不要并行无序启动七个模块。恢复早期的空 Redis 会放大 Mongo 回源和登录洪峰，应在边缘限流、
连接准入和消费者并发受控的前提下逐步扩容。

## 7. RPO/RTO 目标

以下是架构目标，不是已验证 SLA；须由业务负责人批准并通过季度演练校准。

| 能力 | 目标 RPO | 目标 RTO | 说明 |
| --- | --- | --- | --- |
| Mongo 核心事实 | ≤ 5 分钟 | ≤ 60 分钟 | 依赖 PITR、跨区域备份和分片恢复自动化 |
| Kafka 同区域 broker 故障 | 0 | ≤ 10 分钟 | replication=3/minISR=2，仍需真实 broker chaos 证据 |
| Kafka 区域灾难 | ≤ 跨区域复制 lag | ≤ 60 分钟 | 当前跨区域复制尚未落地，不能宣称达标 |
| Redis 在线 route/session/lease | 不恢复 | ≤ 15 分钟开始重连 | 以清空并重建保证不复活旧连接 |
| Redis seq 缓存 | 0 个重复 seq | ≤ 15 分钟 | 允许预留号段空洞；从 Mongo 重新预留 |
| read/max/delivered 水位 | ≤ writer 未 drain 窗口 | ≤ 30 分钟 | writer 默认约 1 秒开始 drain，但积压/Mongo 延迟决定真实窗口 |
| DLT 查询与单条 redrive | 取决于 Kafka DLT retention | ≤ 30 分钟 | 默认 7 天 retention，事故窗口可能需要更长 |
| 富媒体对象 | 待对象存储方案定义 | 待定义 | 当前为生产阻断项 |

write-behind RPO 必须从指标测量：队列深度、最老待写 age、fallback、sync backpressure、最终失败，
不能用源码中的 1 秒 poll timeout 代替。优雅停机 drain 也需要足够 termination grace period，
否则进程被强杀时仍会丢失队列内热水位。

## 8. 季度恢复演练与证据

每季度至少一次在隔离环境执行完整恢复；重大 schema、shard key、seq allocator、Kafka 语义或认证状态
变更后增加专项演练。证据包至少包含：

- 演练编号、负责人、时间线、批准的目标 PITR、backup/snapshot ID 和校验和；
- Mongo 拓扑、FCV、shard/chunk/index 导出，集合 count/抽样一致性报告；
- 恢复消息最大 seq 与 `conversation_sequence` 对比，连续发送后无重复/回退的证明；
- 群 membership epoch、fanout job、控制事件 cursor、mutation、附件元数据抽样；
- 空 Redis 证明：无旧 route/lease/session/seq/node queue，旧 token/ticket 不可复活；
- Kafka 12 topic 契约、consumer group offset、恢复前后 lag、DLT 与 redrive audit 抽样；
- 七服务 readiness、Prometheus 快照、错误率、Mongo/Redis 延迟和 Kafka consumer lag；
- k6 小流量 smoke：登录、发消息、接收、已读、送达 ACK、历史/gap repair、群聊；
- 实际 RPO/RTO、丢失/重复/空洞范围、未达标项、负责人和整改截止时间。

验收结论只能是“本次备份在指定场景下恢复成功”，不能从一次演练外推所有区域、规模和故障组合。

## 9. 当前缺口

- 未接入托管 Mongo PITR/分片一致快照，也没有真实 mongos restore 证据；
- 未配置 Kafka 跨区域复制、复制 lag 告警和 consumer offset 灾备自动化；
- Redis key 尚未按“可重建/短期正确性/在线租约”形成独立 cluster 或 namespace；
- writer 已有 queued/inflight depth、动态 oldest age 与 shutdown 失败结果；仍缺独立 drain duration 指标；
- business/postmaster 已给 120 秒 termination grace 和 30 秒 writer join 上限，但尚未按最坏 Mongo
  延迟与 backlog 演练校准；
- 富媒体对象存储与灾备实现缺失；
- 本文尚未经过真实季度演练验证。
