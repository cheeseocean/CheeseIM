# postmaster/ARCH.md — 消息编排核心事实快照

> seq 分配 + 历史块持久化 + delivery event 发布 + 策略引擎。
> 详细会话/seq 设计见 `docs/ConversationArch.md` 和 `docs/SeqArch.md`（本模块 docs/ 目录）。
> 阻断性问题见 `server/docs/architecture/ASSESSMENT.md`。
> 独立进程由 `application-postmaster.yml` 启动，唯一导入 `module-postmaster.yml`，Dubbo application 名为 `cheese-im-postmaster`（20881），Kafka consumer group 为 `postmaster-ingress-group`。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `IngressEventListener` | `listener/IngressEventListener.java:51` | 消费 ingress topic，批处理 batchSize=500，按会话分桶保序 |
| `HistoryEventListener` | `listener/HistoryEventListener.java:23` | 消费 history topic，写入 `message_block` |
| `DeliveryEventListener` | `listener/DeliveryEventListener.java:40` | **postman 在本模块** ⚠️ 实际在 postman，见 postman/ARCH.md |
| `ConversationSeqService` | `service/ConversationSeqService.java:20` | seq 分配薄包装，委托 `ConversationSeqAllocator`（common-core） |
| `BlockHistoryPersistenceService` | `history/BlockHistoryPersistenceService.java:25` | 历史块 + message id mapping 双 unordered bulk upsert（2026-07-08 P1-8 修复） |
| `DefaultMessagePolicyEngine` | `policy/DefaultMessagePolicyEngine.java:11` | 输出 `MessageRouteDecision`（persistHistory/notification/sendDelivery/needOfflinePush/senderSync） |
| `GroupFanoutPlanner` | `service/GroupFanoutPlanner.java` | 群扩散规划器：成员切片 + delivery key 生成（`g:{groupId}:{memberId}`）；2026-07-06 P0-2 修复接通 |
| `UserMaxSeqPersistenceWriter` | `service/UserMaxSeqPersistenceWriter.java` | 用户 maxSeq 异步写 Mongo（按 userId 分桶多线程 drain + 单桶聚合最大水位，workerCount/queueCapacity 可配） |
| `MessageMutationServiceImpl` | `mutation/MessageMutationServiceImpl.java` | 按 serverMsgId 点查原消息，校验发送者/会话/两分钟窗口，幂等 upsert `message_mutation(REVOKED)`；按 `createdAt + mutationId` 复合游标提供离线增量同步并校验会话成员权限 |

## 2. Ingress 处理流程（`IngressEventListener.handle`）

1. 按 `ConversationIdUtil.buildQueueKey` 分桶，保证同会话进同 batch（line 51）
2. `DefaultMessagePolicyEngine.decide` 输出每条消息的策略位
3. 按 `storageMsgList` / `storageNotificationList` / `transientList` 分组
4. 每组申请一批 seq（`ConversationSeqAllocator.allocateBatch`）并按顺序绑 seq（line 153）
5. `createConversationIfNeeded` 同步 Dubbo 创建会话（line 173）——同步链路
6. publish history event（per-batch）
7. delivery 发布分两路（2026-07-13 已接入 `QueueAdapter.sendBatch`）：
   - 群聊（`ChatType.GROUP`）走 `fanoutGroupDelivery`：根据 `GroupMembershipFacade.loadGroupType` 返回的 `GroupTypeEnum` 选择
     - `NORMAL_GROUP`：同一 ingress 批次按 groupId 聚合，仅查询一次群类型/成员 → `GroupFanoutPlanner.partition` 切片 →
       `MessageProducer.publishForTargets` 复用 protobuf 模板并批量发送成员级 keyed DeliveryEvent（**写扩散**）
     - `SUPER_GROUP`：不投递，仅持久化（**读扩散**），客户端按 seq 拉取
     - `null`（群不存在/Dubbo 异常）：按 NORMAL_GROUP 兜底，避免投递丢失
   - 非群聊（单聊/通知）聚合为 `MessageProducer.publishBatch`，同 key 顺序不变

## 3. seq 分配（生产级，委托 common-core）

委托路径：`ConversationSeqService` → `ConversationSeqAllocator`（common-core）→ Redis Lua + Mongo `$inc`。详见 `common-core/ARCH.md` §3 与本模块 `docs/SeqArch.md`。

## 4. 历史块持久化

