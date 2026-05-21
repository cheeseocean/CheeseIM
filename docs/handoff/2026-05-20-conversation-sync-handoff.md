# CheeseIM 会话/消息同步交接

生成时间：2026-05-20

## 接手入口

下一个 session 可以直接发送：

```text
请先读取 docs/handoff/2026-05-20-conversation-sync-handoff.md，然后继续收敛 CheeseIM 会话/消息同步。
```

建议接手后先执行：

```bash
git status --short
git diff --stat
```

当前工作区包含本轮会话同步改动，也包含之前已有的好友通知、ContentType、SessionPrincipal 等未提交改动。不要在未确认归属前回滚这些文件。

## 当前目标

当前正在按「方案三」收敛 CheeseIM 的同步模型：

- 会话元数据同步：服务端维护用户维度的会话版本日志，客户端用 cursor 做增量同步。
- 消息同步：继续使用会话维度的 seq/range/maxSeq/readSeq 语义，客户端按会话拉取缺口消息。
- 实时同步：TCP/WS 推送只作为唤醒和实时到达通道，客户端仍需要用 seq 做 gap repair。
- SDK 中心化：`sdks/go` 作为通用 IM client 能力层，CheeseBox 只作为 TUI app 集成 SDK。

## 当前架构

### 会话元数据同步

服务端新增 `ConversationVersionLog`，每个用户会话的创建、更新、删除/隐藏都应该写一条用户维度版本日志。

当前已实现的主路径：

- `ConversationService.syncConversations(userId, cursor, limit)` 暴露增量同步能力。
- API Server 暴露 `GET /api/im/conversations/sync/incremental?cursor=&limit=`。
- HTTP Facade 将 service 结果包装成 `ConversationIncrementalSyncResponse`。
- Go SDK 新增 `SyncConversations`，CheeseBox 登录成功后读取本地 cursor 并触发一次会话增量同步。

### 消息同步

消息同步仍以会话 seq 为核心：

- SDK `sync.Service` 区分 `serverMaxSeqs` 和 `syncedMaxSeqs`。
- `OpenConversation` 使用服务端 maxSeq 判断是否存在待同步消息，避免把本地已同步 seq 当作服务端最新 seq。
- CheeseBox 实时收到消息后写入本地 store，并通过 syncer 进行缺口修复。

### 实时推送

当前实时链路语义：

- 服务端 TCP/WS 推送消息或事件。
- Go SDK 监听实时事件。
- CheeseBox UI 收到事件后交给 syncer 做合并和 gap repair，再把 syncer 返回的完整消息列表落到 AppStore。
- 如果 seq 不连续，syncer 通过 SDK puller 拉取缺失消息，UI 只消费修复后的结果。

## 已完成改动

### Server

新增文件：

- `server/common-api/src/main/java/com/cheeseocean/im/common/api/business/domain/ConversationVersionLog.java`
- `server/common-api/src/main/java/com/cheeseocean/im/common/api/dto/conversation/ConversationIncrementalSyncResult.java`
- `server/common-api/src/main/java/com/cheeseocean/im/common/api/enums/ConversationVersionOperation.java`
- `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/ConversationVersionLogRepository.java`
- `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/conversation/ConversationVersionLogDoc.java`
- `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationVersionLogRepositoryImpl.java`
- `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationVersionLogRepositoryImplTest.java`
- `server/api-server/src/main/java/com/cheeseocean/im/apiserver/model/response/ConversationIncrementalSyncResponse.java`

主要修改：

- `ConversationService` 增加 `syncConversations(...)`。
- `ConversationService` 增加 `deleteConversation(ownerUserId, conversationId)`。
- `ConversationServiceImpl` 在会话创建/更新路径写 version log，并实现增量同步。
- `ConversationServiceImpl.deleteConversation(...)` 只删除当前用户维度的 `UserConversation` 元数据，并写入 `ConversationVersionOperation.DELETE` 日志；不删除历史消息，不影响其他用户。
- `ConversationController` 增加会话增量同步 HTTP 接口。
- `ConversationController` 增加 `DELETE /api/im/conversations/{conversationId}`。
- `ConversationFacade` 增加 service 结果到 response 的封装。
- `ConversationServiceImplTest`、`ConversationFacadeTest`、`ConversationControllerTest` 增加覆盖。

### Go SDK

主要修改：

- `sdks/go/types/types.go` 增加 `ConversationSyncCursor`、`ConversationSyncResult`、`BootstrapData.ConversationCursor`。
- `sdks/go/transport/httpapi/client.go` 增加 `SyncConversations`。
- `sdks/go/sync/service.go` 区分本地已同步 seq 和服务端最新 seq，并维护会话同步 cursor。
- `sdks/go/social/service.go` 暴露 `SyncConversations`。
- `sdks/go/transport/httpapi/client.go` 暴露 `DeleteConversation`。
- `sdks/go/social/service.go` 暴露 `DeleteConversation`。
- `sdks/go/client/client.go` 暴露 `GetServerMaxSeq`、`GetConversationCursor`、`UpdateConversationCursor`、`SyncConversations`、`DeleteConversation`。
- `OpenConversation` 改为基于 server max seq 判断是否需要消息同步。

