# postmaster/ARCH.md — 消息编排核心事实快照

> seq 分配 + 历史块持久化 + delivery event 发布 + 策略引擎。
> 详细会话/seq 设计见 `docs/ConversationArch.md` 和 `docs/SeqArch.md`（本模块 docs/ 目录）。
> 阻断性问题见 `server/docs/architecture/ASSESSMENT.md`。
> 独立进程由 `application-postmaster.yml` 启动，唯一导入 `module-postmaster.yml`，Dubbo application 名为 `cheese-im-postmaster`（20881）；稳定队列 group 为 `postmaster-ingress` 与 `postmaster-history`。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `IngressEventListener` | `listener/IngressEventListener.java:51` | 消费 ingress topic，批处理 batchSize=500，按会话分桶保序 |
| `IngressMessageFingerprint` | `listener/IngressMessageFingerprint.java` | 对 ingress 载荷做确定性指纹，seq 不参与重放冲突判定 |
| `HistoryEventListener` | `listener/HistoryEventListener.java:23` | 消费 history topic，写入 `message_block` |
| `DeliveryEventListener` | `listener/DeliveryEventListener.java:40` | **postman 在本模块** ⚠️ 实际在 postman，见 postman/ARCH.md |
| `ConversationSeqService` | `service/ConversationSeqService.java:20` | seq 分配薄包装，委托 `ConversationSeqAllocator`（common-core） |
| `BlockHistoryPersistenceService` | `history/BlockHistoryPersistenceService.java:25` | 历史块 + message id mapping 双 unordered bulk upsert（2026-07-08 P1-8 修复） |
| `DefaultMessagePolicyEngine` | `policy/DefaultMessagePolicyEngine.java:11` | 输出 `MessageRouteDecision`（persistHistory/notification/sendDelivery/needOfflinePush/senderSync） |
| `GroupFanoutPlanner` | `service/GroupFanoutPlanner.java` | 群扩散规划器：成员切片 + delivery key 生成（`g:{groupId}:{memberId}`）；2026-07-06 P0-2 修复接通 |
| `UserMaxSeqPersistenceWriter` | `service/UserMaxSeqPersistenceWriter.java` | 用户 maxSeq 异步写 Mongo（按 userId 分桶多线程 drain + 单桶聚合最大水位，workerCount/queueCapacity 可配） |
| `MessageMutationServiceImpl` | `mutation/MessageMutationServiceImpl.java` | 按 serverMsgId 点查原消息，校验发送者/会话/两分钟窗口，幂等 upsert `message_mutation(REVOKED)`；按 `createdAt + mutationId` 复合游标提供离线增量同步并校验会话成员权限 |

## 2. Ingress 处理流程（`IngressEventListener.handle`）

1. 按 `ConversationIdUtil.buildQueueKey` 分桶，保证同会话进同 batch；
2. 以稳定 `serverMsgId` claim ingress inbox：已完成消息跳过、冲突消息拒绝、活跃租约等待恢复；
3. 群消息按 `(groupId, unique senderIds)` 一次防御性权限查询；拒绝非成员/禁言/异常群，并复用返回的 groupType；
4. `DefaultMessagePolicyEngine.decide` 输出每条消息的策略位；
5. 按 `storageMsgList` / `storageNotificationList` / `transientList` 分组；
6. 仅为 inbox 尚未绑定 seq 的持久化消息批量申请 seq，并在任何外部副作用前固定；重放直接复用原 seq；
7. 普通群首会话创建交给独立 fanout worker；超级群不枚举全量成员创建用户会话；
8. publish history event（per-batch）；
9. delivery 发布分两路（2026-07-13 已接入 `QueueAdapter.sendBatch`）：
   - 群聊（`ChatType.GROUP`）根据权限聚合返回的 `GroupTypeEnum` 选择
     - `NORMAL_GROUP`：ingress 只发布按 groupId 分区的 `GROUP_FANOUT` 紧凑任务；独立 worker 查询成员、
       创建首会话、切片并调用 `MessageProducer.publishForTargets`（**写扩散**）
     - `SUPER_GROUP`：不投递，仅持久化（**读扩散**），客户端按 seq 拉取
     - `null`（群不存在/Dubbo 异常）：按 NORMAL_GROUP 兜底，避免投递丢失
   - 非群聊（单聊/通知）聚合为 `MessageProducer.publishBatch`，同 key 顺序不变
10. HISTORY/DELIVERY 均取得 broker ACK 后标记 inbox `COMPLETED`；明确异常释放租约但保留 seq。

Redis 实现按单消息单 key Lua 原子迁移，并使用 pipeline 合并批量网络往返；RocksDB 实现保持相同接口。进程在副作用完成后、inbox 完成前崩溃时仍可能重复发布相同 `serverMsgId + seq`，但不会再分配第二个 seq；history Mongo upsert 和 postoffice delivery dedup 负责承受下游重放。

`UserMaxSeqPersistenceWriter` 的 drain batch 会先按用户-会话取最大值，再通过
`UserConversationSyncPointRepository.updateMaxSeqBatch` 执行一次 Mongo unordered bulk upsert；
Mongo 使用 `$max`，多副本与 fallback 乱序不会回退水位。writer 上报 queued/inflight depth 与
动态 oldest age；停机先等待当前批次最多 30 秒，再 drain 剩余队列，超时/最终失败进入固定结果指标。

## 3. seq 分配（生产级，委托 common-core）

委托路径：`ConversationSeqService` → `ConversationSeqAllocator`（common-core）→ Redis Lua + Mongo `$inc`。详见 `common-core/ARCH.md` §3 与本模块 `docs/SeqArch.md`。

