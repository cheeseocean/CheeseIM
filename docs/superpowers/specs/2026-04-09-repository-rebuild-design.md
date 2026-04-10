# Repository Rebuild Design

Date: 2026-04-09

## Goal

参考 `/Users/xxxcrel/Develop/backend/go/Open-IM-Server/pkg/common/storage/database/mgo` 下的 Mongo 实现，重建 Java 侧以下 6 个 repository 的定义与实现：

- `conversation.go` -> `UserConversationRepository`
- `seq_conversation.go` -> `ConversationRangeRepository`
- `seq_user.go` -> `UserConversationSyncPointRepository`
- `user.go` -> `UserRepository`
- `friend.go` -> `FriendshipRepository`
- `friend_request.go` -> `FriendRequestRepository`

本次目标不是机械翻译 Go 代码，而是按当前 Java 模型、模块边界和业务语义做 MongoTemplate 风格的等价重建。

## Scope

本次会直接删除当前 Java 中对应的旧 repository 定义与实现，并新建一套新的接口与实现。

仅覆盖上述 6 个 repository。

不在本次范围内：

- 版本日志能力
- user command 相关存储
- 其他 group / blacklist / request repository
- 会话最后一条消息快照字段恢复

## Design Principles

### 1. 接口语义对齐 Go，命名保持 Java 可读性

Java 侧保留领域语义命名，不机械照搬 Go 的 `Take`、`UpdateByMap`、`Page` 等命名。

接口风格原则：

- `Take/FindOne` -> `findOne` / `findById`
- `Find` -> `find...`
- `Create` -> `createAll` / `saveAll`
- `UpdateByMap` -> `updateFields`
- `Page` -> 仅保留当前业务需要的分页查询，不引入通用分页抽象

### 2. MongoTemplate 为主，不再依赖零散的 Spring Data 派生查询

新实现统一放在 `common-core/.../mongo/impl`，以 `MongoTemplate` 为主实现：

- upsert
- projection
- 批量 update
- 排序
- 条件组合

这样更接近 Go 侧实现，也更适合复杂过滤和批量更新。

### 3. 不恢复已明确删掉的旧设计

以下字段与语义保持删除状态，不因参考 Go 代码重新引入：

- `ConversationDTO`
- `latestMsg`
- `latestMsgSeq`
- 会话列表服务端最后一条消息摘要拼装

会话列表排序继续以 `updatedAt` 为准，最后一条消息由客户端缓存或按需加载。

### 4. 好友关系与好友申请拆分

Java 当前 `FriendRepository` 混合了好友关系与好友申请能力。参考 Go 两个文件的职责划分，本次拆成两个独立仓储：

- `FriendshipRepository`
- `FriendRequestRepository`

这样能减少接口耦合，也更符合当前业务语义。

## Model Mapping

### UserConversation

Go: `conversation.go`

Java:

- Domain: `UserConversation`
- Document: `UserConversationDoc`
- Repository: `UserConversationRepository`

字段语义：

- `ownerUserId`
- `conversationId`
- `conversationType`
- `targetId`
- `receiveOpt`
- `unreadCount`
- `pinned`
- `draftText`
- `attachedInfo`
- `groupAtType`
- `privateChat`
- `burnDuration`
- `msgDestruct`
- `msgDestructTime`
- `latestMsgDestructTime`
- `createdAt`
- `updatedAt`

索引：

- unique `(ownerUserId, conversationId)`
- index `ownerUserId`
- index `conversationId`
- index `(ownerUserId, updatedAt desc)`

### ConversationRange

Go: `seq_conversation.go`

Java:

- Domain: `ConversationRange`
- Document: `ConversationRangeDoc`
- Repository: `ConversationRangeRepository`

字段语义：

- `conversationId`
- `minSeq`
- `maxSeq`

索引：

- unique `conversationId`

### UserConversationSyncPoint

Go: `seq_user.go`

Java:

- Domain: `UserConversationSyncPoint`
- Document: `UserConversationSyncPointDoc`
- Repository: `UserConversationSyncPointRepository`

字段语义：

- `userId`
- `conversationId`
- `minSeq`
- `maxSeq`
- `readSeq`

索引：

- unique `(userId, conversationId)`

### User

Go: `user.go`

Java:

- Domain: `User`
- Document: `UserDoc`
- Repository: `UserRepository`

保留与当前 IM 实际需要相关的资料字段与查询，不带入 user command 子集合逻辑。

索引：

- unique `userId`

### Friendship

Go: `friend.go`

Java:

- Domain: `Friendship`
- Document: `FriendshipDoc`
- Repository: `FriendshipRepository`

字段语义对齐：

- `ownerUserId`
- `friendUserId`
- `remark`
- `pinned`
- `createTime`
- 其他当前 Java 模型中已有且业务仍使用的字段

索引：

- unique `(ownerUserId, friendUserId)`
- index `(ownerUserId, pinned desc, createdAt desc)`
- index `friendUserId`

### FriendRequest

Go: `friend_request.go`

Java:

- Domain: `FriendRequest`
- Document: `FriendRequestDoc`
- Repository: `FriendRequestRepository`

字段语义：

- `fromUserId`
- `toUserId`
- `reqMsg`
- `handleResult`
- `handlerUserId`
- `handleMsg`
- `handleTime`
- `ex`
- `createTime`
- `updatedAt`

索引：

- unique `(fromUserId, toUserId)`
- index `(toUserId, handleResult, updatedAt desc)`
- index `(fromUserId, handleResult, updatedAt desc)`

## Repository Interfaces