### CheeseBox

主要修改：

- `MessageItem` 增加 `ConversationID`、`Sequence`、`ClientMsgID`、`ServerMsgID`、`SendTime`、`CreateTime`。
- `PersistedStore` 增加更完整的消息字段持久化，以及 `ConversationSyncCursor` 读写。
- `AppStore` 增加会话 cursor、删除会话、本地消息元数据映射。
- `root_model.go` 登录成功后读取用户维度本地 cursor，更新 SDK cursor，触发会话增量同步和实时事件监听。
- `root_model.go` 增加 `conversationSyncSuccessMsg`、`applyConversationSyncResult`、`toConversationSummary`。
- `root_model.go` 实时消息改为使用 syncer 的合并结果落入 AppStore，不再只在 UI 层追加原始 realtime 消息。
- `syncer.HandleRealtime` 返回 `RealtimeResult`，包含会话 ID、合并后的消息列表以及是否做过 gap repair。
- `PersistedStore` 增加 user-scoped namespace：`NewPersistedStoreForUser(baseDir, userID)`。
- UI fake client 和测试已适配新的 SDK 接口。

## 验证结果

已通过：

```bash
cd server
./gradlew :business:test --tests com.cheeseocean.im.business.service.conversation.ConversationServiceImplTest
./gradlew :api-server:test --tests com.cheeseocean.im.apiserver.facade.ConversationFacadeTest --tests com.cheeseocean.im.apiserver.controller.ConversationControllerTest
./gradlew :common-api:test :common-core:test
```

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache go test ./...
```

```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache go test ./...
```

`git diff --check` 已通过。

## 已知阻塞

当前没有已知测试阻塞。

## 重要约束

- `ConversationVersionLog` 是当前会话元数据增量同步的关键，不要只依赖 `UserConversation` 当前态，否则客户端无法可靠感知删除/隐藏/更新。
- 实时推送不能替代历史同步。TCP/WS 到达事件只能降低延迟，最终一致仍应由 seq 拉取保证。
- CheeseBox 本地 store 已按用户 namespace 隔离。登录前仍使用空 store，登录成功后切换到当前用户 persister。
- 版本日志 cursor 是用户维度，不是全局维度，也不是 conversation 维度。
- 不要把 API response 模型下沉到 business service；service 应返回领域模型或 common-api DTO，HTTP 封装留在 api-server facade。

## 下一步建议

1. 为 CheeseBox 增加用户触发的删除/隐藏会话 UI 操作，调用 SDK `DeleteConversation`，并立即更新本地 AppStore。
2. 继续把更多消息 store/event model 下沉到 Go SDK，减少 CheeseBox 维护消息同步状态的职责。
3. 增加真实端到端测试：两个 CheeseBox 客户端登录、发消息、断线重连、删除/隐藏会话、验证会话增量同步和消息 gap repair。
4. 提交前按主题拆分 review，避免把好友通知、枚举/session、会话同步、CheeseBox 持久化混在一个 commit。

## 建议提交分组

1. 会话版本同步主干：
   - `ConversationVersionLog*`
   - `ConversationIncrementalSync*`
   - `ConversationService.syncConversations(...)`
   - `ConversationController` / `ConversationFacade` 的 sync endpoint
   - 对应 Java 测试
2. 删除/隐藏会话写路径：
   - `ConversationService.deleteConversation(...)`
   - `UserConversationRepository.delete(...)`
   - `DELETE /api/im/conversations/{conversationId}`
   - Go SDK `DeleteConversation`
   - 对应 Java/Go 测试
3. Go SDK 同步状态：
   - `sdks/go/types`
   - `sdks/go/sync`
   - `sdks/go/social`
   - `sdks/go/client`
   - `sdks/go/transport/httpapi`
4. CheeseBox 集成：
   - user-scoped persisted store
   - AppStore 消息去重和元数据保留
   - RootModel 会话增量同步
   - syncer realtime gap repair 结果回流 UI
5. 独立修复：
   - CheeseBox 默认 platform `cli`
   - `ChronicleQueueAdapterTest`
   - `QueueAutoConfigurerTest`
   - `ConversationIdUtilTest`
   - 好友通知 / `ContentType` / `SessionPrincipal` 相关改动

## 当前文件状态提示

本轮会话同步相关改动主要集中在：

- `server/common-api/src/main/java/com/cheeseocean/im/common/api/conversation/ConversationService.java`
- `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationServiceImpl.java`
- `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/ConversationController.java`
- `server/api-server/src/main/java/com/cheeseocean/im/apiserver/facade/ConversationFacade.java`
- `sdks/go/client/client.go`
- `sdks/go/sync/service.go`
- `sdks/go/transport/httpapi/client.go`
- `apps/CheeseBox/internal/ui/root_model.go`
- `apps/CheeseBox/internal/store/app_store.go`
- `apps/CheeseBox/internal/store/persisted_store.go`

工作区里还有一些不是本轮主线的脏文件，例如好友通知、好友关系事件、ContentType、SessionPrincipal 等。提交前需要分组 review，避免把不同主题混在同一个 commit 中。