## 4. 历史块持久化

- 持久化点：`HistoryEventListener` 消费 `TopicNames.HISTORY`
- 块切分：`blockNo = ConversationSeqUtil.blockNo(seq)`，`message_block._id = {conversationId}:{blockNo}`
- 每条消息块内字段：`messages.{seq-offset}`
- 单独 `MessageIdMappingDoc` 保存 `serverMsgId ⇄ {convId, seq, blockNo, offset}` 映射

**批量写（2026-07-08 P1-8 修复）**：一个 `HistoryEvent` 内先把全部 id mapping 合入一个 unordered `bulkOps` upsert（`_id = {convId}:{clientMsgId}` 幂等），再把按 blockNo 分桶后的块更新合入第二个 unordered `bulkOps` upsert；不再循环逐条 `save`/`upsert`。原 `MessageIdMappingRepository` 已删除（无其它使用点）。

**附件元数据（2026-07-08 P1-10）**：`ContentType.hasAttachment()`（IMAGE/VOICE/VIDEO/FILE）的消息，从 content JSON 提取 `attachmentId` 后批量 upsert `attachment_metadata`（`_id = attachmentId`，含 conversationId/serverMsgId/seq/senderId/contentType/sendTime）；content 非 JSON 或缺 `attachmentId` 静默跳过。postbox 附件鉴权按 `_id` 点查（见 `postbox/ARCH.md` §4）。

postmaster 的历史持久化与 mutation 服务只依赖 `MessageHistoryRepository` 的纯 model。Mongo `*Doc`
仅留在独立 `storage-history` adapter，撤回业务不再构造或接收持久化对象。

## 5. 群扩散（**已闭环 2026-07-06**）

- `GroupFanoutPlanner.partition(memberIds)` 把成员按 `cheeseim.delivery.group-fanout.batch-size`（默认 200）切片
- ingress 按默认 50 条 / 512 KiB 估算拆分 job，并在发布前执行 768 KiB 实际 wire hard limit
- `GroupFanoutPlanner.deliveryKey(groupId, memberId)` 产出 `g:{groupId}:{memberId}` 形式 partition key，保证同成员在同一群内消息落同一 Kafka 分区、按序投递
- `IngressEventListener` 对 NORMAL_GROUP 发布 `GroupFanoutEvent`，不再查询成员或执行 O(N) 水位更新
- `GroupFanoutEventListener` 按事件携带的 `membershipVersion` 从 `group_member_epoch` 做
  `(joinedVersion,userId,epochId)` keyset 分页 → 切片 → 每成员重写 `receiverId` 后批量投递；
  不再用 joinTime/消息时间近似快照
- SUPER_GROUP 不发布 fanout job，仅持久化
- `GroupFanoutPlanner.fanoutKey(groupId)` 保证同群任务有序；worker concurrency 可独立配置
- `MessageProducer.publishForMember` 通过 protobuf builder 替换 `receiverId`，避免 Java 侧深拷贝 `Message`
- postman `DeliveryEventListener.resolveTargets` 不再对 `ChatType.GROUP` 跳过——写扩散后每条 DeliveryEvent 已带 `receiverId`，直接按 receiverId 投递即可
- ingress 同步链路不再调用 `loadGroupMembers`；成员枚举故障只阻塞 fanout topic，不阻塞单聊 ingress
- 超过一页的群使用 `group_fanout_job` lease + generation fencing 保存三元页游标，broker ACK 后 checkpoint；
  小群不落 job 状态，重试最多重放默认 200 人的一页

## 6. 控制事件边界

普通消息 `READ_RECEIPT` 遗留旁路已删除，网关明确拒绝该内容类型，postmaster 对遗留队列数据防御性丢弃；typed `CHAT_READ` 是唯一已读入口，统一由 business 的 `ReadStateService` 处理。`CHAT_REVOKE` 由本模块的 `MessageMutationService` 处理，已读与撤回以业务稳定 ID（read 高水位 / mutationId）追加 `conversation_control_event`，repository 再生成确定性分片 ID 并幂等 upsert；中途失败重入只补缺失分片，供 postman 补偿和客户端 cursor 补齐。`CHAT_TYPING` 是纯瞬时在线信号：`TypingStateServiceImpl` 校验会话成员后，通过 Redis `SET NX EX` 按 sender + conversation 做 3-5 秒原子节流/TTL，只尽力通知在线目标，不写 Mongo outbox、message history、ingress 或 seq。普通群成员数默认上限 100（可配置），超级群禁用输入中广播。

## 7. 边界

- postmaster 不接客户端，不直接做在线投递（在线投递在 postman + postoffice）
- postmaster 通过 Dubbo 调用 `business` 的 `ConversationService`（创建会话、查询）
- 同步链路当前对 ingress 内已经有 1 次 `createConversationIfNeeded` Dubbo，新代码**不要**在 ingress 同步链路再加 Dubbo 调用

## 8. 改动评估 checklist

- [ ] 改 ingress inbox TTL/租约需同时评估 Kafka retention、`max.poll.interval.ms` 与最长群扩散耗时
- [ ] inbox 必须在全部下游 broker ACK 后完成；不得在 seq 分配或 history 发布前提前完成
- [ ] 改 `MessageRouteDecision` 字段需同步 postman `DeliveryEventListener` 分支
- [ ] 改 seq 分配段大小需考虑单聊/群聊的热度差异（默认 50/100）
- [ ] 改 history block 切分 blockSize 必须同步客户端 gap repair 与历史查询
- [ ] 接通 `GroupFanoutPlanner` 必须同步更新 `DeliveryEventListener` 跳过群投递的 if 分支
