# business/ARCH.md — 业务域事实快照

> 用户 / 好友 / 黑名单 / 群 / 会话 / 同步点。缓存统一经 common-core `CacheStore`。
> 详细同步设计见 `docs/CheeseIM-数据同步设计文档.md`。

## 1. 子包

| 包 | 职责 |
| --- | --- |
| `service/friend/` | 好友关系 + 申请 + 实时通知 |
| `service/conversation/` | 会话 CRUD + 增量同步（version-log）+ sync point |
| `service/group/` | 群成员查询 + 群发送权限聚合（群状态/成员/禁言/扩散类型） |
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
- 好友申请状态与双向好友关系在 cluster 模式下通过 MongoDB 事务原子提交；通知和缓存失效在提交后执行。all-in-one 单机 Mongo 默认关闭事务，可通过 `cheeseim.mongo.transactions-enabled` 覆盖。
- 黑名单单向：`{ownerUserId}:{blockUserId}`

### 2.3 群

- `Group.groupType`：`NORMAL_GROUP(2)` 写扩散 / `SUPER_GROUP(3)` 读扩散
- 群成员 `GroupMember._id = {groupId}:{userId}`，角色 owner/admin/member
- 群成员关系写入统一走 `GroupMembershipCommandService`：cluster profile 在一个 Mongo 事务内更新
  当前成员、`group_member_epoch` 与 `Group.membershipVersion`；存量群首次使用前建立版本 1 基线
- 禁言 `muteEndTime`，`isMuted()` 即时计算

## 3. 同步模型

详细见 `docs/CheeseIM-数据同步设计文档.md`，要点：

- **会话元数据同步**：服务端维护 `ownerUserId` 维度的 `ConversationVersionLog`，客户端用 cursor 做增量同步，超过 200 条回退全量（`ConversationServiceImpl.fillFullSync` line 469）
- **消息同步**：会话维度 seq/range/maxSeq，客户端按会话拉缺口消息
- **readSeq / deliveredSeq**：Redis 即写 + Mongo write-behind。writer 使用有界主/回退队列，
  上报 queued/inflight depth 与动态 oldest age；停机先等待当前批次最多 30 秒，再 drain 剩余队列。
  `ReadSeqPersistenceWriter` 按 userId 分桶并聚合最大 readSeq，`DeliverySeqPersistenceWriter`
  按用户/设备/会话聚合最大 deliveredSeq。Mongo 是恢复水位，Redis 是即时热状态。
- **发送权限聚合**：`MessageSendPermissionServiceImpl` 聚合 `FriendRelationService` / `UserInfoService` / `ConversationService` 本地调用，让 postbox 发送热路径从三次 Dubbo 收敛为一次；黑名单先短路，避免无谓的接收选项查询。
- **群发送权限聚合**：`GroupMessageSendPermissionServiceImpl` 一次群点查 + 一次 sender 批量成员查询，返回群状态、成员/禁言结论与 groupType；postbox 单 sender、postmaster 同群批 sender 共用该契约。
- 群发送权限使用 2 秒 Redis 快照缓存，并缓存不存在群/非成员拒绝结果；发送者 key 含
  membershipVersion，同群 key 使用共同 hash tag。成员 mutation 精确失效群元数据，旧版本 sender key 自然隔离。
- 会话当前态按 owner 存在 `conversation`；conversation 维度的非默认投递偏好单独写入
  `conversation_delivery_preference`。离线推送禁止回退扫描 owner 当前态集合。

## 4. 缓存规范

- 业务服务只通过 common-core `CacheStore` 创建带固定 key 前缀、值类型和 TTL 的 `CacheRegion`；不得直接注入 Redis 客户端。
- 缓存一律使用 Redis JSON，读取类型显式声明；不使用 Java 序列化、DefaultTyping 或本地 L1。
- 缓存删除放在 `@Transactional` 的 `afterCommit`，保持 cache-aside 时序。
- 用户资料/接收选项 TTL 为 5 分钟，好友关系为 10 分钟，会话视图为 12 小时；缓存未命中可接受，不做旧 key 双写。

## 5. 索引

- `UserConversationDoc`：`{ownerUserId:1, conversationId:1} unique`、`{ownerUserId:1, updatedAt:-1}`、`{ownerUserId:1, pinned:-1, updatedAt:-1}`（声明在 `@CompoundIndexes`）
- `UserConversationSyncPointDoc`：`{userId:1, conversationId:1} unique`
- `FriendshipDoc`：`{ownerUserId:1, friendUserId:1} unique` + 单字段索引

shard-friendly 但**未声明 sharding**，ASSESSMENT P1-7 修复项。

## 6. 已知缺陷

| 缺陷 | 位置 | 修复项 |
| --- | --- | --- |
| ~~`acceptFriendRequest` 非事务~~ | `FriendRelationServiceImpl.java:179` | **已修复 2026-07-13**：cluster 模式使用 MongoDB 事务，提交后再通知和失效缓存 |
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
- [ ] 改群成员/禁言/群状态写路径时同步失效 `group:send-permission`，在此之前不得放大 2 秒 TTL
