# Go SDK And CheeseBox Restructure Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract a reusable Go IM client SDK into `sdks/go` and refactor CheeseBox to consume it, including reconnect sync, push gap repair, and read snapshot synchronization against the new CheeseIM APIs.

**Architecture:** Move protocol, auth, HTTP, TCP, and sync state machine logic out of `apps/CheeseBox` into a standalone SDK with domain-oriented packages. Keep CheeseBox as a pure TUI app that maps SDK bootstrap data and realtime events into Bubble Tea store/view state. Roll out the sync path incrementally: first SDK transport/auth/social APIs, then sync state, then CheeseBox integration and cleanup.

**Tech Stack:** Go 1.25, Bubble Tea, protobuf, custom TCP IM transport, CheeseIM HTTP APIs, seq-based sync state machine

---

## File Map

### Create

- `sdks/go/go.mod`
- `sdks/go/types/types.go`
- `sdks/go/types/events.go`
- `sdks/go/transport/httpapi/client.go`
- `sdks/go/transport/httpapi/client_test.go`
- `sdks/go/transport/tcpim/frame.go`
- `sdks/go/transport/tcpim/frame_test.go`
- `sdks/go/transport/tcpim/client.go`
- `sdks/go/transport/tcpim/client_test.go`
- `sdks/go/auth/service.go`
- `sdks/go/auth/service_test.go`
- `sdks/go/social/service.go`
- `sdks/go/social/service_test.go`
- `sdks/go/sync/service.go`
- `sdks/go/sync/service_test.go`
- `sdks/go/client/client.go`
- `sdks/go/client/client_test.go`

### Modify

- `apps/CheeseBox/go.mod`
- `apps/CheeseBox/cmd/cheesebox/main.go`
- `apps/CheeseBox/internal/domain/types.go`
- `apps/CheeseBox/internal/domain/types_test.go`
- `apps/CheeseBox/internal/store/app_store.go`
- `apps/CheeseBox/internal/store/app_store_test.go`
- `apps/CheeseBox/internal/ui/root_model.go`
- `apps/CheeseBox/internal/ui/root_model_test.go`
- `apps/CheeseBox/internal/ui/app_model.go`
- `apps/CheeseBox/internal/ui/app_model_test.go`
- `apps/CheeseBox/README.md`
- `apps/CheeseBox/arch.md`

### Delete After Migration

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
- `apps/CheeseBox/internal/service/contact_service.go`
- `apps/CheeseBox/internal/service/contact_service_test.go`

---

### Task 1: Scaffold SDK Module

**Files:**
- Create: `sdks/go/go.mod`
- Create: `sdks/go/types/types.go`
- Create: `sdks/go/types/events.go`
- Test: `sdks/go/types/types_test.go`

- [ ] **Step 1: Write the failing type tests**

Add tests that assert the SDK exports:
- `BootstrapData`
- `Conversation`
- `Friend`
- `Group`
- `Message`
- `ReadSnapshot`
- `Event`

Use compile-time construction tests similar to:

```go
func TestBootstrapDataShape(t *testing.T) {
	_ = types.BootstrapData{
		Friends: []types.Friend{{UserID: "u100"}},
		Groups: []types.Group{{GroupID: "g100"}},
		Conversations: []types.Conversation{{ConversationID: "s:u100:u200"}},
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache GOMODCACHE=/tmp/cheeseim-sdk-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./types
```

Expected: FAIL because SDK module and exported types do not exist yet.

- [ ] **Step 3: Write minimal SDK type definitions**

Create focused domain structs only. Do not include Bubble Tea view fields.

