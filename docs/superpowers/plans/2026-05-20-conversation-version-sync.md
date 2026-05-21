# Conversation Version Sync Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add OpenIM-style conversation metadata incremental sync while keeping message sync based on conversation seq ranges.

**Architecture:** Server records per-user conversation metadata changes in `ConversationVersionLog`, exposes full/incremental sync through `ConversationService` and `api-server`, and lets clients persist a conversation sync cursor. Message reliability remains driven by maxSeq/readSeq and `PullMessages`.

**Tech Stack:** Java 17, Spring Boot, MongoTemplate, Dubbo, JUnit/Mockito, Go SDK, Bubble Tea CheeseBox.

---

### Task 1: Server Conversation Version Log

**Files:**
- Create: `server/common-api/src/main/java/com/cheeseocean/im/common/api/business/domain/ConversationVersionLog.java`
- Create: `server/common-api/src/main/java/com/cheeseocean/im/common/api/enums/ConversationVersionOperation.java`
- Create: `server/common-api/src/main/java/com/cheeseocean/im/common/api/dto/conversation/ConversationIncrementalSyncResult.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/conversation/ConversationVersionLogDoc.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/ConversationVersionLogRepository.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationVersionLogRepositoryImpl.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationServiceImpl.java`

- [x] Write failing repository and service tests.
- [x] Add domain, DTO, repository, and Mongo implementation.
- [x] Record INSERT/UPDATE logs from all current `ConversationServiceImpl` write paths.
- [x] Add `syncConversations(ownerUserId, versionId, version, idHash)` to `ConversationService`.

### Task 2: HTTP Incremental Sync API

**Files:**
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/model/response/ConversationIncrementalSyncResponse.java`
- Modify: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/ConversationController.java`
- Modify: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/facade/ConversationFacade.java`

- [x] Add controller/facade failing tests.
- [x] Expose `GET /api/im/conversations/sync/incremental`.
- [x] Map domain `UserConversation` to existing `ConversationResponse` for insert/update items.

### Task 3: Go SDK Conversation Version Cursor

**Files:**
- Modify: `sdks/go/types/types.go`
- Modify: `sdks/go/transport/httpapi/client.go`
- Modify: `sdks/go/social/service.go`
- Modify: `sdks/go/sync/service.go`
- Modify tests under `sdks/go/**`.

- [x] Add SDK tests for full/incremental response parsing and cursor persistence.
- [x] Add API client method for incremental conversation sync.
- [x] Keep message sync based on local synced seq, not server max seq.

### Task 4: CheeseBox Integration

**Files:**
- Modify: `apps/CheeseBox/internal/store/persisted_store.go`
- Modify: `apps/CheeseBox/internal/store/app_store.go`
- Modify: `apps/CheeseBox/internal/ui/root_model.go`

- [x] Persist conversation cursor and message seq metadata.
- [x] Initialize the existing Syncer or replace it with SDK sync events.
- [x] Ensure realtime messages flow through one sync path and are not appended twice.
