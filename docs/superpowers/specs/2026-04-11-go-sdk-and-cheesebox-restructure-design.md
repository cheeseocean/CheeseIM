# Go SDK And CheeseBox Restructure Design

## Problem

当前 `CheeseBox` 同时承担了两类职责：

- IM client 协议与同步能力
- Bubble Tea TUI 应用层

这会带来几个问题：

- `apps/CheeseBox/internal/transport` 和 `internal/service` 实际上已经是通用 IM client 能力，但被绑死在 app 目录下
- `RootModel` 既要处理 UI，又要处理登录、历史加载、实时消息和重连
- 新增的服务端同步接口无法自然沉淀成“其他 Go 应用可复用”的 SDK 能力
- 后续如果要做 GUI、CLI bot、桌面端或其他应用，会重复实现同一套 auth/sync/realtime 逻辑

## Goal

将 Go 侧重构为：

- `sdks/go`
  - 提供通用 IM client 能力
- `apps/CheeseBox`
  - 只作为 TUI 应用
  - 组合 SDK 能力完成展示与交互

目标是让后续其他应用可以直接复用：

- 登录与票据获取
- TCP 连接与实时事件
- 会话同步
- 历史补拉
- 已读同步
- 好友 / 群组 / 会话查询

而不需要依赖 CheeseBox 的 UI 结构。

## Recommended Structure

推荐采用“SDK 提供领域能力，应用层做 UI 装配”的结构。

### SDK 模块

- `sdks/go/client`
  - 对外统一入口 `Client`
- `sdks/go/auth`
  - 登录、token、ws-ticket、重连
- `sdks/go/transport/httpapi`
  - HTTP API 访问
- `sdks/go/transport/tcpim`
  - TCP 长连接、protobuf 编解码、实时事件流
- `sdks/go/sync`
  - `maxSeq/readSeq` 同步
  - reconnect sync
  - wakeup sync
  - push gap repair
- `sdks/go/social`
  - friends
  - groups
  - conversations
  - add friend
- `sdks/go/types`
  - 通用领域模型

### CheeseBox 模块

保留：

- `apps/CheeseBox/internal/ui`
- `apps/CheeseBox/internal/store`
- app 级视图投影模型

迁出：

- `apps/CheeseBox/internal/transport/httpapi`
- `apps/CheeseBox/internal/transport/tcpim`
- `apps/CheeseBox/internal/service/auth_service.go`
- `apps/CheeseBox/internal/service/chat_service.go`
- `apps/CheeseBox/internal/service/contact_service.go`
- `apps/CheeseBox/internal/service/roster_service.go`

## Why SDK Should Not Own UI State

`sdks/go` 不应承接 Bubble Tea 或 app store 这类 UI 状态，原因如下：

- SDK 的消费者不一定是 TUI
- UI 需要的三栏列表、toast、焦点、输入框状态，不属于 IM client 领域
- 如果 SDK 持有过多应用态，后续其他 app 会被迫适配 CheeseBox 的结构

因此边界应保持为：

- SDK 输出领域对象和事件
- CheeseBox 将领域对象映射成 UI view model

## SDK Responsibilities

`sdks/go` 需要至少提供以下能力。

### Authentication

- `Login(userID, password)`
- `LoginWithToken(token)`
- `Reconnect()`

职责：

- HTTP 登录
- 票据获取
- TCP 建连和认证

### Bootstrap

- `Bootstrap()`

职责：

- 拉取好友列表
- 拉取群组列表
- 拉取会话列表
- 拉取会话 `maxSeq`
- 拉取会话 `readSnapshots`
- 初始化本地同步状态

### Conversation Sync

- `OpenConversation(conversationID)`
- `EnsureConversationSynced(conversationID)`
- `SyncAll()`
- `SyncConversations(conversationIDs)`
- `MarkRead(conversationID, readSeq)`

职责：

- 基于本地 `syncedMaxSeqs` 与服务端 `maxSeq` 做差量拉取
- 收到 push 后发现 gap 时补洞
- 获取和收敛 `readSeq/maxSeq/unread`

### Realtime Events

- `Events()`

事件类型建议至少包括：

- realtime message
- disconnect
- error
- sync started
- sync finished
- gap repaired
- read snapshot updated

### Social Query

