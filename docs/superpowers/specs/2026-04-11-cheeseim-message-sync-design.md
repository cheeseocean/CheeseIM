# CheeseIM Message Sync Design

## Problem

当前 CheeseIM 已经具备消息同步所需的一部分基础设施：

- 会话级全局序列范围：
  [ConversationRangeRepositoryImpl.java](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationRangeRepositoryImpl.java)
- 用户维度同步位点：
  [UserConversationSyncPointRepositoryImpl.java](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationSyncPointRepositoryImpl.java)
- Redis/RocksDB 热状态：
  [ConversationStateStore.java](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/common-core/src/main/java/com/cheeseocean/im/common/core/store/conversation/ConversationStateStore.java)
- 写消息时的热状态推进：
  [MessageStateService.java](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/postmaster/src/main/java/com/cheeseocean/im/postmaster/service/MessageStateService.java)
- readSeq 异步持久化：
  [ReadSeqPersistenceWriter.java](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ReadSeqPersistenceWriter.java)

但当前仍缺少一套正式的“消息同步协议”：

- 历史查询仍以“最近一页”为主，而不是按 `seq` 增量补齐
- 客户端没有“本地 maxSeq vs 服务端 maxSeq”的同步状态机
- push 到 gap repair 的闭环没有建立
- `readSeq`、`user maxSeq`、`conversation maxSeq` 还没有统一收敛为对外同步服务

这会导致几个问题：

- 客户端断线重连时只能依赖最近一页接口，无法可靠补洞
- 多端同步缺少正式水位协议，未读和历史消息容易漂移
- 实时 push 丢包时没有标准修复路径
- 现有 Redis 热状态和 Mongo 持久状态没有被组织成完整同步体系

## OpenIM Reference Model

参考的核心实现来自：

- 服务端：
  [sync_msg.go](/Users/xxxcrel/Develop/backend/go/Open-IM-Server/internal/rpc/msg/sync_msg.go)
- 服务端缓存：
  [conversation.go](/Users/xxxcrel/Develop/backend/go/Open-IM-Server/pkg/common/storage/cache/conversation.go)
  [redis/conversation.go](/Users/xxxcrel/Develop/backend/go/Open-IM-Server/pkg/common/storage/cache/redis/conversation.go)
  [redis/seq_user.go](/Users/xxxcrel/Develop/backend/go/Open-IM-Server/pkg/common/storage/cache/redis/seq_user.go)
- SDK 同步状态机：
  [msg_sync.go](/Users/xxxcrel/Develop/backend/go/openim-sdk-core/internal/interaction/msg_sync.go)
  [sync.go](/Users/xxxcrel/Develop/backend/go/openim-sdk-core/internal/conversation_msg/sync.go)

OpenIM 的关键思想不是“翻页拉历史”，而是：

1. 服务端返回每个会话当前 `maxSeq`
2. 客户端维护本地 `syncedMaxSeq`
3. 客户端按缺口区间 `[localMax+1, serverMax]` 拉取消息
4. 收到 push 时如果 seq 不连续，则立刻触发补拉
5. 已读同步和消息体同步分开处理

## Design Goals

CheeseIM 的同步设计需要满足：

- 支持断线重连后的增量补齐
- 支持多端同时在线时的历史消息同步
- 支持推送消息丢失时的 gap repair
- 支持 `readSeq/maxSeq/unread` 的独立同步
- 保持 Redis 热状态优先、Mongo 持久化兜底
- 允许 CheeseBox 和后续移动端 SDK 复用同一套同步语义

## Recommended Architecture

新增统一同步服务：

- `common-api`
  - `ConversationSyncService`
- `business`
  - `ConversationSyncServiceImpl`

此服务只负责“同步协议”，不替代现有：

- `ConversationService`
- `HistoryQueryService`

职责划分如下：

- `ConversationService`
  - 用户会话元数据
  - 置顶、接收选项、草稿等
- `ConversationSyncService`
  - seq 水位查询
  - 按 seq 拉取消息
  - readSeq/maxSeq 快照
  - 已读确认
- `HistoryQueryService`
  - 面向普通历史浏览的 HTTP 视图
  - 不承担断线增量同步职责

## Service Contract

建议 `ConversationSyncService` 至少暴露 4 个方法。

### 1. Get Conversation Max Seqs

能力：

- 返回用户可见会话的当前服务端 `maxSeq`

建议签名：

- `Map<String, Long> getConversationMaxSeqs(String userId, List<String> conversationIds)`

语义：