### UserConversationRepository

保留这些能力：

- `createIfAbsent(UserConversation conversation)`
- `saveAll(List<UserConversation> conversations)`
- `updateFields(String ownerUserId, String conversationId, Map<String, Object> fields)`
- `updateBatchFields(List<String> ownerUserIds, String conversationId, Map<String, Object> fields)`
- `findOne(String ownerUserId, String conversationId)`
- `findByIds(String ownerUserId, List<String> conversationIds)`
- `findAll(String ownerUserId)`
- `findConversationIds(String ownerUserId)`
- `findNotReceiveUserIds(String conversationId, List<String> candidateUserIds)`
- `findPinnedConversationIds(String ownerUserId)`

### ConversationRangeRepository

保留这些能力：

- `allocate(String conversationId, long size)`
- `setMaxSeq(String conversationId, long seq)`
- `getMaxSeq(String conversationId)`
- `setMinSeq(String conversationId, long seq)`
- `getMinSeq(String conversationId)`
- `find(String conversationId)`

`allocate` 返回分配前的起始 seq，语义与 Go `Malloc` 一致。

### UserConversationSyncPointRepository

保留这些能力：

- `updateMaxSeq(String userId, String conversationId, long seq)`
- `getMaxSeq(String userId, String conversationId)`
- `updateMinSeq(String userId, String conversationId, long seq)`
- `getMinSeq(String userId, String conversationId)`
- `updateReadSeq(String userId, String conversationId, long seq)`
- `getReadSeq(String userId, String conversationId)`
- `getReadSeqMap(String userId, List<String> conversationIds)`
- `find(String userId, String conversationId)`
- `findByIds(String userId, List<String> conversationIds)`
- `findByUserId(String userId)`

`updateReadSeq` 只允许前进，不允许回退。

### UserRepository

保留这些能力：

- `saveAll(List<User> users)`
- `updateFields(String userId, Map<String, Object> fields)`
- `findById(String userId)`
- `findByIds(List<String> userIds)`
- `exists(String userId)`
- `findByNickname(String nickname)`
- `findNotificationUsers(int level)`
- `findByAppManagerLevelGte(int level)`
- `pageAll(...)`
- `pageByKeyword(...)`
- `findAllUserIds(...)`
- `getGlobalReceiveOption(String userId)`

### FriendshipRepository

保留这些能力：

- `saveAll(List<Friendship> friendships)`
- `delete(String ownerUserId, List<String> friendUserIds)`
- `updateFields(String ownerUserId, String friendUserId, Map<String, Object> fields)`
- `updateBatchFields(String ownerUserId, List<String> friendUserIds, Map<String, Object> fields)`
- `find(String ownerUserId, String friendUserId)`
- `findFriends(String ownerUserId, List<String> friendUserIds)`
- `findReverseFriends(String friendUserId, List<String> ownerUserIds)`
- `findOwnerFriends(String ownerUserId, int offset, int limit)`
- `findOwnerFriendUserIds(String ownerUserId, int limit)`
- `findOwnersByFriendUserId(String friendUserId)`

### FriendRequestRepository

保留这些能力：

- `saveAll(List<FriendRequest> requests)`
- `updateFields(String fromUserId, String toUserId, Map<String, Object> fields)`
- `update(FriendRequest request)`
- `delete(String fromUserId, String toUserId)`
- `find(String fromUserId, String toUserId)`
- `findBothDirections(String userA, String userB)`
- `findIncoming(String toUserId, List<Integer> handleResults, int offset, int limit)`
- `findOutgoing(String fromUserId, List<Integer> handleResults, int offset, int limit)`
- `countUnhandled(String toUserId, long afterTs)`

## Deliberate Deviations From Go

以下设计不会照搬：

### 1. 不引入版本日志

Go 中 `conversation` 与 `friend` 仓储会同时写 version log。当前 Java 代码没有完整等价链路，本次不带入。

### 2. 不恢复会话最后消息快照

Go 中 conversation 侧仍承担最后消息摘要语义。Java 当前已经明确删除 `latestMsg/latestMsgSeq`，本次保持删除状态。

### 3. 不引入 user command 子集合逻辑

Go `user.go` 同时管理 `userCommands`。这与本次 6 个核心仓储无关，不纳入范围。

### 4. 不机械保留过宽接口

仅实现当前业务真正需要的方法，不把 Go 中历史兼容或边缘查询一并搬入。

## Migration Plan

### Step 1

删除当前 Java 中对应旧 repository 定义与实现：

- `UserConversationRepository`
- `UserConversationSyncPointRepository`
- `UserRepository`
- `FriendRepository`
- 以及与本次新拆分不一致的旧实现

### Step 2

新建 6 个 repository 接口与实现：

- `UserConversationRepository`
- `ConversationRangeRepository`
- `UserConversationSyncPointRepository`
- `UserRepository`
- `FriendshipRepository`
- `FriendRequestRepository`

### Step 3

统一改造 service 层调用，替换旧接口依赖。

### Step 4

补充/更新：

- Mongo 索引
- 关键 upsert / projection / batch update 测试
- 受影响模块编译验证

## Risks

### 1. 现有 service 层依赖旧接口较多

解决方式：先完成接口收口，再批量迁移业务层，避免新旧接口长时间并存。

### 2. 模型字段名与 Go 不完全一致

解决方式：只要求语义一致，不强制字段名完全一致。

### 3. 旧测试会大量失效

解决方式：优先修仓储与主业务编译，再按新接口重写关键测试，不为兼容旧测试留冗余接口。