- [ ] **Step 4: Run test to verify it passes**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sdks/go/go.mod sdks/go/types
git commit -m "feat: scaffold go im sdk types"
```

---

### Task 2: Move HTTP API Client Into SDK

**Files:**
- Create: `sdks/go/transport/httpapi/client.go`
- Create: `sdks/go/transport/httpapi/client_test.go`
- Reference: `apps/CheeseBox/internal/transport/httpapi/client.go`

- [ ] **Step 1: Write the failing HTTP client tests**

Cover:
- login
- ws-ticket
- list friends
- list groups
- list conversations
- get max seqs
- get read snapshots
- sync pull
- ack read seq
- add friend

Use `httptest.Server` and assert exact request paths and methods.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache GOMODCACHE=/tmp/cheeseim-sdk-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./transport/httpapi
```

Expected: FAIL because the package does not exist.

- [ ] **Step 3: Implement the SDK HTTP client**

Port and adapt the CheeseBox client, but align it to the new server API:
- `/api/im/conversations/max-seqs`
- `/api/im/conversations/read-snapshots`
- `/api/im/conversations/sync/pull`
- `/api/im/conversations/{conversationId}/read-seq`

Map directly into SDK `types`, not CheeseBox summaries.

- [ ] **Step 4: Run test to verify it passes**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sdks/go/transport/httpapi
git commit -m "feat: add go sdk http client"
```

---

### Task 3: Move TCP IM Transport Into SDK

**Files:**
- Create: `sdks/go/transport/tcpim/frame.go`
- Create: `sdks/go/transport/tcpim/frame_test.go`
- Create: `sdks/go/transport/tcpim/client.go`
- Create: `sdks/go/transport/tcpim/client_test.go`
- Reference: `apps/CheeseBox/internal/transport/tcpim/*`

- [ ] **Step 1: Copy the current frame and client tests into SDK**

Keep behavior identical:
- request ID validation
- connect/auth flow
- incoming message event decoding
- disconnect/error propagation

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache GOMODCACHE=/tmp/cheeseim-sdk-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./transport/tcpim
```

Expected: FAIL because package and moved code do not exist.

- [ ] **Step 3: Move frame and TCP client into SDK**

Keep the package protocol behavior unchanged. Only strip CheeseBox-specific imports or domain types.

- [ ] **Step 4: Run test to verify it passes**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sdks/go/transport/tcpim
git commit -m "feat: move tcp im transport into sdk"
```

---

### Task 4: Add SDK Auth Service

**Files:**
- Create: `sdks/go/auth/service.go`
- Create: `sdks/go/auth/service_test.go`
- Reference: `apps/CheeseBox/internal/service/auth_service.go`

- [ ] **Step 1: Write failing auth service tests**

Cover:
- `Login`
- `LoginWithToken`
- `Reconnect`
- `Events`

Verify it wires:
- access token issuance
- ws-ticket issuance
- TCP connect/auth

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache GOMODCACHE=/tmp/cheeseim-sdk-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./auth
```

Expected: FAIL because package does not exist.

- [ ] **Step 3: Implement the SDK auth service**

Return SDK session types, not CheeseBox-specific session structs.

- [ ] **Step 4: Run test to verify it passes**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sdks/go/auth
git commit -m "feat: add sdk auth service"
```

---

### Task 5: Add SDK Social Service

**Files:**
- Create: `sdks/go/social/service.go`
- Create: `sdks/go/social/service_test.go`
- Reference: `apps/CheeseBox/internal/service/roster_service.go`
- Reference: `apps/CheeseBox/internal/service/contact_service.go`

- [ ] **Step 1: Write failing social service tests**

Cover:
- bootstrap queries
- list friends
- list groups
- list conversations
- add friend

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache GOMODCACHE=/tmp/cheeseim-sdk-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./social
```

Expected: FAIL because package does not exist.

- [ ] **Step 3: Implement the SDK social service**

Keep it focused on server I/O and domain mapping.

- [ ] **Step 4: Run test to verify it passes**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sdks/go/social
git commit -m "feat: add sdk social service"
```

---

### Task 6: Add SDK Sync State Machine

**Files:**
- Create: `sdks/go/sync/service.go`
- Create: `sdks/go/sync/service_test.go`

- [ ] **Step 1: Write failing sync service tests**

Cover:
- bootstrap initializes `syncedMaxSeqs`
- reconnect sync pulls when `serverMax > localMax`
- push gap repair triggers pull for missing ranges
- read snapshot sync updates unread state
- mark read calls `read-seq`

Use fakes for:
- HTTP sync API
- realtime event source

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache GOMODCACHE=/tmp/cheeseim-sdk-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./sync
```

Expected: FAIL because package does not exist.

- [ ] **Step 3: Implement minimal sync state machine**

Include:
- in-memory `syncedMaxSeqs`
- `BootstrapState`
- `SyncAll`
- `HandleRealtimeMessage`
- `OpenConversation`
- `MarkRead`

Do not add local DB in this phase.

- [ ] **Step 4: Run test to verify it passes**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sdks/go/sync
git commit -m "feat: add sdk conversation sync state machine"
```

---

### Task 7: Add Unified SDK Client Facade

**Files:**
- Create: `sdks/go/client/client.go`
- Create: `sdks/go/client/client_test.go`

- [ ] **Step 1: Write failing client facade tests**

Cover:
- `Login`
- `Reconnect`
- `Bootstrap`
- `OpenConversation`
- `SendText`
- `Events`

Expected shape: one compositional client that uses auth, social, sync, and transport internally.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache GOMODCACHE=/tmp/cheeseim-sdk-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./client
```

Expected: FAIL because package does not exist.

- [ ] **Step 3: Implement the SDK client facade**

Expose app-friendly but UI-agnostic operations.

- [ ] **Step 4: Run test to verify it passes**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sdks/go/client
git commit -m "feat: add sdk client facade"
```

---

### Task 8: Point CheeseBox Module At SDK

**Files:**
- Modify: `apps/CheeseBox/go.mod`
- Modify: `apps/CheeseBox/cmd/cheesebox/main.go`

- [ ] **Step 1: Write a failing integration compile target**

Set up the module to import the new SDK client packages from `sdks/go`.

- [ ] **Step 2: Run module compilation to verify it fails before edits**

Run:

```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./...
```

Expected: FAIL after removing old internal service wiring references.

- [ ] **Step 3: Update module and main wiring**

Wire:
- HTTP base URL
- TCP client
- SDK client
- pass SDK client into UI root

- [ ] **Step 4: Run tests to verify compilation progresses**

Run the same command.

Expected: different, more local failures in UI integration paths only.

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/go.mod apps/CheeseBox/cmd/cheesebox/main.go
git commit -m "refactor: wire cheesebox to sdk client"
```

---

### Task 9: Refactor CheeseBox RootModel To Use SDK

**Files:**
- Modify: `apps/CheeseBox/internal/ui/root_model.go`
- Modify: `apps/CheeseBox/internal/ui/root_model_test.go`

- [ ] **Step 1: Rewrite root model tests around SDK client fakes**

Cover:
- login success
- bootstrap data mapped into store
- open conversation loads synced messages
- send text
- realtime gap repair updates store
- reconnect reloads bootstrap and sync state

- [ ] **Step 2: Run UI tests to verify they fail**

Run:

```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./internal/ui
```

Expected: FAIL because `RootModel` still depends on old auth/roster/chat/contact services.

- [ ] **Step 3: Rewrite `RootModel` to depend on SDK client**

Move all IM workflow orchestration to the SDK:
- login
- bootstrap
- open conversation
- realtime event listening
- reconnect
- mark read when opening active conversation

- [ ] **Step 4: Run UI tests to verify they pass**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/internal/ui/root_model.go apps/CheeseBox/internal/ui/root_model_test.go
git commit -m "refactor: move cheesebox root workflow onto sdk"
```

---

### Task 10: Adapt CheeseBox Domain And Store Mapping

**Files:**
- Modify: `apps/CheeseBox/internal/domain/types.go`
- Modify: `apps/CheeseBox/internal/domain/types_test.go`
- Modify: `apps/CheeseBox/internal/store/app_store.go`
- Modify: `apps/CheeseBox/internal/store/app_store_test.go`

- [ ] **Step 1: Write failing mapping/store tests**

Cover:
- SDK conversation -> UI summary mapping
- SDK message -> UI message item mapping
- read snapshot unread updates
- touch active conversation clears unread locally

- [ ] **Step 2: Run store/domain tests to verify they fail**

Run:

```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./internal/domain ./internal/store
```

Expected: FAIL because the current types assume old service response shapes.

- [ ] **Step 3: Implement mapping-safe app state**

Keep app state focused on TUI projection only.

- [ ] **Step 4: Run tests to verify they pass**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/internal/domain apps/CheeseBox/internal/store
git commit -m "refactor: align cheesebox store with sdk domain"
```

---

### Task 11: Remove Old CheeseBox Transport And Service Layers

**Files:**
- Delete: `apps/CheeseBox/internal/transport/httpapi/client.go`
- Delete: `apps/CheeseBox/internal/transport/httpapi/client_test.go`
- Delete: `apps/CheeseBox/internal/transport/tcpim/frame.go`
- Delete: `apps/CheeseBox/internal/transport/tcpim/frame_test.go`
- Delete: `apps/CheeseBox/internal/transport/tcpim/client.go`
- Delete: `apps/CheeseBox/internal/transport/tcpim/client_test.go`
- Delete: `apps/CheeseBox/internal/service/auth_service.go`
- Delete: `apps/CheeseBox/internal/service/auth_service_test.go`
- Delete: `apps/CheeseBox/internal/service/roster_service.go`
- Delete: `apps/CheeseBox/internal/service/roster_service_test.go`
- Delete: `apps/CheeseBox/internal/service/chat_service.go`
- Delete: `apps/CheeseBox/internal/service/chat_service_test.go`
- Delete: `apps/CheeseBox/internal/service/contact_service.go`
- Delete: `apps/CheeseBox/internal/service/contact_service_test.go`

- [ ] **Step 1: Remove old imports and update references**

Make sure no CheeseBox package still imports deleted internal service/transport code.

- [ ] **Step 2: Run full CheeseBox test suite to verify any remaining references fail**

Run:

```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./...
```

Expected: FAIL only if a remaining old reference was missed.

- [ ] **Step 3: Finish cleanup**

Delete obsolete files and fix remaining imports.

- [ ] **Step 4: Run full CheeseBox test suite to verify it passes**

Run the same command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A apps/CheeseBox sdks/go
git commit -m "refactor: extract go im sdk and slim cheesebox app"
```

---

### Task 12: Update Docs And Usage

**Files:**
- Modify: `apps/CheeseBox/README.md`
- Modify: `apps/CheeseBox/arch.md`

- [ ] **Step 1: Update runtime and architecture docs**

Document:
- CheeseBox now depends on `sdks/go`
- sync API usage
- reconnect and gap repair behavior
- how future apps should consume the SDK

- [ ] **Step 2: Run a docs sanity check**

Manually inspect both files for stale references to internal CheeseBox transport/service packages.

- [ ] **Step 3: Commit**

```bash
git add apps/CheeseBox/README.md apps/CheeseBox/arch.md
git commit -m "docs: document sdk-based cheesebox architecture"
```

---

### Task 13: Final Verification

**Files:**
- Verify: `sdks/go/...`
- Verify: `apps/CheeseBox/...`

- [ ] **Step 1: Run SDK tests**

```bash
cd sdks/go
GOCACHE=/tmp/cheeseim-sdk-gocache GOMODCACHE=/tmp/cheeseim-sdk-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./...
```

Expected: PASS.

- [ ] **Step 2: Run CheeseBox tests**

```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./...
```

Expected: PASS.

- [ ] **Step 3: Run real startup smoke**

```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go run ./cmd/cheesebox
```

Expected: TUI starts, login flow works, bootstrap succeeds, and realtime events still connect.

- [ ] **Step 4: Commit final fixes if needed**

```bash
git add -A
git commit -m "test: verify sdk-backed cheesebox flow"
```