- 持久化点：`HistoryEventListener` 消费 `TopicNames.HISTORY`
- 块切分：`blockNo = ConversationSeqUtil.blockNo(seq)`，`message_block._id = {conversationId}:{blockNo}`
- 每条消息块内字段：`messages.{seq-offset}`
- 单独 `MessageIdMappingDoc` 保存 `serverMsgId ⇄ {convId, seq, blockNo, offset}` 映射

**批量写（2026-07-08 P1-8 修复）**：一个 `HistoryEvent` 内先把全部 id mapping 合入一个 unordered `bulkOps` upsert（`_id = {convId}:{clientMsgId}` 幂等），再把按 blockNo 分桶后的块更新合入第二个 unordered `bulkOps` upsert；不再循环逐条 `save`/`upsert`。原 `MessageIdMappingRepository` 已删除（无其它使用点）。

**附件元数据（2026-07-08 P1-10）**：`ContentType.hasAttachment()`（IMAGE/VOICE/VIDEO/FILE）的消息，从 content JSON 提取 `attachmentId` 后批量 upsert `attachment_metadata`（`_id = attachmentId`，含 conversationId/serverMsgId/seq/senderId/contentType/sendTime）；content 非 JSON 或缺 `attachmentId` 静默跳过。postbox 附件鉴权按 `_id` 点查（见 `postbox/ARCH.md` §4）。

## 5. 群扩散（**已闭环 2026-07-06**）

- `GroupFanoutPlanner.partition(memberIds)` 把成员按 `cheeseim.delivery.group-fanout.batch-size`（默认 500）切片
- `GroupFanoutPlanner.deliveryKey(groupId, memberId)` 产出 `g:{groupId}:{memberId}` 形式 partition key，保证同成员在同一群内消息落同一 Kafka 分区、按序投递
- `IngressEventListener.fanoutGroupDelivery` 调用：
  - `GroupMembershipFacade.loadGroupType(groupId)` → `GroupTypeEnum.NORMAL_GROUP` / `SUPER_GROUP` / `null`
  - NORMAL_GROUP：`loadGroupMembers` → 切片 → 每成员 `MessageProducer.publishForMember` 替换 `receiverId` 后投递
  - SUPER_GROUP：不投递，仅持久化
  - null：按 NORMAL_GROUP 兜底
- `MessageProducer.publishForMember` 通过 protobuf builder 替换 `receiverId`，避免 Java 侧深拷贝 `Message`
- postman `DeliveryEventListener.resolveTargets` 不再对 `ChatType.GROUP` 跳过——写扩散后每条 DeliveryEvent 已带 `receiverId`，直接按 receiverId 投递即可
- 同步链路上每个群消息最多加 2 次 Dubbo：`loadGroupType` + `loadGroupMembers`（新会话已有后者用于 createConversation，复用可优化，留作 P2-14 合并 Dubbo 的后续工作）

## 6. 控制事件边界

旧 `IngressEventListener.preProcessReadReceipts` 仍是普通消息 `READ_RECEIPT` 遗留旁路，不能作为已读实现。typed `CHAT_READ` 统一由 business 的 `ReadStateService` 处理；`CHAT_REVOKE` 由本模块的 `MessageMutationService` 处理；`CHAT_TYPING` 由 `TypingStateServiceImpl` 校验会话成员后以 3-5 秒短 TTL 通知在线目标。三者均追加 `conversation_control_event` 供 postman 补偿和客户端 cursor 补齐，输入中不写消息 history、ingress 或 seq。

## 7. 边界

- postmaster 不接客户端，不直接做在线投递（在线投递在 postman + postoffice）
- postmaster 通过 Dubbo 调用 `business` 的 `ConversationService`（创建会话、查询）
- 同步链路当前对 ingress 内已经有 1 次 `createConversationIfNeeded` Dubbo，新代码**不要**在 ingress 同步链路再加 Dubbo 调用

## 8. 改动评估 checklist

- [ ] 改 `MessageRouteDecision` 字段需同步 postman `DeliveryEventListener` 分支
- [ ] 改 seq 分配段大小需考虑单聊/群聊的热度差异（默认 50/100）
- [ ] 改 history block 切分 blockSize 必须同步客户端 gap repair 与历史查询
- [ ] 接通 `GroupFanoutPlanner` 必须同步更新 `DeliveryEventListener` 跳过群投递的 if 分支
