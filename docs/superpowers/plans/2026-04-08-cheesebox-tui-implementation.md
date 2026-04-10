# CheeseBox TUI Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `apps/CheeseBox` into a usable Bubble Tea TUI that logs in with an existing access token, authenticates over CheeseIM TCP protobuf, loads roster and recent history, and supports one-to-one and group text chat.

**Architecture:** The app is split into `transport`, `service`, `store`, and `ui` layers so Bubble Tea models never touch raw protobuf or networking details. HTTP remains the query/control plane for ticket and roster/history loading, while TCP protobuf is the realtime plane for auth, heartbeat, send, receive, and reconnect.

**Tech Stack:** Go, Bubble Tea, Bubbles, Lip Gloss, protobuf-go, standard `net`/`http`, CheeseIM TCP protobuf protocol

---

## File Map

### Create

- `apps/CheeseBox/go.mod`
- `apps/CheeseBox/README.md`
- `apps/CheeseBox/.gitignore`
- `apps/CheeseBox/cmd/cheesebox/main.go`
- `apps/CheeseBox/internal/config/config.go`
- `apps/CheeseBox/internal/config/config_test.go`
- `apps/CheeseBox/internal/domain/types.go`
- `apps/CheeseBox/internal/domain/types_test.go`
- `apps/CheeseBox/internal/proto/message_protocol.proto`
- `apps/CheeseBox/internal/proto/message_protocol.pb.go`
- `apps/CheeseBox/internal/proto/gen.go`
- `apps/CheeseBox/internal/transport/httpapi/client.go`
- `apps/CheeseBox/internal/transport/httpapi/client_test.go`
- `apps/CheeseBox/internal/transport/tcpim/frame.go`
- `apps/CheeseBox/internal/transport/tcpim/frame_test.go`
- `apps/CheeseBox/internal/transport/tcpim/client.go`
- `apps/CheeseBox/internal/transport/tcpim/client_test.go`
- `apps/CheeseBox/internal/service/auth_service.go`
- `apps/CheeseBox/internal/service/auth_service_test.go`
- `apps/CheeseBox/internal/service/roster_service.go`
- `apps/CheeseBox/internal/service/roster_service_test.go`
- `apps/CheeseBox/internal/service/chat_service.go`
- `apps/CheeseBox/internal/service/chat_service_test.go`
- `apps/CheeseBox/internal/store/app_store.go`
- `apps/CheeseBox/internal/store/app_store_test.go`
- `apps/CheeseBox/internal/ui/theme.go`
- `apps/CheeseBox/internal/ui/messages.go`
- `apps/CheeseBox/internal/ui/login_model.go`
- `apps/CheeseBox/internal/ui/login_model_test.go`
- `apps/CheeseBox/internal/ui/app_model.go`
- `apps/CheeseBox/internal/ui/app_model_test.go`
- `apps/CheeseBox/internal/ui/help_view.go`
- `server/postbox/src/main/java/com/cheeseocean/im/postbox/api/ConversationSummaryResponse.java`
- `server/postbox/src/main/java/com/cheeseocean/im/postbox/api/HistoryMessageResponse.java`
- `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/BlockMessageQueryService.java`
- `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationPresentationResolver.java`
- `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationQueryService.java`
- `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryQueryService.java`
- `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/MessagePreviewResolver.java`
- `server/business/src/main/java/com/cheeseocean/im/business/controller/ConversationController.java`
- `server/business/src/main/java/com/cheeseocean/im/business/controller/GroupController.java`
- `server/business/src/test/java/com/cheeseocean/im/business/controller/ConversationControllerTest.java`
- `server/business/src/test/java/com/cheeseocean/im/business/controller/GroupControllerTest.java`

### Modify

- `apps/CheeseBox/arch.md`
  - add a short implementation-status appendix that points at the real code paths once the app exists

### References

- `docs/superpowers/specs/2026-04-08-cheesebox-tui-design.md`
- `server/common-api/src/main/proto/message_protocol.proto`
- `server/postoffice/src/test/java/com/cheeseocean/im/postoffice/client/ProtocolContractFixtures.java`
- `apps/im_java_client_demo/README.md`

## Task 1: Bootstrap the Go module and shared domain model

