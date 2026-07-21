# CheeseIM 服务端可观测性

> 状态：权威。主链路 Micrometer 指标与 Grafana 模板说明。

## 指标

所有指标使用 `cheeseim.*` 前缀，不包含 userId、conversationId、messageId 等高基数标签。

| 指标 | 主要标签 | 含义 |
| --- | --- | --- |
| `cheeseim_queue_publish_total` / `cheeseim_queue_publish_latency_seconds` | backend, topic, result | Kafka/Chronicle 发布结果与 broker/append 延迟 |
| `cheeseim_ingress_batch_total` / `cheeseim_ingress_batch_latency_seconds` | result | ingress 批处理结果与延迟 |
| `cheeseim_ingress_messages_total` | result | ingress 消息吞吐 |
| `cheeseim_node_queue_depth` | node, state | ready/processing/dead 队列长度 |
| `cheeseim_node_queue_retry_total` | result | requeued/dead/malformed |
| `cheeseim_online_route_lookup_total` | result | hit/miss/stale |
| `cheeseim_delivery_dedup_claim_total` | status | acquired/delivered/in_progress/unavailable |
| `cheeseim_read_advance_total` | result | advanced/unchanged/invalid/not_found |
| `cheeseim_writer_operation_total` | writer, result | fallback/retry/drop |
| `cheeseim_writer_backlog_depth` | writer, state | read/max/delivery write-behind 的 queued/inflight 数量 |
| `cheeseim_writer_backlog_oldest_age_milliseconds` | writer, state | 最老 queued/inflight 更新的实时等待时长；依赖卡死后仍持续增长 |
| `cheeseim_typing_signal_total` | result | dispatched/suppressed/rejected/disabled |
| `cheeseim_offline_push_total` / `cheeseim_offline_push_latency_seconds` | provider, result | 厂商推送尝试、结果和延迟 |
| `cheeseim_dlt_operation_total` / `cheeseim_dlt_operation_latency_seconds` | operation, topic, result | DLT 摘要查询与单条 redrive 的结果、耗时 |
| `cheeseim_api_rate_limit_total` | result | API 限流 allowed/rejected/unavailable；unavailable 表示 Redis fail-open |

## Grafana

导入 [`docs/grafana/cheeseim-server.json`](grafana/cheeseim-server.json)，选择 Prometheus 数据源即可。
模板只提供主链路基线；生产告警阈值需在容量压测后按部署规模校准。

## 健康端点

七个独立服务显式启用 Actuator 和 Prometheus。api-server 使用 19079，其余模块使用
19080–19085（与 Dubbo 20880–20885 按模块尾号对应）。只开放 health/info/prometheus，
健康详情默认隐藏。

- `/actuator/health/liveness`：只看 JVM/Spring 可用状态，中间件故障不能触发全副本重启；
- `/actuator/health/readiness`：包含模块实际依赖的 Mongo/Redis，失败时停止接收新流量；
- `/actuator/prometheus`：供内网 Prometheus 抓取，不得暴露公网。

Kafka 客户端当前没有统一 Actuator health contributor；Kafka 可用性仍以 producer failure、
consumer lag、DLT 和 broker exporter 告警为主，后续补 AdminClient readiness 时必须设置严格超时，
不能在每次 probe 创建新 client。

## write-behind RPO 告警

`read_seq`、`user_max_seq`、`delivery_seq` 同时上报 queued 与 inflight。depth 用于判断积压规模，
oldest age 才是灾备 RPO 的主要代理指标；只看队列长度会漏掉单个 Mongo 调用长时间卡住的情况。
建议先以以下 PromQL 建立预警，再用容量压测校准阈值：

```promql
max by (writer) (cheeseim_writer_backlog_oldest_age_milliseconds) > 5000
```

持续 5 分钟超过 5 秒先告警，不自动重启 Pod。Mongo 故障时重启会丢失进程内 write-behind，
应先摘除写流量、确认 Mongo 状态与 writer oldest age，再决定 drain 或故障切换。
`cheeseim_writer_operation_total{result=~"shutdown_timeout|shutdown_interrupted|shutdown_drop|drop|sync_failed"}`
任一增长都需要人工处置。

business 与 postmaster Helm 默认给 120 秒 termination grace、15 秒 preStop，其中 writer 最多等待
30 秒让已取出的批次完成，再 drain 队列。该值是保护基线，不是容量证明；演练需记录 shutdown 前
depth/age、退出耗时与最终失败计数，再决定是否调整队列容量、Mongo timeout 或 grace period。

Helm Chart 可选生成 ServiceMonitor 与 PrometheusRule，默认关闭。只有集群已安装 Prometheus Operator
CRD 时才启用，并用 `additionalLabels` 对齐目标 Prometheus 的 rule/serviceMonitor selector。
内置规则覆盖 writer oldest-age warning/critical 和明确持久化失败，查询限定到 release namespace；
NetworkPolicy 的 monitoring namespace selector 也必须与实际 Prometheus namespace 一致。
