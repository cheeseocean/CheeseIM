# Conversation Seq Allocator Design

**Date:** 2026-04-13

## Goal

为 CheeseIM 的会话消息 seq 提供一套和 OpenIM 一致的分配模型：
- Mongo 作为全局真相源
- Redis 作为分布式缓存号段
- RocksDB 作为单机无 Redis 部署时的本地缓存号段

目标语义：
- 会话内 seq 单调递增
- 不重复
- 允许空洞
- 多节点模式下不能依赖本地 RocksDB 保证全局一致性

## Current Problem

当前 [ConversationSeqService](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/postmaster/src/main/java/com/cheeseocean/im/postmaster/service/ConversationSeqService.java) 直接依赖通用 [SequenceIdGenerator](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/id/SequenceIdGenerator.java)。

这套实现的问题：
- 主路径是 Redis `INCRBY`
- 降级路径是本机 RocksDB
- 对通用 ID 合理
- 对消息 seq 不够稳

一旦多节点部署时 Redis 不可用，多个节点各自使用 RocksDB，会破坏同一会话的全局单调性。

## OpenIM Model

OpenIM 的 `seq_conversation` 模型分两层：

1. Mongo 真相源
- `findOneAndUpdate + $inc(max_seq, size)` 原子推进高水位
- 负责全局一致性

2. Redis 缓存段
- 每个会话维护 `CURR / LAST / TIME / LOCK`
- 只缓存已经从 Mongo 预留出的号段
- 命中缓存时直接分配
- 耗尽时再去 Mongo 申请下一段并回填

结果：
- Mongo 决定一致性
- Redis 决定性能
- 崩溃只会造成空洞，不会造成重复

## CheeseIM Design

### Core Components

1. `ConversationRangeRepository`
- 继续使用现有 Mongo 实现
- 作为唯一 durable high watermark

2. `ConversationSeqCacheStore`
- 新增接口
- 抽象 Redis 和 RocksDB 两类缓存段存储

3. `ConversationSeqAllocator`
- 统一协调缓存段与 Mongo 扩段
- 对外暴露：
  - `next(conversationId)`
  - `allocate(conversationId, size)`
  - `getMaxSeq(conversationId)`

4. `ConversationSeqService`
- `postmaster` 侧消费的会话 seq 服务
- 不再直接依赖 `SequenceIdGenerator`

### Cache Key Model

会话 seq 缓存逻辑对齐 OpenIM：
- `CURR`
- `LAST`
- `TIME`
- `LOCK`

Redis key 建议：
- `im:seq:conv:{conversationId}`

RocksDB 也存储同样语义的数据结构，只是作用域仅限当前单机。

### Allocation Semantics

缓存分配状态：
- `0`: 当前缓存段足够，直接分配
- `1`: 当前会话没有缓存段，需要去 Mongo 初始化缓存段
- `2`: 已被其他分配者锁定，等待重试
- `3`: 当前缓存段已耗尽，需要去 Mongo 扩段

Mongo 扩段规则：
- 单聊：基础段 `50`
- 群聊：基础段 `100`
- 最终申请大小 = `基础段 + 请求 size`

### Deployment Modes

#### cluster
- 必须启用 Redis 缓存段
- Mongo 作为真相源
- 多节点共享 Redis 缓存段

#### standalone
- 允许 Redis 或 RocksDB
- 无 Redis 时，RocksDB 仅作为本机缓存段
- Mongo 仍然是真相源

### Fail-Fast Rule

如果部署模式是 `cluster` 且 Redis seq cache 未启用：
- 启动直接失败
- 不允许退化为 RocksDB 缓存段

原因：
- RocksDB 只能保证单机
- 不能给多节点部署制造“看似可用”的错误安全感

## SequenceIdGenerator Boundary

`SequenceIdGenerator` 继续保留，但职责收缩：
- 只用于通用业务 ID
- 不再用于会话消息 seq

消息 seq 统一走：
- `ConversationSeqAllocator`
- `ConversationRangeRepository`

## Migration

1. 新增 `ConversationSeqCacheStore`
2. 新增 Redis / RocksDB 实现
3. 新增 `ConversationSeqAllocator`
4. 改造 `ConversationSeqService`
5. 移除消息主链对 `SequenceIdGenerator` 的依赖

## Verification

至少覆盖：
- 单节点连续分配
- 批量分配连续区间
- Redis miss 初始化缓存段
- Redis 段耗尽扩段
- RocksDB standalone 模式分配
- Mongo 与缓存段不一致时以 Mongo 为准重写
- 多线程并发不重复