**Files:**
- Create: `apps/CheeseBox/go.mod`
- Create: `apps/CheeseBox/.gitignore`
- Create: `apps/CheeseBox/README.md`
- Create: `apps/CheeseBox/cmd/cheesebox/main.go`
- Create: `apps/CheeseBox/internal/config/config.go`
- Create: `apps/CheeseBox/internal/config/config_test.go`
- Create: `apps/CheeseBox/internal/domain/types.go`
- Create: `apps/CheeseBox/internal/domain/types_test.go`

- [ ] **Step 1: Write the failing config and domain tests**

Add tests for:

- config defaults for `api_base_url`, `tcp_addr`, `device_id`, `platform`
- `ConversationRef` constructors for direct and group sessions
- `MessageItem` helper for marking self vs peer

Run:

```bash
cd apps/CheeseBox && go test ./internal/config ./internal/domain
```

Expected: FAIL because the module and packages do not exist yet.

- [ ] **Step 2: Create the Go module and base files**

Implement:

- `go.mod` with Bubble Tea, Bubbles, Lip Gloss, and protobuf dependencies
- `.gitignore` for local binaries and `.env`
- `config.go` with env-backed defaults plus a `RuntimeConfig` struct
- `types.go` with `ConversationRef`, `MessageItem`, `NavKey`, `ConnectionStatus`, and `Toast`
- `main.go` that loads config and boots an empty Bubble Tea app placeholder

- [ ] **Step 3: Run the focused tests**

Run:

```bash
cd apps/CheeseBox && go test ./internal/config ./internal/domain
```

Expected: PASS

- [ ] **Step 4: Run module-level smoke verification**

Run:

```bash
cd apps/CheeseBox && go test ./...
```

Expected: PASS with only bootstrap packages present.

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/go.mod apps/CheeseBox/.gitignore apps/CheeseBox/README.md apps/CheeseBox/cmd/cheesebox/main.go apps/CheeseBox/internal/config apps/CheeseBox/internal/domain
git commit -m "feat: bootstrap CheeseBox Go module"
```

## Task 2: Mirror the CheeseIM protobuf contract and TCP frame codec

**Files:**
- Create: `apps/CheeseBox/internal/proto/message_protocol.proto`
- Create: `apps/CheeseBox/internal/proto/message_protocol.pb.go`
- Create: `apps/CheeseBox/internal/proto/gen.go`
- Create: `apps/CheeseBox/internal/transport/tcpim/frame.go`
- Create: `apps/CheeseBox/internal/transport/tcpim/frame_test.go`

- [ ] **Step 1: Write the failing codec tests**

Cover:

- encode auth request to the expected TCP header layout
- decode connect/auth/chat ack/chat notify from frame bytes
- reject invalid magic and truncated frames

Use the server-side constants from `ProtocolContractFixtures.java` as the expected frame shape.

Run:

```bash
cd apps/CheeseBox && go test ./internal/transport/tcpim -run TestFrame
```

Expected: FAIL because the codec and generated protobuf types do not exist.

- [ ] **Step 2: Copy and generate the protobuf contract**

Implement:

- copy `server/common-api/src/main/proto/message_protocol.proto`
- add `option go_package = "CheeseIM/apps/CheeseBox/internal/proto;proto";` to the copied proto so generated imports stay local to the module
- generate and commit `message_protocol.pb.go`
- add `gen.go` with `//go:generate` so regeneration stays explicit

Use this exact generation command:

```bash
cd apps/CheeseBox && protoc \
  --proto_path=internal/proto \
  --go_out=paths=source_relative:internal/proto \
  internal/proto/message_protocol.proto
```

Tooling expectation:

- `protoc` installed locally
- `protoc-gen-go` installed and available on `PATH`

- [ ] **Step 3: Implement TCP frame encode/decode helpers**

Implement:

- TCP header constants
- request-id padding/trimming
- `EncodeFrame`
- `DecodeFrame`
- helpers mapping command types to transport message types

- [ ] **Step 4: Run the focused codec tests**

Run:

```bash
cd apps/CheeseBox && go test ./internal/transport/tcpim -run TestFrame
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/internal/proto apps/CheeseBox/internal/transport/tcpim/frame.go apps/CheeseBox/internal/transport/tcpim/frame_test.go
git commit -m "feat: add CheeseBox protobuf TCP codec"
```

## Task 3: Add the minimal server query API required by CheeseBox

