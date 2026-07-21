# CheeseIM 部署运行模式

> 状态：**权威**。本文件定义服务端的启动模式、配置叠加方式和外部依赖；与代码冲突时以 `server/config/src/main/resources` 为准。

## 1. 运行模式

运行模式只使用一个配置键：`cheeseim.runtime.mode`。`common.yml` 的默认值为 `standalone`，启动入口或 profile 可以覆盖它。

| 模式 | 启动入口 | 队列与 Dubbo | 必需中间件 | 适用场景 |
| --- | --- | --- | --- | --- |
| `all-in-one` | `./gradlew :bootstrap-all:bootRun` | Chronicle + injvm | Redis；Mongo 用于消息历史与 authcenter | 本地联调 |
| `standalone` | 各模块的 `main` 方法 | 本地默认值 + Nacos/Dubbo 远程 | Mongo、Redis、Nacos；Kafka 按队列配置 | 拆分模块本地联调 |
| `cluster` | 独立模块加 `cluster` profile | Kafka + Dubbo 远程 | Mongo 副本集、Redis Sentinel/Cluster、Kafka、Nacos | 集群部署 |

`application-all.yml` 显式设置 `all-in-one`；`application-cluster.yml` 显式设置 `cluster`。不要新增 `app.runtime.*`、`app.modules.*` 或按配置导入顺序推断运行模式。

## 2. 本地 all-in-one

先提供 JWT 签名密钥（至少 32 个字符），再启动：

```bash
export CHEESEIM_AUTH_JWT_SECRET='replace-with-a-secret-of-at-least-32-characters'
cd server
./gradlew :bootstrap-all:bootRun
```

默认暴露 HTTP `18079`、WebSocket `5147`（`/ws`）和 TCP `5148`。Dubbo 使用 injvm，不开放网络端口。离线推送维护任务由 `cheeseim.push.scheduled-tasks.enabled` 显式控制，all-in-one 默认启用。

## 3. 独立模块与 cluster overlay

每个独立模块的启动类默认加载同名 `application-{module}.yml`；该文件导入 `common.yml` 与 `module-{module}.yml`。集群部署在此基础上叠加 `cluster` profile，例如：

```bash
cd server
./gradlew :postman:bootRun --args='--spring.profiles.active=cluster,redis-cluster'
```

cluster profile 不提供任何中间件的 `localhost` 默认值，部署时必须配置：

| 配置 | 用途 |
| --- | --- |
| `MONGODB_URI` | Mongo 副本集连接串 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker 列表 |
| `KAFKA_TRANSACTION_ID_PREFIX` | 可选；Kafka 事务 producer 前缀，显式配置时必须包含 pod/节点唯一部分 |
| `KAFKA_TOPICS_AUTO_CREATE_ENABLED` | 生产保持 false；topic 由 migration 预创建，应用启动仍强校验契约 |
| `NACOS_SERVER_ADDR`、`NACOS_NAMESPACE` | 服务注册、配置与元数据中心 |
| `REDIS_SENTINEL_*` 或 `REDIS_CLUSTER_*` | Redis 高可用拓扑 |
| `REDIS_PASSWORD` | Redis 认证（如启用） |
| `CHEESEIM_AUTH_JWT_SECRET` | authcenter JWT 签名密钥 |
| `CHEESEIM_POSTOFFICE_NODE_ID` | postoffice 稳定节点 ID；cluster 下必填，重启不得变化 |

集群默认 `CHEESEIM_QUEUE_TYPE=kafka`，并把会话 seq 切到 cluster 部署模式。仅可选择一个 Redis profile：`redis-sentinel` 或 `redis-cluster`。

每个独立启动配置都显式 import `application-cluster.yml`，该文件自身只在 `cluster` profile 激活。
这是必要约束：启动类使用自定义 `spring.config.name=application-{module}` 后，Spring Boot 不会仅凭
profile 名自动加载另一个 base name 的 `application-cluster.yml`。

应用 Pod 默认没有 Kafka DDL 权限。首次部署先执行：

