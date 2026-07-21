# infra-state/ARCH.md — Redis/RocksDB 状态基础设施事实快照

> Session、refresh token、会话水位、幂等 inbox、delivery、typing、seq cache 与 typed cache 的运行时实现。

## 1. 模块边界

- common-core 只保留 Store/Cache port、状态机结果、seq allocator 和稳定业务模型；
- 本模块拥有 Redis Lua/pipeline、RocksDB、本地状态目录、状态配置与自动装配；
- feature 默认只依赖 common-core port，不得直接 import Redis/RocksDB adapter；
- 例外只有 api-server composition root：显式构造最小 `RedisIdempotencyStore`，并关闭完整状态自动配置；
- 节点 ready/processing/lease/dead Lua 当前由 postoffice/postman 的 Redis-specific service 共享，
  `NodeQueueRedisScripts` 因此归本模块，但不是通用 `QueueAdapter` 的一部分。

## 2. 自动装配

- `StateStoreAutoConfigurer`、`CacheAutoConfigurer`、`ConversationSeqAllocatorConfigurer` 通过
  `AutoConfiguration.imports` 注册；
- 默认 `cheeseim.state.auto-config-enabled=true`；api-server 固定为 false，仅保留显式幂等 adapter；
- 检测到 Redis 配置时使用共享 Redis 实现；无 Redis 的单机模式回退 RocksDB；
- cluster 模式没有 Redis 时必须 fail-fast，禁止每个副本各用一份 RocksDB 冒充共享状态；
- 每个 port 都允许业务装配层提供自定义 Bean 覆盖默认实现。

## 3. 正确性与性能不变量

- Redis 多 key Lua 必须使用同一 hash tag；跨 slot 批量只能 pipeline，不能 MGET/多 key DEL；
- read/max/delivery 水位只允许单调推进，禁止 `$set`/read-modify-write 导致回退；
- refresh token family 的 rotate/reuse/revoke 必须保持单 key 原子且只保存 token hash；
- message/ingress inbox 必须保留 claim、稳定 ID/seq binding、complete/release 语义，不能退化成通用 SETNX；
- cluster 正确性依赖 Redis；RocksDB 只服务单 JVM all-in-one/开发环境；
- 状态 key、TTL 或 codec 变化必须给出存量兼容、清空或 migration 策略。

## 4. 依赖方向

```text
feature ──compile──> common-core state/cache port
   └─────runtime──> infra-state ──> common-core/common-api
                                  ├── Redis
                                  └── RocksDB（单机 fallback）
```

根构建 `verifyStateArchitectureBoundary` 阻止 common-core 重新依赖 Redis/RocksDB、feature 越过 port，
以及 infra-state 反向依赖 feature。

## 5. 当前迁移债务

实现类暂保留历史 package `com.cheeseocean.im.common.core.*`，避免物理拆分同时制造大规模无语义改名。
Gradle module、依赖和门禁已经强制源码所有权；后续 package 改名应作为独立机械任务。

## 6. 改动检查

- [ ] 新状态实现是否保持 Redis/RocksDB 两种模式的结果 code 与 TTL 语义；
- [ ] 新 Lua 的全部 key 是否证明同槽并有 failover/slot migration 验收计划；
- [ ] api-server 是否仍禁用完整 state auto-config，且 bootJar 未携带 RocksDB native library；
- [ ] shutdown、backlog 和失败指标是否使用固定低基数标签；
- [ ] cluster profile 是否在缺 Redis 时启动失败，而不是隐式回退本地状态。
