# postbox/ARCH.md — 消息接入与历史查询事实快照

> `MessageSender` Dubbo 入口 + ingress event 发布 + 历史查询 RPC。
> 改发送热路径前必读；权限链是当前 RTT 瓶颈。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `MessageSenderImpl` | `service/MessageSenderImpl.java:57` | `@DubboService`，统一消息发送入口 |
| `MessageSendFingerprint` | `service/MessageSendFingerprint.java` | 归一化客户端载荷与发送身份的 SHA-256 指纹 |
| `IngressMessagePublisher` | `service/IngressMessagePublisher.java:23` | 发布 ingress event（Protobuf bytes）到 `TopicNames.INGRESS` |
| `HistoryQueryService` | `service/HistoryQueryService.java:53` | 历史消息查询 |
| `BlockMessageQueryService` | `service/BlockMessageQueryService.java:23` | block 维度查询 + 附件候选（`attachment_metadata` 点查，2026-07-08 P1-10） |
| `Postbox` | `Postbox.java:19` | 启动类，开 Kafka/Dubbo/Mongo Repos |

## 2. 发送热路径（`MessageSenderImpl.sendMessage`）

1. 归一化消息选项，以 `(senderId, conversationId, clientMsgId)` claim 发送 inbox；
2. 已确认请求直接返回首次 `serverMsgId/acceptedAt`；并发请求返回处理中；载荷变化返回冲突；
3. 首次执行调用一次权限聚合 Dubbo：单聊返回黑名单/receiveOpt，群聊返回群状态/成员/禁言/groupType；
4. 复用 inbox 固定的 `serverMsgId/createTime` 发布 ingress event；
5. 取得 broker ACK 后将 inbox 标记为 `ACCEPTED`，明确发布失败则释放短租约。

拒绝响应使用稳定 `ErrorCode` 区分参数非法、幂等冲突、处理中、群不存在、群不可用、非成员和禁言；postoffice 不再统一压成 `1004`。

~~三次同步 Dubbo 是发送 RTT 瓶颈。~~ **已修复 2026-07-09**：发送热路径不再分别调用 `FriendRelationService` / `UserInfoService` / `ConversationService`，统一由 business 聚合。

发送 inbox 已于 2026-07-19 接入。它保证客户端重试和并发请求使用稳定消息身份，但 broker ACK 后、inbox 确认前仍可能重发同一 `serverMsgId`；postmaster 必须继续按稳定 ID 建立 ingress inbox，才能关闭端到端重复分配 seq 的窗口。

## 3. Ingress 事件分区

- topic: `TopicNames.INGRESS`
- key: `ConversationIdUtil.buildQueueKey(convId)`，保证同会话消息进同一 Kafka 分区，保序
- payload: Protobuf bytes `Message`

## 4. 历史查询链路

- `getConversationMessages`：已改为先查 latest `blockNo`，再按 `conversationId + blockNo range` 窗口读取并按 seq 倒序裁剪 limit；`limit` 最大 200，最近页最多扫描 16 个窗口，避免长会话全扫和恶意大分页。
- `pullMessagesBySeqRange`（line 89）：block-range-bounded，gap repair 用，健康。
- `BlockMessageQueryService.findAttachmentCandidate`：按 `attachment_metadata._id = attachmentId` 点查后 `findSlot` 还原内容（2026-07-08 P1-10 修复，替代原 `message_id_mapping` 上的 `content.regex` 全扫——该 regex 查的 `content` 字段在 mapping 文档上并不存在，属死查询）。元数据由 postmaster `BlockHistoryPersistenceService` 对 `ContentType.hasAttachment()` 消息随历史持久化批量写入。
- `BlockMessageQueryService.findSlot`：按 `BlockIndexUtil.docId` 点查 `_id`（修复原 `((seq-1)/100)+1` 与 `BlockIndexUtil.blockNo` 差一导致永远查错块的 bug）。
- 历史页与 seq-range gap repair 会按本批 serverMsgId 一次查询 `message_mutation`，将 `REVOKED` overlay 合并为 tombstone；原 `message_block` 不物理改写。
- postbox 只依赖 `MessageHistoryRepository` 与 `history.model`；Mongo `*Doc` 和 BSON Binary 在 adapter
  边界完成转换，不进入查询服务。Mongo 实现已物理归属 `storage-history`，由自动配置装配。
- 权限校验 `allow`：provider 缺失、RPC 异常、非预期返回时默认拒绝；仅在 30s 本地权限缓存未过期时兜底放行。

## 5. 边界

- postbox **不落消息真相**：历史块持久化在 `postmaster`（Mongo），postbox 只查询。
- postbox 不 import `common-core.history.document` 或 `org.bson`；新增历史字段先进入 port model，再由 adapter 映射。
- postbox 不接客户端，客户端消息通过 `postoffice` → Dubbo 进入 `MessageSender`。
- HTTP 控制面不在此模块（在 `api-server`）。

## 6. 配置

`module-postbox.yml`：Mongo + Redis database 0 + Kafka `postbox-group`（concurrency 3, max-poll-records 100, ack-mode `manual_immediate`）+ actuator。

- `cheeseim.message-send-inbox.ttl-seconds`：首次 ACK 保留期，默认 7 天；
- `cheeseim.message-send-inbox.lease-seconds`：发送执行租约，默认 30 秒；
- 独立 cluster 部署的 broker 地址由 `application-cluster.yml` 与 `KAFKA_BOOTSTRAP_SERVERS` 提供，不应修改 `common.yml` 写死地址。

## 7. 改动评估 checklist

- [ ] 改 `MessageSender` 签名会同时影响 api-server、postmaster、NotificationSender
- [ ] 改 `MessageSendPermissionService` 返回语义需同步 business 聚合实现与 postbox 判定逻辑
- [ ] 改 `GroupMessageSendPermissionService` 需同步 business provider、postbox 首次校验和 postmaster 防御校验
- [ ] 改 ingress event 结构需同步 `IngressEventListener`（postmaster）
- [ ] 改 inbox key、指纹字段或 TTL 需评估客户端最大重试窗口和存量 key 兼容性
- [ ] 改历史 block 切分逻辑需同步客户端按 seq range 拉取逻辑