```bash
./distro/create-im-topics.sh \
  --bootstrap-server '<broker-list>' \
  --partitions 12 \
  --replication-factor 3 \
  --min-in-sync-replicas 2 \
  --retention-ms 604800000
```

脚本从 `TopicNames` 读取六个主 topic，并同时创建十二个主/DLT topic。已有 topic 不会被静默改分区、
副本或配置；若与契约不兼容，应用启动校验会失败，运维必须使用显式 partition expansion、
replica reassignment 或 `kafka-configs` migration，不能由业务 Pod 自动修改。

## 4. 中间件 compose 边界

`distro/docker/docker-compose.middleware.yml` 只启动 Nacos、Kafka、Zookeeper 与 Kafka Console，用于本地拆分模块联调；它**不**包含 MongoDB 与 Redis。Mongo 和 Redis 必须由开发者或目标部署环境单独提供，不能把该 compose 误当成完整生产编排。

该 compose 中 Kafka 为容器网络与宿主机分别暴露监听器：Console 使用 `kafka:9092`，宿主机启动的模块应使用 `localhost:9094`。生产环境必须以实际 broker 地址设置 `KAFKA_BOOTSTRAP_SERVERS`，不要沿用本地端口。

## 5. 服务镜像

`server/Dockerfile` 是七个生产服务的统一镜像入口，只允许
`api-server/authcenter/business/postoffice/postbox/postmaster/postman`，明确拒绝把 all-in-one 或 ops-cli
误发布为常驻业务服务。

以仓库 `server/` 为 build context：

```bash
docker build \
  --build-arg MODULE=postmaster \
  --build-arg VERSION=1.0.0 \
  -t cheeseim/postmaster:1.0.0 \
  server
```

镜像默认启用 `cluster` profile，以 UID/GID 10001 非 root 运行，并让 JVM 按容器内存上限计算 heap。
Spring Boot fat jar 在构建阶段拆成 dependency/loader/application 层，业务代码变化不会让基础依赖层全部失效。
部署必须显式设置前述中间件环境变量、模块端口、CPU/内存 request/limit 和唯一
`KAFKA_TRANSACTION_ID_PREFIX`。`/app/logs`、`/app/data` 可写，但 cluster 模式不得把本地目录当作
可靠消息或业务数据存储。

基础镜像 tag 当前尚未使用 digest 固定；进入正式供应链前必须由镜像同步流程锁定 digest、生成 SBOM
并完成签名/漏洞扫描。Dockerfile 不声明通用 `HEALTHCHECK`，因为各模块管理端口和依赖不同，
探针由后续 Kubernetes 工作负载显式配置。

## 6. 端口矩阵

| 模块 | HTTP/长连接端口 | Dubbo 端口 |
| --- | --- | --- |
| all-in-one | HTTP 18079 / WS 5147 / TCP 5148 | injvm |
| api-server | HTTP 18079 / management 19079 | consumer only |
| authcenter | management 19084 | 20884 |
| business | HTTP 18085 / management 19085 | 20885 |
| postoffice | WS 5147 / TCP 5148 / management 19080 | 20880 |
| postbox | management 19082 | 20882 |
| postmaster | management 19081 | 20881 |
| postman | management 19083 | 20883 |

独立模块的管理端口只暴露 `/actuator/health`、`/actuator/health/liveness`、
`/actuator/health/readiness`、`/actuator/info` 和 `/actuator/prometheus`。liveness 只表示进程自身，
不得因 Mongo/Redis 短暂故障触发重启风暴；readiness 包含模块所需的 Mongo/Redis。
管理端口不得通过公网 Service/Ingress 暴露，必须由 NetworkPolicy/安全组只允许 kubelet、
Prometheus 和运维网访问。Kafka readiness 仍需专用 AdminClient health indicator 后才能纳入探针。

