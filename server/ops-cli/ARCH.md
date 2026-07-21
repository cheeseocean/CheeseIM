# ops-cli/ARCH.md — 独立运维命令边界

## 1. 职责

- 不开放 HTTP、TCP 或 Dubbo；
- 通过运维主机权限以及 Kafka/Mongo 凭据授权；
- 当前提供 DLT offset 查询和单条受控 redrive；
- 输出只包含摘要、checksum 和审计结果，禁止输出原始消息 payload。

## 2. DLT 不变量

- source topic 必须来自 `TopicNames.managedTopics()`；
- 查询使用 assign + seek，不加入 consumer group、不提交 DLT offset；
- redrive 是 copy-not-delete，原 DLT 保留到 retention 到期；
- 每次只重放精确一条 `(topic,partition,offset,checksum)`；
- `operationId` 绑定 DLT 身份、operator 和 reason，Mongo lease 阻止并发执行；
- broker ACK 后才完成审计；ACK 后进程崩溃仍可能重复复制，依赖原链路稳定消息 ID/inbox 去重；
- redrive 使用新的 Kafka CreateTime，禁止沿用旧时间导致消息立即过期。

## 3. 禁止

- 禁止增加“重放整个 topic/partition”的无上限命令；
- 禁止删除 DLT、提交 DLT consumer offset或修改原记录；
- 禁止绕过 checksum、reason、operatorId 或 operationId；
- 禁止把该能力直接暴露给普通用户 SessionPrincipal。
