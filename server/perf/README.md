# CheeseIM 服务端容量与 Chaos 验证

> 工具只提供可重复的验证入口，不代表百万 DAU 已验证。正式结论必须附环境规格、k6 summary、Prometheus 快照和故障时间线。

## 1. 长连接容量场景

选择 k6 是因为仓库无需增加 Java/Node 构建依赖。`k6/ws-im.js` 直接编码当前 typed Protobuf WS envelope，覆盖：一次性 ticket 认证、心跳、单聊发送、broker ACK、接收消息、设备 delivery 高水位 ACK、read 高水位 ACK。

准备至少与最大连接数相同的 ticket JSON，格式见 `k6/tickets.example.json`。ticket 必须在压测开始前通过 `/api/auth/login` 和 `/api/im/ws-ticket` 签发，每个 VU 独占且只能消费一次；用户按互为 peer 的成对方式准备。

```bash
RUN_ID=smoke-001 CONNECTIONS=20 RAMP=10s HOLD=30s RAMP_DOWN=10s \
MESSAGE_RATE=10 WS_URL=ws://127.0.0.1:5147/ws \
TICKETS_FILE=server/perf/k6/tickets.json \
k6 run --summary-export server/perf/artifacts/smoke-001/summary.json server/perf/k6/ws-im.js

node server/perf/k6/verify-summary.mjs server/perf/artifacts/smoke-001/summary.json
```

参数：`CONNECTIONS` 是峰值 VU，`RAMP/HOLD/RAMP_DOWN` 是连接曲线，`MESSAGE_RATE` 是所有 VU 的目标总发送速率，`HEARTBEAT_MS` 默认 15 秒，`SEND_READ_ACK=false` 可关闭已读。单机 k6 在高连接数前必须先测 load-generator 自身 CPU/端口上限；百万连接应分布式拆分，不得用单机结果外推。

正确性阈值：协议错误与重复接收必须为 0；`brokerAccepted/sent`、`receivedUnique/brokerAccepted`、`deliveryAck/receivedUnique` 均需达到 99.9%。故障恢复场景需在停止注入后继续运行至少 2 个最大重试窗口，再核对 Mongo `message_id_mapping.clientMsgId` 中 `k6-{RUN_ID}-` 前缀的唯一数与 k6 的 `brokerAccepted` 一致。未被 broker 接受的发送不计入“服务端丢失”。

容量通过阈值：连接成功率 ≥99.9%，broker ACK p99 ≤200ms，节点 ready/processing 队列在恢复窗口内回到基线，dead 队列增量为 0，writer `drop|sync_failed|retry_exhausted_backpressure` 为 0。当前 k6 脚本只直接计算 broker ACK 延迟；端到端 p99 需从发送时间戳与接收时间戳或服务端 trace 另行统计，不得用 ACK p99 替代。阈值需按真实跨地域 SLA 调整，但不得放宽零重复、零已接受消息丢失。

## 2. 多节点 Chaos

必须在隔离的预发布 namespace 执行。`chaos/run-chaos.sh` 默认只打印命令；只有 `CONFIRM_CHAOS=yes` 才执行。脚本不猜测 Kubernetes/虚机拓扑，通过环境变量注入目标环境已审批的 start/heal 命令，并用 trap 保证恢复。

| 场景 | 注入变量 | 核对重点 |
| --- | --- | --- |
| postoffice 重启 | `POSTOFFICE_RESTART_CMD` / `POSTOFFICE_HEAL_CMD` | 重连、stale route、processing reclaim、重复投递 |
| Redis 短断 | `REDIS_OUTAGE_CMD` / `REDIS_HEAL_CMD` | 路由/seq/read/delivery fail-fast，恢复后无水位回退 |
| Kafka broker 不可用 | `KAFKA_OUTAGE_CMD` / `KAFKA_HEAL_CMD` | 未 broker ACK 的请求明确失败，已 ACK 消息最终落历史并投递 |
| Mongo 主从切换 | `MONGO_STEPDOWN_CMD` / `MONGO_HEAL_CMD` | writer retry/backpressure/failure、history retry、cursor/read/delivery 单调 |

示例只用于说明，资源名必须替换为实际拓扑：

```bash
CONFIRM_CHAOS=yes CHAOS_DURATION_SECONDS=15 \
POSTOFFICE_RESTART_CMD='kubectl -n cheeseim rollout restart deployment/postoffice' \
POSTOFFICE_HEAL_CMD='kubectl -n cheeseim rollout status deployment/postoffice --timeout=180s' \
server/perf/chaos/run-chaos.sh postoffice-restart
```

每次执行保存：k6 summary、Prometheus 故障前/中/后快照、各模块日志、Kafka consumer lag、Mongo replica 状态和脚本时间戳。Prometheus 至少核对：`cheeseim_queue_publish_total{result="failure"}`、`cheeseim_node_queue_depth{state=~"ready|processing|dead"}`、`cheeseim_node_queue_retry_total`、`cheeseim_delivery_dedup_claim_total`、`cheeseim_writer_operation_total{result=~"drop|sync_failed|retry_exhausted_backpressure"}`、`cheeseim_online_route_lookup_total{result="stale"}`。

## 3. Smoke 与语法验证

```bash
node --input-type=module --check < server/perf/k6/ws-im.js
node --check server/perf/k6/verify-summary.mjs
bash -n server/perf/chaos/run-chaos.sh
CONFIRM_CHAOS=no POSTOFFICE_RESTART_CMD='true' server/perf/chaos/run-chaos.sh postoffice-restart
```

本地没有 k6 时只做以上静态验证；具备 k6 和两张有效 ticket 后再执行 `CONNECTIONS=2 HOLD=10s MESSAGE_RATE=1` 的真实 smoke。
