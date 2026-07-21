# Kafka DLT 运维手册

> 状态：权威。适用于 CheeseIM 管理 topic 的 `.DLT`。命令不提供业务 HTTP 接口。

## 1. 安全边界

- 只允许 `TopicNames` 中的 source topic；命令参数传 `ingress`，不是 `ingress.DLT`。
- 查询只返回 offset、时间、key 指纹、payload 字节数、checksum 和截断后的异常摘要，不返回 payload。
- 查询使用独立 consumer 的 `assign + seek`，不加入 consumer group、不提交 offset。
- redrive 每次只复制一条记录，原 DLT 保留到 broker retention 到期，不支持批量删除或整分区重放。
- redrive 必须提供稳定 operationId、列表返回的 checksum、operatorId 和变更原因。
- Mongo `dlt_redrive_audit` 默认不设 TTL；归档或清理必须由独立合规 migration 执行。

## 2. 前置条件

1. 使用只允许读取 `*.DLT`、写 CheeseIM 管理 topic 的 Kafka 运维凭据。
2. 使用可读写 `dlt_redrive_audit` 的 Mongo 凭据。
3. 设置 `MONGODB_URI`、`KAFKA_BOOTSTRAP_SERVERS`；redrive 额外设置
   `CHEESEIM_DLT_OPERATOR_ID` 为工单系统可追踪的操作者身份。
4. 生产环境应设置 `KAFKA_TOPICS_AUTO_CREATE_ENABLED=false`，避免运维命令误建 topic。

构建可执行包：

```bash
cd server
./gradlew :ops-cli:bootJar
```

下文用 `java -jar ops-cli.jar` 表示构建产物的实际路径。

## 3. 查询

```bash
java -jar ops-cli.jar list \
  --topic=ingress \
  --partition=0 \
  --after=-1 \
  --limit=50
```

`after` 是排他 offset；下一页继续传上一页返回的 `nextAfterOffset`。`limit` 在服务端有硬上限，
因此该命令不能退化为一次性扫描完整 DLT。

先根据异常摘要、发生时间和上下游状态确认根因已经消除，再决定是否 redrive。未知 schema、
持续性依赖故障或无法证明下游幂等时不得重放。

## 4. 单条 redrive

```bash
export CHEESEIM_DLT_OPERATOR_ID='ops-user-or-ticket-subject'
java -jar ops-cli.jar redrive \
  --operation-id=INC-20260719-001-ingress-0-42 \
  --topic=ingress \
  --partition=0 \
  --offset=42 \
  --checksum='<list 返回的 64 位 sha256>' \
  --reason='dependency recovered; approved by INC-20260719-001'
```

执行过程：

1. 精确读取 DLT offset 并校验 retained range、original-topic header 与 checksum；
2. operationId 在 Mongo 中绑定记录身份、operator、reason，并抢占有 generation 的短租约；
3. 以新的 Kafka CreateTime 复制到原 source topic，附加稳定 operationId 和 DLT identity header；
4. 等待 broker ACK 后将审计标记为 COMPLETED。

相同 operationId 与完全相同参数重复执行会返回既有完成态；operationId 绑定不同记录或原因会被拒绝。

## 5. 审计与故障处理

审计点查：

```javascript
db.dlt_redrive_audit.findOne({_id: "INC-20260719-001-ingress-0-42"})
```

- `PROCESSING` 且 lease 未过期：不得并发执行，等待当前操作者完成。
- `FAILED` 或 lease 已过期：确认 broker 与原消费链路状态后，使用完全相同参数和 operationId 重试。
- `COMPLETED`：不要创建新 operationId 重复重放；先核对业务消息稳定 ID/inbox 结果。

broker 已 ACK、进程却在审计完成前崩溃时，重试可能再次复制。operationId header 提供稳定追踪身份，
正确性仍依赖各主链路既有 message identity/inbox 去重；这不是 Kafka exactly-once 承诺。

redrive 后不删除 DLT。若需阻止再次操作，以审计 COMPLETED 和工单关闭为准；broker retention 到期后自然清理。

## 6. 监控与告警

- `cheeseim_dlt_operation_total{operation,topic,result}`：任何 redrive failure 立即告警；
- `cheeseim_dlt_operation_latency_seconds`：运维控制面耗时；
- DLT backlog/最老记录年龄由 Kafka exporter 按 `*.DLT` partition offset 监控，不以本 CLI 定时全扫代替。

生产阈值需按 topic SLO 校准。最低基线：DLT 新增记录立即通知当班；最老记录接近 retention 的 50% 时升级；
PROCESSING 审计超过 lease 仍未完成时告警。完成真实 Kafka 故障演练前，本能力只能标记为仓库就绪。