api-server 默认按 socket peer 做 120 请求/60 秒的应用层限流。只有 Ingress/LB 会覆盖而非透传客户端
`X-Forwarded-For` 时，才可设置 `CHEESEIM_API_RATE_LIMIT_TRUSTED_PROXY_HOPS`；否则保持 0，
避免客户端伪造来源绕过限流。大规模生产仍需在边缘层配置连接数、带宽、请求体和 DDoS 防护，
应用层 Redis 限流不能替代边缘保护。

## 7. Kubernetes / Helm

[`distro/helm/cheeseim`](../distro/helm/cheeseim/README.md) 提供七服务 Chart。它只部署应用，
不在同一 Chart 搭建 Mongo、Redis、Kafka 或 Nacos，也不创建任何 Secret。

关键约束：

- api-server/authcenter/business/postbox/postmaster/postman 使用 Deployment；
- postoffice 使用 StatefulSet，以稳定 ordinal Pod 名作为 gateway nodeId，避免替换后遗留无人消费的旧 node queue；
- 默认 3 副本、PDB `minAvailable=2`、双 topology spread；这只是 HA 基线，不是百万 DAU 容量证明；
- root filesystem 只读、UID 10001、禁提权、drop all capabilities、不挂载 ServiceAccount token；
- image 必须在生产 values 中改为不可变 digest；
- 每个服务引用独立 Secret，authcenter JWT/assertion secret 禁止进入其它 Pod；
- NetworkPolicy 默认只允许同 namespace Dubbo、ingress namespace 的外部入口和 monitoring namespace 的管理端口；
- egress 暂不限制，待列全 DNS/Nacos/Kafka/Mongo/Redis/厂商推送地址后再收紧。

正式安装前必须执行 `helm lint`、`helm template` 和 `kubectl apply --dry-run=server`。当前仓库环境
没有 Helm/kubectl，因此 Chart 只完成 values/YAML/模板人工审计，不能据此宣称集群验收通过。

business/postmaster 包含 read/max/delivery write-behind，默认 termination grace 为 120 秒、
preStop 为 15 秒；writer 在收到停机后最多等待当前批次 30 秒，再 drain 队列。发布前观察
`cheeseim_writer_backlog_*`，积压或 oldest age 超阈值时不得继续滚动，实际 grace 由退出演练校准。

已安装 Prometheus Operator CRD 的集群可显式启用 `monitoring.serviceMonitor.enabled` 与
`monitoring.prometheusRule.enabled`；默认关闭以兼容无 CRD 集群。启用时需用 additionalLabels
匹配 Prometheus selector，并保证 monitoring NetworkPolicy namespace selector 与实际部署一致。

## 8. 容量与故障验证

集群发布前按 [`server/perf/README.md`](../server/perf/README.md) 执行 k6 长连接 smoke 和目标容量阶梯测试。脚本覆盖 auth、heartbeat、chat-send、read 与 delivery ACK，并用 broker ACK、唯一接收数和 delivery ACK 数核对已接受消息的零丢失/零重复。

多节点 chaos 只允许在隔离的预发布环境执行。至少覆盖 postoffice 重启、Redis 短断、Kafka broker 不可用和 Mongo primary stepdown；保存 k6 summary、Prometheus 快照、consumer lag 与副本集状态。未附这些证据时，不得把估算连接数描述为已验证容量。

## 9. 灾备恢复

区域恢复必须遵循 [`disaster-recovery.md`](disaster-recovery.md)。Mongo 是业务持久状态主真相源；
Redis 恢复使用空集群或新 namespace，禁止整体回灌与 Mongo/Kafka 不同时间点的 RDB/AOF。
特别是会话 seq 的 Mongo 预留上界与 Redis 号段内消费位置不能跨时间点拼接，否则可能重复发号。

Kafka retention 只提供重放窗口，不等于备份；生产需保留 topic/consumer offset 时间线并按业务 RPO
部署跨区域复制。每季度在隔离环境完成 PITR、空 Redis 重建、Kafka offset、七服务灰度恢复和 k6 smoke，
保存实际 RPO/RTO 证据。当前仓库仅提供手册，尚无真实恢复演练证据。