**Files:**
- Create: `server/postbox/src/main/java/com/cheeseocean/im/postbox/api/ConversationSummaryResponse.java`
- Create: `server/postbox/src/main/java/com/cheeseocean/im/postbox/api/HistoryMessageResponse.java`
- Create: `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/BlockMessageQueryService.java`
- Create: `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationPresentationResolver.java`
- Create: `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationQueryService.java`
- Create: `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryQueryService.java`
- Create: `server/postbox/src/main/java/com/cheeseocean/im/postbox/service/MessagePreviewResolver.java`
- Create: `server/business/src/main/java/com/cheeseocean/im/business/controller/ConversationController.java`
- Create: `server/business/src/main/java/com/cheeseocean/im/business/controller/GroupController.java`
- Create: `server/business/src/test/java/com/cheeseocean/im/business/controller/ConversationControllerTest.java`
- Create: `server/business/src/test/java/com/cheeseocean/im/business/controller/GroupControllerTest.java`
- Reuse existing test: `server/postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java`
- Reuse existing test: `server/postbox/src/test/java/com/cheeseocean/im/postbox/service/HistoryQueryServiceTest.java`
- Reuse existing test: `server/postbox/src/test/java/com/cheeseocean/im/postbox/service/DirectConversationServiceTest.java`

- [ ] **Step 1: Run the existing failing postbox tests and add the missing controller tests**

Use the existing `postbox` query-service tests as the contract for the missing production classes:

- recent conversations
- recent history page
- readable preview rendering

Add controller tests for:

- `GET /api/im/conversations`
- `GET /api/im/conversations/{conversationId}/messages?limit=50`
- `GET /api/im/groups`

Run:

```bash
cd server && ./gradlew :postbox:test --tests '*ConversationQueryServiceTest' --tests '*HistoryQueryServiceTest'
cd server && ./gradlew :business:test --tests '*ConversationControllerTest' --tests '*GroupControllerTest'
```

Expected: FAIL because the query classes and controllers are missing.

- [ ] **Step 2: Implement the minimal server query API required by CheeseBox**

Implement:

- `postbox` query services needed by the existing tests
- `ConversationController` that exposes recent conversations and recent history over HTTP
- `GroupController` that exposes the current user's groups by combining `ConversationQueryService.getAllConversations(...)`, `GroupMembershipQueryService`, and `GroupRepository`
- history and conversation responses enriched with sender display names via `UserServiceFacade.getUsersInfo(...)` so group-chat rendering has a deterministic `senderName` source
- response DTOs shaped for TUI use but still grounded in current server data

This task is not a general server refactor. It only exposes the minimum real HTTP surface the TUI needs for:

- chats list
- groups list
- recent history

- [ ] **Step 3: Run the focused server query tests**

Run:

```bash
cd server && ./gradlew :postbox:test --tests '*ConversationQueryServiceTest' --tests '*HistoryQueryServiceTest'
cd server && ./gradlew :business:test --tests '*ConversationControllerTest' --tests '*GroupControllerTest'
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add server/postbox/src/main/java/com/cheeseocean/im/postbox/api server/postbox/src/main/java/com/cheeseocean/im/postbox/service server/business/src/main/java/com/cheeseocean/im/business/controller/ConversationController.java server/business/src/main/java/com/cheeseocean/im/business/controller/GroupController.java server/business/src/test/java/com/cheeseocean/im/business/controller/ConversationControllerTest.java server/business/src/test/java/com/cheeseocean/im/business/controller/GroupControllerTest.java
git commit -m "feat: add CheeseBox query endpoints"
```

## Task 4: Build the CheeseBox HTTP adapter for ticket, roster, history, and add-friend

**Files:**
- Create: `apps/CheeseBox/internal/transport/httpapi/client.go`
- Create: `apps/CheeseBox/internal/transport/httpapi/client_test.go`
- Modify: `apps/CheeseBox/internal/domain/types.go`

- [ ] **Step 1: Write the failing HTTP adapter tests**

Cover:

- `IssueWsTicket` sends bearer auth and parses `ticket`
- `ListFriends` parses friend summaries
- `ListGroups` parses the new `GET /api/im/groups` response
- `LoadHistoryPage` parses the new `GET /api/im/conversations/{conversationId}/messages` response
- `ListConversations` parses the new `GET /api/im/conversations` response
- `AddFriend` posts the request body correctly

