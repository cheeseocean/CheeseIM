# CheeseIM 数据同步设计文档

生成时间：2026-05-21

## 目录

1. [概述](#1-概述)
2. [整体架构](#2-整体架构)
3. [核心数据结构](#3-核心数据结构)
4. [消息同步流程](#4-消息同步流程)
5. [会话同步流程](#5-会话同步流程)
6. [实时同步与 Gap Repair](#6-实时同步与-gap-repair)
7. [未读数与已读处理](#7-未读数与已读处理)
8. [本地持久化与多用户隔离](#8-本地持久化与多用户隔离)
9. [API 接口列表](#9-api-接口列表)
10. [关键设计决策](#10-关键设计决策)
11. [与 OpenIM 设计的对应关系](#11-与-openim-设计的对应关系)
12. [后续演进](#12-后续演进)

---

## 1. 概述

### 1.1 设计目标

CheeseIM 的数据同步设计参考 OpenIM 的消息 seq、会话版本同步和 SDK 本地状态管理思路，但按当前 Java Server、Go SDK、CheeseBox TUI 的工程结构落地。

核心目标：

- **消息可靠同步**：消息以 conversation 维度的递增 `seq` 为最终一致依据，客户端通过 `maxSeq`、`syncedMaxSeq` 和 range pull 修复缺口。
- **会话元数据增量同步**：服务端维护用户维度的 `ConversationVersionLog`，客户端使用 `ConversationSyncCursor` 增量拉取会话 insert/update/delete。
- **实时推送低延迟**：TCP/WS 只承担实时到达和唤醒职责，不替代历史同步。客户端收到实时消息后仍按 seq 检查连续性。
- **用户维度隔离**：会话列表、会话 cursor、本地消息缓存都以当前登录用户为隔离边界，避免多账号污染。
- **SDK 中心化**：`sdks/go` 提供通用 IM client 能力，CheeseBox 只做 TUI 集成和本地展示状态维护。

### 1.2 同步对象

| 对象 | 维度 | 可靠性依据 | 同步方式 |
|------|------|------------|----------|
| 消息 | conversation | `seq`、`maxSeq`、range pull | 拉取指定 seq range，实时事件触发 gap repair |
| 会话元数据 | user | `versionId`、`version`、`idHash` | 用户维度版本日志增量同步 |
| 已读状态 | user + conversation | `readSeq`、`maxSeq` | snapshot 初始化，ack read 写回 |
| 本地展示状态 | user + conversation | 本地 store + server cursor | CheeseBox user-scoped persisted store |

---

## 2. 整体架构

### 2.1 模块边界

```
┌─────────────────────────────────────────────────────────────────────┐
│                           CheeseIM Server                            │
│                                                                     │
│  ┌──────────────┐      ┌──────────────────┐      ┌──────────────┐  │
│  │ api-server   │─────▶│ business service │─────▶│ common-core  │  │
│  │ HTTP Facade  │      │ Conversation     │      │ Mongo/Cache  │  │
│  └──────┬───────┘      └────────┬─────────┘      └──────┬───────┘  │
│         │                       │                       │          │
│         │                       ▼                       ▼          │
│         │              UserConversation          Conversation       │
│         │              VersionLog                Sequence/Message   │
└─────────┼───────────────────────────────────────────────────────────┘
          │ HTTP + TCP/WS
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                            Go SDK                                   │
│                                                                     │
│  auth.Service ── tcpim.Client ── Event                              │
│       │                                                             │
│       ├── social.RosterService ── conversations / friends / groups  │
│       │                                                             │
│       └── sync.Service ── serverMaxSeqs / syncedMaxSeqs / cursor    │
└─────────┬───────────────────────────────────────────────────────────┘
          │ SDK API
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                            CheeseBox                                │
│                                                                     │
│  RootModel ── AppStore ── PersistedStore(user namespace)            │
│      │                                                              │
│      └── sync.Syncer ── MessageStore + SDKPuller                    │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 职责划分

| 层 | 职责 | 不承担的职责 |
|----|------|--------------|
| `server/business` | 会话元数据版本日志、删除/隐藏语义、会话增量同步 | HTTP response 格式 |
| `server/api-server` | SessionPrincipal 绑定、HTTP 参数解析、response 封装 | 业务状态判断 |
| `server/common-core` | Mongo repository、缓存、seq 存储适配 | 业务编排 |
| `sdks/go` | 登录、HTTP/TCP 适配、同步状态、对外 SDK API | TUI 展示状态 |
| `apps/CheeseBox` | 用户本地持久化、UI 状态、realtime gap repair 集成 | 服务端真相判断 |

---

## 3. 核心数据结构

### 3.1 服务端会话版本日志

`ConversationVersionLog` 是会话元数据增量同步的核心日志。

```java
class ConversationVersionLog {
    String id;
    String ownerUserId;
    String versionId;
    long version;
    String conversationId;
    ConversationVersionOperation operation;
    long createdAt;
}
```

字段语义：

- `ownerUserId`：版本日志归属用户。会话同步是用户维度，不是全局维度。
- `versionId`：当前版本流 ID。版本流重建时变化，客户端应转全量同步。
- `version`：用户维度递增版本号。
- `operation`：`INSERT`、`UPDATE`、`DELETE`。
- `conversationId`：发生变更的会话。

`ConversationVersionLogRepository.append(...)` 通过用户最新日志计算下一个版本号；如果用户没有历史日志，则生成新的 `versionId` 并从版本 1 开始。

### 3.2 服务端会话同步结果

```java
class ConversationIncrementalSyncResult {
    String versionId;
    long version;
    long idHash;
    boolean full;
    List<UserConversation> insert;
    List<UserConversation> update;
    List<String> delete;
}
```

同步语义：

- `full=true`：客户端用 `insert` 全量重建本地会话列表。
- `full=false`：客户端按 `insert`、`update`、`delete` 增量合并。
- `idHash`：当前用户会话 ID 集合 hash，用于客户端发现本地集合漂移。

### 3.3 Go SDK 同步状态

```go
type ConversationSyncCursor struct {
    VersionID string
    Version   int64
    IDHash    int64
}

type ConversationSyncResult struct {
    ConversationSyncCursor
    Full   bool
    Insert []Conversation
    Update []Conversation
    Delete []string
}
```

SDK `sync.Service` 维护三类状态：

| 状态 | 类型 | 说明 |
|------|------|------|
| `serverMaxSeqs` | `map[conversationID]int64` | 服务端当前最大消息 seq 快照 |
| `syncedMaxSeqs` | `map[conversationID]int64` | 本地已经完整同步到的最大 seq |
| `conversationCursor` | `ConversationSyncCursor` | 会话元数据同步游标 |

`serverMaxSeqs` 与 `syncedMaxSeqs` 必须分开。前者描述服务端最新位置，后者描述本地落地位置；混用会导致打开会话时误判“已经同步完成”。

### 3.4 CheeseBox 本地消息结构

CheeseBox 的 `MessageItem` 保留服务端消息身份字段：

- `ConversationID`
- `Sequence`
- `ClientMsgID`
- `ServerMsgID`
- `SendTime`
- `CreateTime`

AppStore 按稳定消息身份去重，优先级为：

1. `ServerMsgID`
2. `Sequence`
3. `ClientMsgID`
4. 本地 `ID`

---

## 4. 消息同步流程

### 4.1 消息发送

```
CheeseBox SendText
       │
       ▼
Go SDK Client.SendText
       │
       ├─ resolveChatTarget(conversationID, currentUser)
       ├─ 构造 ProtoMessage
       └─ tcpim.Client.SendChatMessage
              │
              ▼
         Server 接收并分配 conversation seq
```

当前 Go SDK 发送消息后返回本地 `types.Message`，包含：

- `ClientMsgID`
- `SenderID`
- `ReceiverID` 或 `GroupID`
- `ChatType`
- `ContentType`
- `Content`
- `SendTime`

服务端最终的 `ServerMsgID` 和 `Sequence` 由服务端消息链路生成，并通过历史拉取或实时事件回流到客户端。

### 4.2 打开会话拉取历史

```
OpenConversation(conversationID, limit)
       │
       ▼
读取 serverMaxSeq
       │
       ▼
计算 [beginSeq, serverMaxSeq]
       │
       ▼
PullMessages(ranges, limit)
       │
       ▼
返回 PulledConversationMessages
```

Go SDK 当前 `OpenConversation` 使用 `serverMaxSeq` 推导最近 `limit` 条消息范围。CheeseBox `sync.Syncer.OpenConversation` 在已有本地消息时使用 `localMax < serverMax` 判断是否补拉后续消息。

### 4.3 Range Pull

SDK 使用 `SeqRange` 描述待拉取范围：

```go
type SeqRange struct {
    ConversationID string
    BeginSeq       int64
    EndSeq         int64
}
```

服务端返回：

```go
type PulledConversationMessages struct {
    ConversationID string
    EndSeq         int64
    Completed      bool
    Messages       []Message
}
```

设计原则：

- 客户端只相信服务端返回的消息内容和 seq。
- 本地合并按消息稳定身份去重。
- 拉取失败不推进本地 `syncedMaxSeq`。

---

## 5. 会话同步流程

### 5.1 触发时机

当前已实现的触发点：

| 触发点 | 行为 |
|--------|------|
| 登录成功 | CheeseBox 读取本地 user-scoped cursor，写入 SDK，然后触发一次 `SyncConversations` |
| 手动调用 SDK | 外部可调用 `Client.SyncConversations(ctx)` 拉取会话增量 |
| 删除/隐藏会话 | 服务端写 `DELETE` 版本日志，后续客户端增量同步可感知删除 |

目标触发点：

- TCP/WS 连接恢复后触发会话增量同步。
- 服务端会话变更通知作为唤醒信号。
- 定时或前后台切换触发轻量同步。

### 5.2 增量同步请求

HTTP 接口：

```http
GET /api/im/conversations/sync/incremental?cursor={versionId}:{version}:{idHash}&limit=200
Authorization: Bearer <accessToken>
```

Go SDK 对外接口：

```go
func (c *Client) SyncConversations(ctx context.Context) (types.ConversationSyncResult, error)
```

SDK 行为：

1. 从 `sync.Service` 读取当前 `ConversationSyncCursor`。
2. 调用 `social.RosterService.SyncConversations`。
3. 成功后用返回结果更新 SDK cursor。
4. 应用层负责把返回的 insert/update/delete 合并到本地 store。

### 5.3 服务端处理

```
ConversationController
       │
       ▼
ConversationFacade
       │
       ▼
ConversationService.syncConversations(ownerUserId, cursor, limit)
       │
       ├─ 无 cursor / cursor 失效 / idHash 漂移 → full=true
       ├─ cursor 有效 → findAfter(ownerUserId, versionId, version, limit)
       ├─ 按 operation 分类 insert/update/delete
       └─ 返回最新 versionId/version/idHash
```

同步过程中不要把 api-server response model 下沉到 business service。business service 返回 common-api DTO，HTTP 封装留在 facade。

### 5.4 删除/隐藏会话

删除接口：

```http
DELETE /api/im/conversations/{conversationId}
Authorization: Bearer <accessToken>
```

语义：

- 删除当前用户维度的 `UserConversation` 元数据。
- 写入 `ConversationVersionOperation.DELETE`。
- 不删除历史消息。
- 不影响同一会话中的其他用户。

该语义更接近“隐藏会话”或“从我的会话列表移除”。用户再次收到该会话新消息时，服务端可重新创建当前用户的会话元数据并写入 `INSERT` 或 `UPDATE`。

### 5.5 客户端合并规则

| 服务端结果 | 客户端处理 |
|------------|------------|
| `full=true` | 清空本地会话列表，用 `insert` 重建 |
| `insert` | upsert 本地会话 |
| `update` | upsert 本地会话 |
| `delete` | 删除本地会话摘要和展示入口，历史消息可按本地策略保留或清理 |
| cursor | 持久化到当前用户 namespace |

---

## 6. 实时同步与 Gap Repair

### 6.1 实时事件定位

实时 TCP/WS 事件不是最终一致来源，只是低延迟到达通道。

```
tcpim.Event
       │
       ▼
Go SDK Client.handleTransportEvent
       │
       ▼
types.Event{Kind: Realtime, Message, ConversationID}
       │
       ▼
CheeseBox RootModel.handleRealtimeEvent
       │
       ▼
sync.Syncer.HandleRealtime
```

### 6.2 Gap Repair

CheeseBox 当前 syncer 负责实时消息合并和缺口修复：

```go
type RealtimeResult struct {
    ConversationID string
    Messages       []types.Message
    Repaired       bool
}
```

处理流程：

1. 根据消息 `ChatType`、`SenderID`、`ReceiverID`、`GroupID` 推导 `conversationID`。
2. 读取本地消息列表，计算 `localMax`。
3. 如果 `message.Sequence > localMax + 1`，拉取 `[localMax+1, message.Sequence-1]`。
4. 合并修复消息和当前实时消息。
5. 返回完整合并后的消息列表给 UI。
6. UI 用 `SetMessages` 覆盖当前会话消息列表，避免只追加实时消息导致缺口消息停留在 syncer 内存中。

### 6.3 消息合并排序

合并规则：

- 去重 key 优先级：`ServerMsgID` > `Sequence` > `ClientMsgID`。
- 排序优先 `Sequence`。
- `Sequence` 相同使用 `SendTime` 排序。

如果消息没有服务端身份且没有 seq，只能按 `ClientMsgID` 去重；这类消息通常是本地待确认消息。

---

## 7. 未读数与已读处理

### 7.1 初始化

登录 bootstrap 返回：

```go
type BootstrapData struct {
    Conversations      []Conversation
    MaxSeqs            map[string]int64
    ReadSnapshots      map[string]ReadSnapshot
    ConversationCursor ConversationSyncCursor
}
```

其中：

- `MaxSeqs` 初始化 SDK `serverMaxSeqs`。
- `ReadSnapshots` 初始化应用层展示的 read/max/unread。
- `ConversationCursor` 初始化会话元数据同步游标。

### 7.2 已读 ACK

Go SDK 暴露：

```go
func (c *Client) MarkRead(ctx context.Context, conversationID string, readSeq int64) error
```

底层通过 `sync.Service.MarkRead` 调用 HTTP API 的 `AckReadSeq`。

服务端应保证 readSeq 单调：

- 新 readSeq 小于或等于当前 readSeq 时不回退。
- unread 推荐由 `maxSeq - readSeq` 推导。
- 多端读状态最终以服务端 snapshot 为准。

---

## 8. 本地持久化与多用户隔离

### 8.1 CheeseBox Store 分层

```
AppStore
   │
   ├─ Conversations map[string]ConversationSummary
   ├─ MessagesByConv map[string][]MessageItem
   ├─ ConversationCursor
   └─ Persister
          │
          ▼
     PersistedStoreForUser(baseDir, userID)
```

### 8.2 User-scoped Namespace

CheeseBox 登录前使用空 AppStore。登录成功后：

1. 根据当前 `userID` 创建用户维度 persister。
2. 读取该用户的本地会话、消息、cursor。
3. 将本地 cursor 注入 Go SDK。
4. 触发服务端会话增量同步。

这样可以避免同一台机器上不同账号共用 `cheesebox_store.json` 时互相污染。

### 8.3 Cursor 持久化

会话同步成功后，客户端需要持久化：

- `VersionID`
- `Version`
- `IDHash`

该 cursor 属于用户维度。不能跨用户复用，也不能按 conversation 维度拆分。

---

## 9. API 接口列表

### 9.1 会话接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/im/conversations` | 获取当前会话列表 |
| `GET` | `/api/im/conversations/sync/incremental` | 按 cursor 增量同步会话元数据 |
| `DELETE` | `/api/im/conversations/{conversationId}` | 删除/隐藏当前用户维度会话 |

### 9.2 消息接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | 消息 range pull API | 按 `SeqRange` 拉取历史消息 |
| `POST` | read ack API | 标记指定会话 readSeq |

注：当前文档关注同步模型，具体 HTTP 路径以 `sdks/go/transport/httpapi/client.go` 和 api-server controller 为准。

### 9.3 SDK 对外能力

| SDK 方法 | 说明 |
|----------|------|
| `Login` / `LoginWithToken` / `Reconnect` | 建立 session，bootstrap 同步状态 |
| `SyncConversations` | 会话元数据增量同步 |
| `DeleteConversation` | 删除/隐藏当前用户维度会话 |
| `OpenConversation` | 打开会话并拉取最近历史 |
| `PullMessages` | 拉取指定 seq ranges |
| `MarkRead` | 上报 readSeq |
| `Events` | 订阅实时事件 |

---

## 10. 关键设计决策

### 10.1 消息同步与会话同步分离

消息同步以 conversation seq 为核心，会话同步以用户版本日志为核心。两者不能混用：

- 消息 seq 解决“消息有没有缺、顺序是否连续”。
- 会话 version 解决“会话列表、会话配置是否变化”。

### 10.2 删除会话不是删除消息

当前 `DeleteConversation` 是用户维度隐藏语义：

- 删除 `UserConversation` 当前态。
- 追加 `DELETE` 版本日志。
- 不删除消息存储。
- 不影响对端或群内其他用户。

### 10.3 Realtime 不能替代 Pull

实时消息可能乱序、丢失、延迟或重复。客户端必须按 seq 判断连续性，缺口通过 `PullMessages` 修复。

### 10.4 Cursor 必须持久化

如果客户端不持久化会话 cursor，每次启动都只能做全量同步。CheeseBox 因此把 `ConversationSyncCursor` 存入 user-scoped persisted store。

### 10.5 API Response 不下沉到 Business

business service 返回 common-api domain/DTO。api-server facade 负责 HTTP response 包装，避免业务层依赖 transport 模型。

---

## 11. 与 OpenIM 设计的对应关系

| OpenIM 设计点 | CheeseIM 当前对应 | 差异 |
|---------------|------------------|------|
| MsgSyncer 维护 `syncedMaxSeqs` | Go SDK `sync.Service.syncedMaxSeqs` | CheeseIM 还未把完整消息 syncer 全部下沉到 SDK |
| 服务端 conversation maxSeq | Go SDK `serverMaxSeqs` | 当前 bootstrap 获取快照，后续更新路径需继续完善 |
| PushMsg 到达后检查 seq 连续性 | CheeseBox `sync.Syncer.HandleRealtime` | 目前 gap repair 在 CheeseBox 层，目标是继续下沉到 SDK |
| VersionSynchronizer 同步会话 | `ConversationVersionLog` + `ConversationSyncCursor` | CheeseIM 已实现用户维度版本日志 |
| LocalConversation 持久化 | CheeseBox `PersistedStoreForUser` | 当前是 TUI JSON store，非通用 SDK DB |
| ReadDrawing / readSeq | SDK `MarkRead` + server snapshot | 已有基础链路，仍需多端 read event 同步 |

CheeseIM 的当前实现更轻量：服务端主线保留消息 seq 和会话版本日志，客户端先在 Go SDK 暴露同步能力，再由 CheeseBox 做本地集成。后续可以逐步把 OpenIM SDK Core 中更完整的本地数据库、事件队列、批量同步器下沉到 Go SDK。

---

## 12. 后续演进

### 12.1 SDK 侧同步引擎下沉

当前 CheeseBox 仍维护部分消息 store 和 realtime gap repair。后续建议在 Go SDK 引入更完整的 sync engine：

- SDK 内部持久化 `syncedMaxSeqs`。
- SDK 统一处理 realtime push、range pull、gap repair。
- App 只订阅“会话更新”“消息更新”“同步状态”事件。

### 12.2 连接恢复同步

连接恢复后应触发：

1. 拉取会话增量。
2. 获取最新 maxSeq 快照。
3. 对比本地 `syncedMaxSeqs` 并批量补拉。
4. 发出同步开始/结束事件。

### 12.3 会话变更实时通知

当前会话同步主要由登录触发。后续可以增加服务端会话变更事件，作为客户端调用 `SyncConversations` 的唤醒信号。

### 12.4 批量消息同步策略

参考 OpenIM 的 `SplitPullMsgNum` 思路，后续 SDK 可按消息数量拆分多个 conversation range：

- 单批最大消息数。
- 单会话最大拉取数。
- 通知会话和普通会话可采用不同限额。
- 失败批次不推进对应 conversation 的 `syncedMaxSeq`。

### 12.5 端到端测试

建议补充真实端到端场景：

- 两个 CheeseBox 客户端登录。
- A 给 B 发消息。
- B 离线后重连，验证会话增量和消息 range pull。
- 删除/隐藏会话后重新同步，验证本地会话被移除但历史消息仍可按策略拉取。
- 人为制造 seq gap，验证 realtime gap repair。

