# infra-queue/ARCH.md — 队列运行时基础设施事实快照

> Kafka/Chronicle 驱动、监听器装配与 Kafka DLT 实现。队列业务契约仍归 `common-core` 所有。

## 1. 模块边界

- `common-core.queue` 只保留 `QueueAdapter`、消息/订阅模型、handler、生产者接口、监听注解和 DLT port；
- 本模块实现 Chronicle 单机队列、Kafka 集群队列、`@QueueListener` 注册、topic 契约校验和 Kafka DLT 操作；
- feature 只允许 import `com.cheeseocean.im.common.core.queue.*`，不得 import `com.cheeseocean.im.infra.queue.*`；
- 本模块是 library，不是独立服务，不增加 RPC 或网络跳数。

## 2. 装配契约

- Spring Boot 通过 `AutoConfiguration.imports` 加载三个自动配置，不依赖 feature 扩大 component scan；
- `cheeseim.queue.type=chronicle`（默认）只创建 Chronicle adapter，不创建 Kafka producer/admin；
- `cheeseim.queue.type=kafka` 创建 byte[] Kafka producer、Kafka adapter，并校验主 topic 与 DLT 契约；
- `QueueAdapter`、监听后处理器和 DLT port 均允许调用方提供自定义 Bean 覆盖默认实现；
- DLT 实现只有在存在 `DltRedriveAuditStore` 时装配，普通业务进程不会获得运维操作端口。
- listener runtime 持有每个 `Subscription`，Spring context 关闭时逆序 unsubscribe；Chronicle poller 先等待
  当前 handler 结束，30 秒后才强制中断，避免正常滚动发布遗留非 daemon 线程或无条件打断处理中批次。

## 3. 性能与可靠性不变量

- 同会话消息 key 必须稳定映射到同一 Kafka partition；
- 消费确认必须发生在 handler 成功之后，失败按统一 consumer policy 重试并进入 DLT；
- Kafka 与 Chronicle 使用相同 payload 语义：protobuf/byte[] 不经过对象 JSON 二次编码；
- batch listener 必须保留按 key 顺序，不能为了并行吞吐打乱单会话消息；
- cluster 环境的 topic 分区、副本、minISR、retention 必须通过启动校验；业务 Pod 是否拥有 DDL 权限由配置独立控制；
- Chronicle 仅用于 all-in-one/本地单机，不是多副本生产后端。

## 4. 依赖方向

```text
feature ──compile──> common-core queue port
   └─────runtime──> infra-queue ──> common-core/common-api
                                  ├── Kafka
                                  └── Chronicle
```

根构建的 `verifyQueueArchitectureBoundary` 阻止 common-core 反向依赖驱动、feature import 实现包，
以及 infra-queue 反向依赖业务模块。

## 5. 改动检查

- [ ] 新增后端必须实现相同的 ACK、重试、DLT、key 顺序和 batch 语义；
- [ ] 不得在 feature 直接注入 `KafkaTemplate` 或 Chronicle API；
- [ ] 调整 topic 契约需同步 `distro/kafka`、cluster 配置、DLT runbook 和容量模型；
- [ ] 自动配置变更需验证 Chronicle/Kafka 两种 context 以及 all-in-one 单 Bean 装配；
- [ ] 大幅调整 consumer 并发前需确认 partition 数、下游 Mongo/Redis 容量与 rebalance 时间。
