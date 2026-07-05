# postbox/ARCH.md — 消息接入与历史查询事实快照

> `MessageSender` Dubbo 入口 + ingress event 发布 + 历史查询 RPC。
> 改发送热路径前必读；权限链是当前 RTT 瓶颈。

## 1. 核心组件

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| `MessageSenderImpl` | `service/MessageSenderImpl.java:57` | `@DubboService`，统一消息发送入口 |
| `IngressMessagePublisher` | `service/IngressMessagePublisher.java:23` | 发布 ingress event（Protobuf bytes）到 `TopicNames.INGRESS` |
| `HistoryQueryService` | `service/HistoryQueryService.java:53` | 历史消息查询 |
| `BlockMessageQueryService` | `service/BlockMessageQueryService.java:60` | block 维度查询 + 附件候选 |
| `Postbox` | `Postbox.java:19` | 启动类，开 Kafka/Dubbo/Mongo Repos |

## 2. 发送热路径（`MessageSenderImpl.sendMessage`）

1. 生成 `serverMsgId`（`IdGenerator.generateMsgId()`，line 71）
2. 黑名单校验 Dubbo `friendRelationService.isBlocked`（line 109）
3. 用户 receiveOpt Dubbo `userServiceFacade.getReceiveOptions`（line 114）
4. 会话 receiveOpt Dubbo `conversationService.getReceiveOption`（line 123）
5. 发布 ingress event

⚠️ **三次同步 Dubbo 是发送 RTT 瓶颈**。ASSESSMENT P2-14 是合并项；新代码**不要**再加同步 Dubbo 调用。

## 3. Ingress 事件分区

- topic: `TopicNames.INGRESS`
- key: `ConversationIdUtil.buildQueueKey(convId)`，保证同会话消息进同一 Kafka 分区，保序
- payload: Protobuf bytes `Message`

## 4. 历史查询链路

- `getConversationMessages`（line 53）：当前**拉全部 block 内存排序再截 limit**，长会话 O(n)。ASSESSMENT P1-9 修复项 → 改 blockNo range 二分定位。
- `pullMessagesBySeqRange`（line 89）：block-range-bounded，gap repair 用，健康。
- `BlockMessageQueryService.findAttachmentCandidates`（line 60）：`content.regex` 全表扫，**不可扩展到百万量级**，ASSESSMENT P1-10 修复项 → 附件元数据表。
- 权限校验 `allow`（line 198）：`RpcException` 时 `return true`，**安全洞**，ASSESSMENT P1-11 修复项。

## 5. 边界

- postbox **不落消息真相**：历史块持久化在 `postmaster`（Mongo），postbox 只查询。
- postbox 不接客户端，客户端消息通过 `postoffice` → Dubbo 进入 `MessageSender`。
- HTTP 控制面不在此模块（在 `api-server`）。

## 6. 配置

`module-postbox.yml`：Mongo + Redis database 0 + Kafka `postbox-group`（concurrency 3, max-poll-records 100, ack-mode `manual_immediate`）+ 附件下载 token（secret/TTL）+ actuator。

⚠️ Kafka bootstrap 在 `common.yml` 是注释状态，独立部署需解开。

## 7. 改动评估 checklist

- [ ] 改 `MessageSender` 签名会同时影响 api-server、postmaster、NotificationSender
- [ ] 改 ingress event 结构需同步 `IngressEventListener`（postmaster）
- [ ] 改历史 block 切分逻辑需同步客户端按 seq range 拉取逻辑