Use `httptest.Server` for all test cases.

Run:

```bash
cd apps/CheeseBox && go test ./internal/transport/httpapi -run TestClient
```

Expected: FAIL because the client package does not exist.

- [ ] **Step 2: Implement the typed HTTP client**

Implement:

- `HTTPClient` with base URL, token injection, timeout, and JSON decode
- typed response structs for ticket, conversations, friends, groups, history, add-friend
- concrete endpoint bindings to the server query API from Task 3

- [ ] **Step 3: Run the focused HTTP tests**

Run:

```bash
cd apps/CheeseBox && go test ./internal/transport/httpapi -run TestClient
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add apps/CheeseBox/internal/transport/httpapi apps/CheeseBox/internal/domain/types.go
git commit -m "feat: add CheeseBox HTTP server adapters"
```

## Task 5: Implement the TCP IM client and auth/send/receive event stream

**Files:**
- Create: `apps/CheeseBox/internal/transport/tcpim/client.go`
- Create: `apps/CheeseBox/internal/transport/tcpim/client_test.go`
- Modify: `apps/CheeseBox/internal/domain/types.go`

- [ ] **Step 1: Write the failing TCP client tests**

Cover:

- dial, send auth envelope, and emit auth-success event
- send chat message and emit ack event
- receive inbound chat notify and emit message event
- surface disconnect and decode errors as typed events

Use `net.Pipe()` or a local TCP listener instead of real server access.

Run:

```bash
cd apps/CheeseBox && go test ./internal/transport/tcpim -run TestClient
```

Expected: FAIL because the client event loop does not exist yet.

- [ ] **Step 2: Implement the realtime TCP client**

Implement:

- dial lifecycle
- auth handshake using `ProtoClientEnvelope.auth`
- background read loop
- background heartbeat ticker
- `SendChatMessage`
- typed event channel for auth, ack, inbound message, disconnect, and errors

- [ ] **Step 3: Run the focused TCP client tests**

Run:

```bash
cd apps/CheeseBox && go test ./internal/transport/tcpim -run TestClient
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add apps/CheeseBox/internal/transport/tcpim/client.go apps/CheeseBox/internal/transport/tcpim/client_test.go apps/CheeseBox/internal/domain/types.go
git commit -m "feat: add CheeseBox TCP IM client"
```

## Task 6: Add the auth, roster, and chat services plus central app store

**Files:**
- Create: `apps/CheeseBox/internal/service/auth_service.go`
- Create: `apps/CheeseBox/internal/service/auth_service_test.go`
- Create: `apps/CheeseBox/internal/service/roster_service.go`
- Create: `apps/CheeseBox/internal/service/roster_service_test.go`
- Create: `apps/CheeseBox/internal/service/chat_service.go`
- Create: `apps/CheeseBox/internal/service/chat_service_test.go`
- Create: `apps/CheeseBox/internal/store/app_store.go`
- Create: `apps/CheeseBox/internal/store/app_store_test.go`

- [ ] **Step 1: Write the failing service and store tests**

Cover:

- auth service: `access token -> ws-ticket -> tcp auth`
- roster service: initial load into typed lists
- chat service: create optimistic message, send, apply ack, append inbound message
- app store: active nav, active conversation, message map, toast, connection status
- recent-conversation fallback: maintain local recent-opened conversations and last-message summary state when the chats list must be derived client-side

Run:

```bash
cd apps/CheeseBox && go test ./internal/service ./internal/store
```

Expected: FAIL because these packages do not exist.

- [ ] **Step 2: Implement the services and store**

Implement:

- `AuthService.Login`
- `AuthService.Reconnect`
- `RosterService.LoadInitialData`
- `ChatService.OpenConversation`
- `ChatService.SendText`
- `ChatService.HandleRealtimeEvent`
- `ChatService` and `AppStore` reducers that upsert local recent conversations on open, send ack, and inbound message
- `AppStore` selectors for `Chats` tab ordering and unread-summary presentation

Keep transport dependencies behind small interfaces so UI tests can use fakes.

- [ ] **Step 3: Run the focused service/store tests**

Run:

```bash
cd apps/CheeseBox && go test ./internal/service ./internal/store
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add apps/CheeseBox/internal/service apps/CheeseBox/internal/store
git commit -m "feat: add CheeseBox services and app store"
```

