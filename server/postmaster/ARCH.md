# postmaster/ARCH.md — 消息编排核心事实快照

> seq 分配 + 历史块持久化 + delivery event 发布 + 策略引擎。
> 详细会话/seq 设计见 `docs/ConversationArch.md` 和 `docs/SeqArch.md`（本模块 docs/ 目录）。
> 阻断性问题见 `server/docs/architecture/ASSESSMENT.md`。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `IngressEventListener` | `listener/IngressEventListener.java:51` | 消费 ingress topic，批处理 batchSize=500，按会话分桶保序 |
| `HistoryEventListener` | `listener/HistoryEventListener.java:23` | 消费 history topic，写入 `message_block` |
| `DeliveryEventListener` | `listener/DeliveryEventListener.java:40` | **postman 在本模块** ⚠️ 实际在 postman，见 postman/ARCH.md |
| `ConversationSeqService` | `service/ConversationSeqService.java:20` | seq 分配薄包装，委托 `ConversationSeqAllocator`（common-core） |
| `BlockHistoryPersistenceService` | `history/BlockHistoryPersistenceService.java:29` | 历史块 upsert + message id mapping 保存 |
| `DefaultMessagePolicyEngine` | `policy/DefaultMessagePolicyEngine.java:11` | 输出 `MessageRouteDecision`（persistHistory/notification/sendDelivery/needOfflinePush/senderSync） |
| `GroupFanoutPlanner` | `service/GroupFanoutPlanner.java:18` | 群扩散规划器（已实现但**未接通**，P0-2 修复项） |
| `UserMaxSeqPersistenceWriter` | `service/UserMaxSeqPersistenceWriter.java` | 用户 maxSeq 异步写 Mongo（单线程 drain + 2000 队列） |

## 2. Ingress 处理流程（`IngressEventListener.handle`）

1. 按 `ConversationIdUtil.buildQueueKey` 分桶，保证同会话进同 batch（line 51）
2. `DefaultMessagePolicyEngine.decide` 输出每条消息的策略位
3. 按 `storageMsgList` / `storageNotificationList` / `transientList` 分组
4. 每组申请一批 seq（`ConversationSeqAllocator.allocateBatch`）并按顺序绑 seq（line 153）
5. `createConversationIfNeeded` 同步 Dubbo 创建会话（line 173）——同步链路
6. publish history event + delivery event（per-message，未批量）

## 3. seq 分配（生产级，委托 common-core）

委托路径：`ConversationSeqService` → `ConversationSeqAllocator`（common-core）→ Redis Lua + Mongo `$inc`。详见 `common-core/ARCH.md` §3 与本模块 `docs/SeqArch.md`。

## 4. 历史块持久化

- 持久化点：`HistoryEventListener` 消费 `TopicNames.HISTORY`
- 块切分：`blockNo = ConversationSeqUtil.blockNo(seq)`，`message_block._id = {conversationId}:{blockNo}`
- 每条消息块内字段：`messages.{seq-offset}`
- 单独 `MessageIdMappingDoc` 保存 `serverMsgId ⇄ {convId, seq, blockNo, offset}` 映射

⚠️ 当前 `BlockHistoryPersistenceService.java:55` 在循环里**逐条 `mappingRepository.save`**，未用 `bulkOps`，是写吞吐瓶颈。ASSESSMENT P1-8 修复项。

## 5. 群扩散（**未闭环**）

- `GroupFanoutPlanner` 已实现 500 批的分区扩展
- **没有任何代码调用它**（grep 全仓零 caller）
- `DeliveryEventListener`（在 postman）对 `ChatType.GROUP` 直接 `return List.of()`，群消息只持久化不投递
- ASSESSMENT P0-2：在 `IngressEventListener.handleMessage` 调 `GroupFanoutPlanner`，普通群写扩散 publish N 个 keyed `DeliveryEvent`，超级群读扩散仅持久化

## 6. 已读回执（**残缺**）

`IngressEventListener.preProcessReadReceipts`（line 133-144）注释掉，`messageStateService` 未注入。ASSESSMENT P3-20 修复项。

## 7. 边界

- postmaster 不接客户端，不直接做在线投递（在线投递在 postman + postoffice）
- postmaster 通过 Dubbo 调用 `business` 的 `ConversationService`（创建会话、查询）
- 同步链路当前对 ingress 内已经有 1 次 `createConversationIfNeeded` Dubbo，新代码**不要**在 ingress 同步链路再加 Dubbo 调用

## 8. 改动评估 checklist

- [ ] 改 `MessageRouteDecision` 字段需同步 postman `DeliveryEventListener` 分支
- [ ] 改 seq 分配段大小需考虑单聊/群聊的热度差异（默认 50/100）
- [ ] 改 history block 切分 blockSize 必须同步客户端 gap repair 与历史查询
- [ ] 接通 `GroupFanoutPlanner` 必须同步更新 `DeliveryEventListener` 跳过群投递的 if 分支