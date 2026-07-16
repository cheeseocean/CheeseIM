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
| `NACOS_SERVER_ADDR`、`NACOS_NAMESPACE` | 服务注册、配置与元数据中心 |
| `REDIS_SENTINEL_*` 或 `REDIS_CLUSTER_*` | Redis 高可用拓扑 |
| `REDIS_PASSWORD` | Redis 认证（如启用） |
| `CHEESEIM_AUTH_JWT_SECRET` | authcenter JWT 签名密钥 |
| `CHEESEIM_POSTOFFICE_NODE_ID` | postoffice 稳定节点 ID；cluster 下必填，重启不得变化 |

集群默认 `CHEESEIM_QUEUE_TYPE=kafka`，并把会话 seq 切到 cluster 部署模式。仅可选择一个 Redis profile：`redis-sentinel` 或 `redis-cluster`。

## 4. 中间件 compose 边界

`distro/docker/docker-compose.middleware.yml` 只启动 Nacos、Kafka、Zookeeper 与 Kafka Console，用于本地拆分模块联调；它**不**包含 MongoDB 与 Redis。Mongo 和 Redis 必须由开发者或目标部署环境单独提供，不能把该 compose 误当成完整生产编排。

该 compose 中 Kafka 为容器网络与宿主机分别暴露监听器：Console 使用 `kafka:9092`，宿主机启动的模块应使用 `localhost:9094`。生产环境必须以实际 broker 地址设置 `KAFKA_BOOTSTRAP_SERVERS`，不要沿用本地端口。

## 5. 端口矩阵

| 模块 | HTTP/长连接端口 | Dubbo 端口 |
| --- | --- | --- |
| all-in-one | HTTP 18079 / WS 5147 / TCP 5148 | injvm |
| authcenter | — | 20884 |
| business | HTTP 18085 | 20885 |
| postoffice | WS 5147 / TCP 5148 | 20880 |
| postbox | actuator（由 Spring 默认或部署环境配置） | 20882 |
| postmaster | — | 20881 |
| postman | actuator（由 Spring 默认或部署环境配置） | 20883 |

## 6. 容量与故障验证

集群发布前按 [`server/perf/README.md`](../server/perf/README.md) 执行 k6 长连接 smoke 和目标容量阶梯测试。脚本覆盖 auth、heartbeat、chat-send、read 与 delivery ACK，并用 broker ACK、唯一接收数和 delivery ACK 数核对已接受消息的零丢失/零重复。

多节点 chaos 只允许在隔离的预发布环境执行。至少覆盖 postoffice 重启、Redis 短断、Kafka broker 不可用和 Mongo primary stepdown；保存 k6 summary、Prometheus 快照、consumer lag 与副本集状态。未附这些证据时，不得把估算连接数描述为已验证容量。
