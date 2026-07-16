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
| `cheeseim_typing_signal_total` | result | dispatched/suppressed/rejected/disabled |
| `cheeseim_offline_push_total` / `cheeseim_offline_push_latency_seconds` | provider, result | 厂商推送尝试、结果和延迟 |

## Grafana

导入 [`docs/grafana/cheeseim-server.json`](grafana/cheeseim-server.json)，选择 Prometheus 数据源即可。
模板只提供主链路基线；生产告警阈值需在容量压测后按部署规模校准。