- `ListFriends()`
- `ListGroups()`
- `ListConversations()`
- `AddFriend()`

## Server API Mapping

SDK 需要直接接入当前最新的同步接口：

- `GET /api/im/conversations/max-seqs`
- `GET /api/im/conversations/read-snapshots`
- `POST /api/im/conversations/sync/pull`
- `PUT /api/im/conversations/{conversationId}/read-seq`

以及已有接口：

- `/api/auth/login`
- `/api/im/ws-ticket`
- `/api/im/friends`
- `/api/im/groups`
- `/api/im/conversations`
- `/api/im/friends/requests`

## Local Sync State

SDK 内部维护：

- `syncedMaxSeqs map[string]int64`
- `readSeqs map[string]int64`
- `maxSeqs map[string]int64`

第一阶段可以先只做内存态。  
后续若要支持完整离线恢复，再引入本地 DB。

## Sync Workflow

### Login Success

1. HTTP login
2. issue ws-ticket
3. TCP connect + auth
4. `Bootstrap()`

### Bootstrap

1. 拉 friends / groups / conversations
2. 拉 `max-seqs`
3. 拉 `read-snapshots`
4. 初始化本地 `syncedMaxSeqs`
5. 生成首屏 bootstrap result

### Open Conversation

1. 读取本地已缓存消息
2. 对比本地 `syncedMaxSeq` 与服务端 `maxSeq`
3. 如有缺口，调 `sync/pull`
4. 更新本地消息列表

### Reconnect / Wakeup

1. 重新拉 `max-seqs`
2. 对全部或活跃会话进行差量同步
3. 拉 `read-snapshots`
4. 通知应用层刷新会话列表与未读

### Push Gap Repair

收到实时消息时：

- 如果 seq 连续：
  - 直接落本地
  - 推进 `syncedMaxSeq`
- 如果 seq 不连续：
  - 触发 `sync/pull`
  - 补齐 `[localMax+1, pushedSeq]`

## CheeseBox Integration

重构后 CheeseBox 应只依赖 SDK，不再自己编排 auth/roster/chat。

### RootModel

`RootModel` 只处理：

- 登录表单
- 调 SDK 登录
- 接收 SDK bootstrap result
- 监听 SDK events
- 将领域数据写入 `AppStore`

### AppStore

`AppStore` 继续只持有 UI 投影：

- 好友列表
- 群组列表
- 会话列表
- 当前会话消息
- toast
- 连接状态

### Mapping Layer

CheeseBox 需要保留一层 SDK -> UI 的映射：

- `sdk Conversation -> ConversationSummary`
- `sdk Message -> MessageItem`
- `sdk ReadSnapshot -> unread badge`

这层逻辑仍属于 app。

## Domain Model Split

SDK 的 `types` 应是通用 IM 领域对象，不应复用 CheeseBox 当前偏 UI 的 summary 结构。

建议 SDK 模型至少包含：

- `Conversation`
- `Friend`
- `Group`
- `Message`
- `ReadSnapshot`
- `BootstrapData`

CheeseBox 再自己映射为：

- `ConversationSummary`
- `MessageItem`
- `FriendSummary`
- `GroupSummary`

## Migration Order

建议按以下顺序迁移：

1. 新建 `sdks/go` 模块与基础包结构
2. 将 `httpapi` 和 `tcpim` 迁入 SDK
3. 将 `auth/roster/chat/contact` service 迁入 SDK，并做 Java API 对齐
4. 新增 SDK sync 状态机
5. 让 CheeseBox 改为依赖 SDK `Client`
6. 清理 CheeseBox 内部已废弃的 transport/service

## Phase 1 Scope

第一阶段只完成：

- SDK 拆分
- CheeseBox 改为依赖 SDK
- reconnect sync
- push gap repair
- read snapshot sync

暂不做：

- 本地消息数据库
- 完整历史分页缓存
- 文件消息同步
- 多设备独立同步位点

## Recommendation

按“中拆”方案执行：

- SDK 提供通用 IM client 能力
- CheeseBox 保留 TUI 装配和展示

这是最适合当前 CheeseIM 的平衡点：

- 可复用
- 不会把 SDK 过度 UI 化
- 又能把现有 CheeseBox 的协议逻辑及时沉淀为正式客户端能力