- `conversationIds` 为空时，返回该用户全部会话的 `maxSeq`
- 优先从 Redis 热状态读取
- 缺失时回落到 `ConversationRangeRepository`

### 2. Pull Messages By Seq Ranges

能力：

- 按会话的 seq 区间增量拉取消息

建议签名：

- `PullMessagesResponse pullMessagesBySeqRanges(String userId, List<SeqRangeRequest> ranges, int limitPerConversation)`

`SeqRangeRequest` 包含：

- `conversationId`
- `beginSeq`
- `endSeq`

返回建议包含：

- `Map<String, List<Message>> messagesByConversation`
- `Map<String, Long> endSeqByConversation`
- `Map<String, Boolean> completedByConversation`

语义：

- 消息源来自历史持久层，而不是 Redis 缓存
- 服务端负责做会话权限校验
- 若单次范围过大，可按 `limitPerConversation` 截断，并告知是否完成

### 3. Get Conversation Read Snapshots

能力：

- 返回用户视角下每个会话的 `readSeq/maxSeq/unread`

建议签名：

- `Map<String, ConversationReadSnapshot> getConversationReadSnapshots(String userId, List<String> conversationIds)`

`ConversationReadSnapshot` 包含：

- `conversationId`
- `readSeq`
- `maxSeq`
- `unreadCount`

语义：

- `readSeq` 优先从 Redis 热状态读取，回落到 `UserConversationSyncPointRepository`
- `maxSeq` 优先从 Redis 热状态读取，回落到 `ConversationRangeRepository`
- `unreadCount` 优先直接读取 Redis 计数
- 当 Redis miss 时可用 `maxSeq - readSeq` 回补

### 4. Ack Read Seq

能力：

- 标记已读位点

建议签名：

- `void ackReadSeq(String userId, String conversationId, long readSeq)`

语义：

- 同步更新 Redis `userReadSeq`
- 异步写入 `UserConversationSyncPointRepository`
- 将业务表 `unreadCount` 收敛为 0 或按新位点重算

## Storage Responsibilities

### ConversationRange

`ConversationRange` 是全局会话维度的 seq 边界：

- `minSeq`
- `maxSeq`

用途：

- 作为服务端消息水位的权威持久值
- 用于重建 Redis 丢失后的会话最大序列

不承担：

- 用户已读
- 用户未读
- 用户同步进度

### UserConversationSyncPoint

`UserConversationSyncPoint` 是用户维度同步位点：

- `maxSeq`
- `minSeq`
- `readSeq`

用途：

- 持久化用户级同步水位
- Redis 热状态恢复时的兜底值
- 多端切换、离线恢复时的可靠基线

其中：

- `readSeq` 是必须对外暴露的同步位点
- `maxSeq` 建议也持久化，而不只停留在 Redis

### ConversationStateStore

`ConversationStateStore` 是 Redis/RocksDB 热状态投影：

- `conversation maxSeq`
- `user maxSeq`
- `user readSeq`
- `user unread`
- `conversation lastMessageSummary`

它的定位是：

- 实时写入
- 高频读取
- 支撑推送和同步接口的热点查询

它不是最终持久层。

## Cache Strategy

推荐延续当前思路：

- Redis 优先
- Mongo 兜底
- 异步刷盘

### Read Path

查询 `maxSeq/readSeq/unread` 时：

1. 先查 `ConversationStateStore`
2. miss 时回 Mongo：
   - `ConversationRangeRepository`
   - `UserConversationSyncPointRepository`
3. 必要时回填 Redis

### Write Path

写消息时：

1. 分配 seq
2. 更新 Redis：
   - `conversation maxSeq`
   - `user maxSeq`
   - `sender readSeq`
   - `recipient unread`
   - `lastMessageSummary`
3. 异步刷 Mongo：
   - `ConversationRange.maxSeq`
   - `UserConversationSyncPoint.maxSeq`

标记已读时：

1. 先写 Redis `userReadSeq`
2. 异步刷 Mongo `UserConversationSyncPoint.readSeq`

## New Persistence Workers

当前已有：

- `ReadSeqPersistenceWriter`

还应新增：

- `UserMaxSeqPersistenceWriter`

职责：

- 按 `(userId, conversationId)` 聚合取最大 `maxSeq`
- 将 Redis 中推进过的 `user maxSeq` 异步刷入 `UserConversationSyncPointRepository`

这样可以避免：

- Mongo 中的 `readSeq` 持久了，但 `user maxSeq` 永远停留在旧值
- 多端恢复时只能依赖 Redis 热状态

## Message Retrieval Strategy

当前 [HistoryQueryService.java](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryQueryService.java) 只提供“最近一页历史消息”。