## Task 7: Build the login page and main three-column Bubble Tea shell

**Files:**
- Create: `apps/CheeseBox/internal/ui/theme.go`
- Create: `apps/CheeseBox/internal/ui/messages.go`
- Create: `apps/CheeseBox/internal/ui/login_model.go`
- Create: `apps/CheeseBox/internal/ui/login_model_test.go`
- Create: `apps/CheeseBox/internal/ui/app_model.go`
- Create: `apps/CheeseBox/internal/ui/app_model_test.go`
- Create: `apps/CheeseBox/internal/ui/help_view.go`
- Modify: `apps/CheeseBox/cmd/cheesebox/main.go`

- [ ] **Step 1: Write the failing UI tests**

Cover:

- login form default values and submit message
- main app navigation switching with `c`, `f`, `g`
- `Settings` tab rendering current connection/config summary
- focus cycling with `Tab`
- movement with `j` / `k` and arrow keys
- opening a conversation from friends/groups
- `Esc` leaving input focus
- `?` opening help
- `q` quitting
- rendering status bar and toast text

Run:

```bash
cd apps/CheeseBox && go test ./internal/ui
```

Expected: FAIL because the UI models do not exist yet.

- [ ] **Step 2: Implement the Bubble Tea models**

Implement:

- login form with text inputs
- app shell with top status, left nav, middle list, right chat area
- helper renderers for message viewport and help overlay
- command dispatch from login into async service calls

- [ ] **Step 3: Run the focused UI tests**

Run:

```bash
cd apps/CheeseBox && go test ./internal/ui
```

Expected: PASS

- [ ] **Step 4: Run a full package test pass**

Run:

```bash
cd apps/CheeseBox && go test ./...
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/cmd/cheesebox/main.go apps/CheeseBox/internal/ui
git commit -m "feat: add CheeseBox Bubble Tea shell"
```

## Task 8: Wire history loading, slash commands, manual reconnect, and end-to-end polish

**Files:**
- Modify: `apps/CheeseBox/internal/service/chat_service.go`
- Modify: `apps/CheeseBox/internal/store/app_store.go`
- Modify: `apps/CheeseBox/internal/ui/app_model.go`
- Modify: `apps/CheeseBox/internal/ui/app_model_test.go`
- Modify: `apps/CheeseBox/README.md`
- Modify: `apps/CheeseBox/arch.md`

- [ ] **Step 1: Write the failing integration-focused UI/service tests**

Cover:

- opening a conversation loads recent history into the viewport
- `/addfriend <userId> [message]` triggers the HTTP adapter
- `r` triggers reconnect and updates status transitions
- send failure and history failure show the right toast
- inbound message updates the `Chats` tab summary and unread placeholder

Run:

```bash
cd apps/CheeseBox && go test ./internal/service ./internal/ui -run 'Test(History|Slash|Reconnect|Toast)'
```

Expected: FAIL because these interactions are not wired yet.

- [ ] **Step 2: Implement the final app wiring**

Implement:

- history load on conversation open
- slash-command parser with `/addfriend`
- manual reconnect command path
- final README run instructions
- `arch.md` appendix linking the real implementation files

- [ ] **Step 3: Run the focused tests**

Run:

```bash
cd apps/CheeseBox && go test ./internal/service ./internal/ui -run 'Test(History|Slash|Reconnect|Toast)'
```

Expected: PASS

- [ ] **Step 4: Run the final verification suite**

Run:

```bash
cd apps/CheeseBox && go test ./...
```

Expected: PASS

- [ ] **Step 5: Manual smoke against local server**

Run:

```bash
cd apps/CheeseBox && go run ./cmd/cheesebox
```

Expected:

- login form appears
- valid access token can fetch `ws-ticket`
- TCP auth succeeds
- friends list loads
- groups list loads
- a conversation can open and send a text message
- an inbound message updates the active conversation or chats summary

- [ ] **Step 6: Commit**

```bash
git add apps/CheeseBox/README.md apps/CheeseBox/arch.md apps/CheeseBox/internal/service/chat_service.go apps/CheeseBox/internal/store/app_store.go apps/CheeseBox/internal/ui/app_model.go apps/CheeseBox/internal/ui/app_model_test.go
git commit -m "feat: finish CheeseBox chat workflow"
```
