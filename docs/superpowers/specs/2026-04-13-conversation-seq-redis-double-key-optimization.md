# Conversation Seq Redis Double-Key Optimization

**Date:** 2026-04-13

## Goal

为 CheeseIM 的会话 seq Redis 缓存段实现补充一份优化设计，重点解决当前“数据和锁共用一个 key”带来的退化问题。

该文档只描述优化方案，不包含实现任务拆解。

## Current State

当前 Redis 会话 seq 缓存段实现位于：

- [RedisConversationSeqCacheStore.java](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/redis/RedisConversationSeqCacheStore.java)

当前模型使用单个 hash key 保存：

- `CURR`
- `LAST`
- `TIME`
- `LOCK`

扩段时会在同一个 key 上写入锁并缩短 TTL。

## Problem

当前单 key 方案可以工作，但有一个结构性问题：

- 数据状态和并发锁耦合在一起
- 锁 TTL 与数据 TTL 共享
- 扩段期间如果短 TTL 过期，整条缓存数据会一起丢失

这会导致：

1. 其他节点更容易把已有缓存误判成 `MISS`
2. Mongo 扩段次数增加
3. seq 空洞增多
4. 调试时很难区分“锁丢了”还是“数据丢了”

它不会直接造成重复 seq，但会让退化路径变差。

## Recommended Design

推荐把当前单 key 方案调整为双 key 方案。

### Data Key

- `im:seq:conv:{conversationId}`

字段：

- `CURR`
- `LAST`
- `TIME`

职责：

- 存放当前缓存段状态
- 生命周期长
- 不再承载锁信息

### Lock Key

- `im:seq:conv:{conversationId}:lock`

值：

- `ownerToken`

职责：

- 仅用于扩段互斥
- 生命周期短
- 不承载任何 seq 数据

## State Machine

外部状态机保持不变，仍然是：

- `ALLOCATED`
- `MISS`
- `LOCKED`
- `EXHAUSTED`

差异只在于内部实现。

### `MISS`

条件：

- data key 不存在
- 当前节点成功抢到 lock key

语义：

- 当前节点负责回源 Mongo 初始化缓存段

### `LOCKED`

条件：

- lock key 已存在

语义：

- 其他节点正在初始化或扩段
- 当前节点等待后重试

### `ALLOCATED`

条件：

- data key 存在
- lock key 不存在
- `CURR + size <= LAST`

语义：

- 直接在 Redis 内原子推进 `CURR`

### `EXHAUSTED`

条件：

- data key 存在
- lock key 不存在
- `CURR + size > LAST`
- 当前节点成功抢到 lock key

语义：

- 当前节点负责回源 Mongo 扩段并重写缓存段

## Suggested Lua Semantics

### allocate

输入：

- `conversationId`
- `size`
- `ownerToken`
- `lockTtl`
- `dataTtl`
- `nowMillis`

逻辑：

1. 如果 data key 不存在：
   - 尝试 `SETNX lockKey ownerToken EX lockTtl`
   - 成功返回 `MISS`
   - 失败返回 `LOCKED`

2. 如果 data key 存在：
   - 如果 `size == 0`
     - 直接返回 `CURR/LAST/TIME`
   - 如果 lock key 存在
     - 返回 `LOCKED`
   - 如果 `CURR + size <= LAST`
     - 更新 `CURR/TIME`
     - 返回 `ALLOCATED`
   - 否则：
     - 尝试设置 lock key
     - 成功返回 `EXHAUSTED`
     - 失败返回 `LOCKED`

### install

输入：

- `conversationId`
- `ownerToken`
- `currSeq`
- `lastSeq`
- `time`

逻辑：

1. 校验 lock key 的 owner 是否为当前 `ownerToken`
2. 如果不匹配：
   - 返回失败状态
   - 不覆盖现有缓存段
3. 如果匹配：
   - 写 data key 的 `CURR/LAST/TIME`
   - 删除 lock key
   - 返回成功

## TTL Recommendation

### Data Key

建议：

- 长 TTL
- 或不主动过期

原因：

- 会话 seq 缓存是小对象
- 数据状态应当比锁稳定
- 不应因为短期扩段竞争而丢失整条缓存

第一版建议：

- data key 不主动过期

### Lock Key

建议：

- 3s 到 5s

原因：

- 扩段本来就是短操作
- 锁必须短命，防止死锁

## Advantages

1. 锁超时不会删除 seq 数据
2. 其他节点可以稳定读到当前缓存段状态
3. Mongo 回源频率更低
4. 空洞控制更好
5. 数据和锁职责分离，后续监控更清晰

## Compatibility

这项优化不需要改动以下上层接口：

- `ConversationSeqCacheStore`
- `ConversationSeqAllocator`
- `ConversationSeqService`

因此它是 Redis 实现内部优化，不影响上层业务调用方式。

## Recommendation

当前阶段：

- 保留现有四态状态机和接口定义
- 不立即实现

后续 Redis 优化阶段：

- 优先将会话 seq 缓存实现改成 data key + lock key 双 key 结构

这是一个合理且低风险的演进方向，主要收益在退化行为和可维护性，而不是单次分配性能。