这类接口应保留，但定位应变为：

- 面向普通历史浏览
- 面向 HTTP UI 直接展示

不应继续承担：

- reconnect sync
- wakeup sync
- gap repair

同步拉取必须切换为：

- 基于 `conversationId + seq range`
- 可按 `limitPerConversation` 分片返回

## Client Sync State Machine

CheeseBox 和未来 SDK 的状态机建议统一为：

### Local State

本地维护：

- `syncedMaxSeqs: Map<conversationId, long>`
- `readSeqs: Map<conversationId, long>`
- 本地消息库中的消息体

### On Login / Reconnect / Wakeup

流程：

1. 调 `getConversationMaxSeqs`
2. 对每个会话计算：
   - `localMax = syncedMaxSeqs.getOrDefault(conversationId, 0)`
   - `serverMax = response.maxSeq`
3. 若 `serverMax > localMax`，构造区间：
   - `[localMax + 1, serverMax]`
4. 调 `pullMessagesBySeqRanges`
5. 落本地消息库
6. 成功后推进 `syncedMaxSeqs`

### On Push

推送消息到达时：

- 如果 `incomingSeq == localMax + 1`
  - 直接落本地
  - 推进 `syncedMaxSeqs`
- 如果 `incomingSeq > localMax + 1`
  - 说明有 gap
  - 立即触发补拉 `[localMax + 1, incomingSeq]`

### On Read Sync

独立周期或事件触发：

1. 调 `getConversationReadSnapshots`
2. 同步本地 `readSeq`
3. 用服务端返回的 `unreadCount` 或 `maxSeq - readSeq` 更新本地会话列表

## Multi-Device Semantics

推荐采用以下多端同步语义：

- 消息体同步：
  - 每个端 independently 维护自己的 `syncedMaxSeqs`
  - 服务端不保存“某个端同步到了哪里”
  - 只保存“用户会话级 readSeq/maxSeq”
- 已读同步：
  - `readSeq` 以用户维度共享
  - 某端标记已读后，其他端在下次 read snapshot sync 或通知后收敛

这样做的好处：

- 服务端不需要维护“设备维度消息同步位点”
- 多端历史补齐仍由客户端本地状态机决定
- 已读状态仍然可以保持用户维度一致

## Notifications For Sync State

可选增强：

- 新增会话同步提示通知，例如：
  - `conversation_read_updated`
  - `conversation_deleted`
  - `conversation_changed`

作用：

- 减少客户端轮询 `getConversationReadSnapshots`
- 让多端已读更新更快收敛

这部分不是第一阶段必须项。

## Phase 1 Scope

第一阶段建议只做这些：

- `ConversationSyncService`
  - `getConversationMaxSeqs`
  - `pullMessagesBySeqRanges`
  - `getConversationReadSnapshots`
  - `ackReadSeq`
- `UserMaxSeqPersistenceWriter`
- CheeseBox 本地 `syncedMaxSeqs`
- reconnect sync
- push gap repair

不在第一阶段内：

- 本地复杂分页缓存校验
- 设备级同步位点
- 删除消息后的复杂重排
- 会话级增量 hash 同步

## Why This Fits CheeseIM

这套设计和当前 CheeseIM 的已有结构是兼容的：

- 不需要推翻现有 `ConversationRange/UserConversationSyncPoint/ConversationStateStore`
- 不需要让 `HistoryQueryService` 承担不适合的同步职责
- 可以先服务 CheeseBox，再逐步沉淀成移动端 SDK 能复用的同步模型

它和 OpenIM 的一致点在于：

- 以 `seq` 为核心同步边界
- push 只是优化，不是唯一消息来源
- gap repair 必须是第一等公民
- `readSeq` 与消息体同步分开

## Implementation Notes

实现时应注意：

- `pullMessagesBySeqRanges` 的返回顺序应稳定，优先按 seq 升序
- 通知会话可单独分流，但第一阶段不强制分离返回结构
- `ackReadSeq` 必须保证单调递增，不允许回退
- `UserMaxSeqPersistenceWriter` 也应按最大值聚合，避免乱序刷盘回退
- 服务端 `maxSeq/readSeq` 查询接口必须做会话权限校验

## Recommendation

按以下顺序实施：

1. 先定义 `ConversationSyncService` 合约
2. 再落 `business` 实现
3. 然后补 `UserMaxSeqPersistenceWriter`
4. 最后让 CheeseBox 接入本地同步状态机

这样可以先把服务端边界钉死，再让客户端开始真正的断线补齐和多端同步。
