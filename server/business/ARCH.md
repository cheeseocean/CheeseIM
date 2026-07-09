# business/ARCH.md — 业务域事实快照

> 用户 / 好友 / 黑名单 / 群 / 会话 / 同步点。JetCache 缓存规范在 common-core。
> 详细同步设计见 `docs/CheeseIM-数据同步设计文档.md`。

## 1. 子包

| 包 | 职责 |
| --- | --- |
| `service/friend/` | 好友关系 + 申请 + 实时通知 |
| `service/conversation/` | 会话 CRUD + 增量同步（version-log）+ sync point |
| `service/group/` | 群成员查询（最小集，群管理入群申请待补） |
| `service/user/` | 用户信息 + 全局 receiveOpt |
| `service/permission/` | 发送权限聚合：黑名单 + 用户 receiveOpt + 会话 receiveOpt 一次性返回给 postbox |
| `service/blacklist/` | 黑名单 |
| `domain/` | 与 common-api `business/domain` 镜像的业务方法 |

## 2. 数据模型事实

### 2.1 会话三件套

| 实体 | _id | 用途 |
| --- | --- | --- |
| `UserConversation` | `{ownerUserId}:{conversationId}` | 用户私有会话视图（置顶/免打扰/未读）|
| `ConversationSequence` | `{conversationId}` | 全局会话 seq 锚点 |
| `ConversationVersionLog` | 自增 | 用户会话列表变更日志，用于增量同步 |

`ConversationVersionLog` 当前**无 TTL**，长期增长。ASSESSMENT P1-12 修复项。

### 2.2 好友/黑名单

- 好友关系**双向写扩散**：`acceptFriendRequest` 写 `left` 和 `right` 两条（`FriendRelationServiceImpl.java:179`）
- ⚠️ 双向写 + 请求更新 + 缓存失效**无 `@Transactional`**，部分失败留单边好友。ASSESSMENT §3.2 修复项。
- 黑名单单向：`{ownerUserId}:{blockUserId}`

### 2.3 群

- `Group.groupType`：`NORMAL_GROUP(2)` 写扩散 / `SUPER_GROUP(3)` 读扩散
- 群成员 `GroupMember._id = {groupId}:{userId}`，角色 owner/admin/member
- 禁言 `muteEndTime`，`isMuted()` 即时计算

## 3. 同步模型

详细见 `docs/CheeseIM-数据同步设计文档.md`，要点：

- **会话元数据同步**：服务端维护 `ownerUserId` 维度的 `ConversationVersionLog`，客户端用 cursor 做增量同步，超过 200 条回退全量（`ConversationServiceImpl.fillFullSync` line 469）
- **消息同步**：会话维度 seq/range/maxSeq，客户端按会话拉缺口消息
- **readSeq**：Redis 即写（`ackReadSeq` line 144）+ Mongo 写 behind（`ReadSeqPersistenceWriter` 按 userId 分桶多线程 drain，单桶聚合最大 readSeq，workerCount/queueCapacity 可配，Redis 仍权威）
- **发送权限聚合**：`MessageSendPermissionServiceImpl` 聚合 `FriendRelationService` / `UserInfoService` / `ConversationService` 本地调用，让 postbox 发送热路径从三次 Dubbo 收敛为一次；黑名单先短路，避免无谓的接收选项查询。

## 4. JetCache 用法

- `FriendRelationServiceImpl`：friendshipCache / friendListCache (`BOTH`)，incoming/outgoing request (`REMOTE`)
- `ConversationServiceImpl`：6 个 cache（detail/ids/ids_hash/pinned/not_notify/not_receive），全 `REMOTE`，本地 5min / 远端 12h
- 缓存删除放 `@Transactional` 的 `afterCommit`（`ConversationServiceImpl.java:553-575`），cache-aside 规范
- `UserServiceImpl`：userInfo 用 `BOTH`，receiveOpt 用 `REMOTE`

⚠️ L1 无跨节点失效广播（common-core 通用缺陷），新增 cache 优先 `REMOTE`。

## 5. 索引

- `UserConversationDoc`：`{ownerUserId:1, conversationId:1} unique`、`{ownerUserId:1, updatedAt:-1}`、`{ownerUserId:1, pinned:-1, updatedAt:-1}`（声明在 `@CompoundIndexes`）
- `UserConversationSyncPointDoc`：`{userId:1, conversationId:1} unique`
- `FriendshipDoc`：`{ownerUserId:1, friendUserId:1} unique` + 单字段索引

shard-friendly 但**未声明 sharding**，ASSESSMENT P1-7 修复项。

## 6. 已知缺陷

| 缺陷 | 位置 | 修复项 |
| --- | --- | --- |
| `acceptFriendRequest` 非事务 | `FriendRelationServiceImpl.java:179` | ASSESSMENT §3.2 |
| `ConversationVersionLog` 无 TTL | `ConversationVersionLogDoc.java:16` | P1-12 |
| ~~`ReadSeqPersistenceWriter` 单线程~~ | ~~`ReadSeqPersistenceWriter.java:31`~~ | **已修复 2026-07-09**：按 userId hash 分桶多线程 drain |
| `getOfflinePushUserIds` Mongo 全扫 | | 索引补全 |

## 7. 边界

- business **不持有消息历史**（在 postmaster 落 Mongo），**不操作连接**（在 postoffice）
- 通过 Dubbo 被 postoffice/api-server/postbox/postmaster 调用
- 不下沉 HTTP DTO（DTO 只在 api-server）

## 8. 改动评估 checklist

- [ ] 改 `UserConversation` 字段需同步 sync 协议与客户端缓存
- [ ] 改 conversationId 形态会破坏所有 Mongo 文档 _id，禁止
- [ ] 改 sync point 字段需同步 sdks/go 拉取协议
- [ ] 新增 cache 默认 `REMOTE`，验证写后清除时序
