# IM Auth Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the target-state IM authentication system with separated login state, connection state, conversation authorization, resource authorization, and active session revocation.

**Architecture:** The implementation is split into five layers: common contracts, session/ticket issuing, gateway connection authentication, conversation/resource authorization, and active kickoff. WebSocket authentication is moved to one-time `ws_ticket`, identity is bound to `ConnectionContext`, and all sender identity and resource access checks are enforced server-side.

**Current Status Note:** `ws_ticket` issuing has now been fully收口到 `authcenter`; `postoffice` no longer exposes its own `/api/im/ws-ticket` fallback endpoint. `postbox` also now provides a real conversation list endpoint and short-lived attachment proxy download authorization.

**Tech Stack:** Java 17, Spring Boot, Dubbo, Redis, WebSocket, Gradle, MySQL/PostgreSQL

---

## File Structure

### New files

- `common/src/main/java/com/cheeseocean/im/common/api/session/SessionQueryDubboService.java`
- `common/src/main/java/com/cheeseocean/im/common/api/session/SessionIssueDubboService.java`
- `common/src/main/java/com/cheeseocean/im/common/api/session/SessionRevocationDubboService.java`
- `common/src/main/java/com/cheeseocean/im/common/api/permission/ConversationPermissionDubboService.java`
- `common/src/main/java/com/cheeseocean/im/common/api/permission/ResourcePermissionDubboService.java`
- `common/src/main/java/com/cheeseocean/im/common/api/connection/KickoffCommandDubboService.java`
- `common/src/main/java/com/cheeseocean/im/common/model/auth/SessionPrincipal.java`
- `common/src/main/java/com/cheeseocean/im/common/model/auth/WsTicketPrincipal.java`
- `common/src/main/java/com/cheeseocean/im/common/model/auth/ConnectionPrincipal.java`
- `common/src/main/java/com/cheeseocean/im/common/model/auth/PermissionCheckRequest.java`
- `common/src/main/java/com/cheeseocean/im/common/model/auth/PermissionCheckResult.java`
- `common/src/main/java/com/cheeseocean/im/common/model/auth/KickoffCommand.java`
- `common/src/main/java/com/cheeseocean/im/common/constants/RedisKeys.java`
- `common/src/main/java/com/cheeseocean/im/common/enums/SessionStatus.java`
- `common/src/main/java/com/cheeseocean/im/common/enums/ConnectionState.java`
- `common/src/main/java/com/cheeseocean/im/common/enums/ConversationAction.java`
- `common/src/main/java/com/cheeseocean/im/common/enums/KickoffReason.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/WsTicketAuthService.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/WsTicketAuthServiceImpl.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/SessionStateValidator.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionContext.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionBindService.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionRegistry.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/kickoff/KickoffSubscriber.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/kickoff/KickoffExecutor.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WsRequestType.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WsResponseCode.java`
- `postman/src/main/java/com/cheeseocean/im/postman/auth/MessageAuthFacade.java`
- `postman/src/main/java/com/cheeseocean/im/postman/auth/SenderIdentityResolver.java`
- `postman/src/main/java/com/cheeseocean/im/postman/permission/MessageSendPermissionChecker.java`
- `postman/src/main/java/com/cheeseocean/im/postman/model/SendMessageCommand.java`
- `postbox/src/main/java/com/cheeseocean/im/postbox/permission/ConversationPermissionDubboServiceImpl.java`
- `postbox/src/main/java/com/cheeseocean/im/postbox/permission/ResourcePermissionDubboServiceImpl.java`
- `postbox/src/main/java/com/cheeseocean/im/postbox/permission/ConversationPermissionService.java`
- `postbox/src/main/java/com/cheeseocean/im/postbox/permission/HistoryAccessService.java`
- `postbox/src/main/java/com/cheeseocean/im/postbox/permission/AttachmentAccessService.java`
- `postbox/src/main/java/com/cheeseocean/im/postbox/policy/DirectChatPolicy.java`
- `postbox/src/main/java/com/cheeseocean/im/postbox/policy/GroupChatPolicy.java`
- `postbox/src/main/java/com/cheeseocean/im/postbox/policy/ChannelPolicy.java`
- `docs/architecture/im-auth-design.md`

### Existing files to modify

- `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/AuthMessageHandler.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/HeartbeatMessageHandler.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionManager.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/model/UserConnection.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/server/WebSocketServerHandler.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/AuthService.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/JwtAuthService.java`
- `postman/src/main/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImpl.java`
- Any history-message controller/service files
- Any attachment download controller/service files

### Tests to add or update

- `common` model/interface serialization tests if present in repo patterns
- `postoffice` auth and connection tests
- `postman` send authorization tests
- `postbox` permission and resource access tests

## Task 1: Add shared auth contracts in `common`

**Files:**
- Create: `common/src/main/java/com/cheeseocean/im/common/api/session/SessionQueryDubboService.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/api/session/SessionIssueDubboService.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/api/session/SessionRevocationDubboService.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/api/permission/ConversationPermissionDubboService.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/api/permission/ResourcePermissionDubboService.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/api/connection/KickoffCommandDubboService.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/model/auth/*.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/constants/RedisKeys.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/enums/*.java`

- [x] **Step 1: Inspect existing `common` package layout and naming patterns**

Run: `rg --files common/src/main/java/com/cheeseocean/im/common`
Expected: Existing package conventions and serializable DTO style are visible.

- [x] **Step 2: Add the new API interfaces and DTOs**

Requirements:
- DTOs are `Serializable`
- Contracts contain no implementation logic
- Constants only define Redis key prefixes

- [x] **Step 3: Compile `common`**

Run: `./gradlew :common:compileJava`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add common/src/main/java/com/cheeseocean/im/common
git commit -m "feat: add IM auth shared contracts"
```

## Task 2: Replace direct JWT socket auth with `ws_ticket` flow in `postoffice`

**Files:**
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/WsTicketAuthService.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/WsTicketAuthServiceImpl.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/SessionStateValidator.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionContext.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionBindService.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/AuthMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/AuthService.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/auth/JwtAuthService.java`

- [x] **Step 1: Add the new `ConnectionContext` model**

Fields must include:
- `connId`
- `userId`
- `tenantId`
- `sessionId`
- `deviceId`
- `tokenVersion`
- `state`

- [x] **Step 2: Implement `WsTicketAuthService`**

Requirements:
- Consume ticket
- Reject missing, expired, or already-used ticket
- Validate session active status
- Validate user banned status
- Validate token version

- [x] **Step 3: Refactor `AuthMessageHandler` to accept ticket auth**

Requirements:
- `AUTH` only accepts `ticket`
- On success bind `ConnectionContext`
- On failure return auth error and close connection

- [x] **Step 4: Reduce `JwtAuthService` responsibility**

Requirements:
- Remove login-token-as-socket-auth responsibility
- If retained temporarily, mark as non-primary path and keep pure validation only

- [x] **Step 5: Compile `postoffice`**

Run: `./gradlew :postoffice:compileJava`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add postoffice/src/main/java/com/cheeseocean/im/postoffice
git commit -m "feat: add ws ticket auth for postoffice"
```

## Task 3: Bind authenticated identity to all socket operations

**Files:**
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionManager.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/model/UserConnection.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/server/WebSocketServerHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/HeartbeatMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`

- [x] **Step 1: Extend `UserConnection` to carry `ConnectionContext`**

Requirements:
- Pending connections exist before auth
- Authenticated identity is stored only after successful auth

- [x] **Step 2: Update `ConnectionManager` to manage context-aware connections**

Requirements:
- Track pending and authenticated states
- Keep session/device/user lookup indexes for authenticated connections
- Support lookup for kickoff by user, session, or device

- [x] **Step 3: Enforce auth state in `HeartbeatMessageHandler` and `ChatMessageHandler`**

Requirements:
- Reject business messages from unauthenticated connections
- Heartbeat updates `lastHeartbeatAt`
- Heartbeat can trigger disconnect when session invalidates

- [x] **Step 4: Remove sender identity trust from client messages**

Requirements:
- `ChatMessageHandler` derives sender identity from `ConnectionContext`
- Ignore any client-provided `senderId`

- [x] **Step 5: Compile `postoffice` and run targeted tests if present**

Run: `./gradlew :postoffice:compileJava`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add postoffice/src/main/java/com/cheeseocean/im/postoffice
git commit -m "feat: bind socket identity to connection context"
```

## Task 4: Add message send authorization in `postman`

**Files:**
- Create: `postman/src/main/java/com/cheeseocean/im/postman/auth/MessageAuthFacade.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/auth/SenderIdentityResolver.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/permission/MessageSendPermissionChecker.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/model/SendMessageCommand.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImpl.java`

- [x] **Step 1: Define `SendMessageCommand` with server-side sender fields**

Fields must include:
- `tenantId`
- `conversationId`
- `senderUserId`
- `senderSessionId`
- `senderDeviceId`
- `clientMsgId`
- `messageType`
- `body`

- [x] **Step 2: Add permission facade around conversation `SEND` checks**

Requirements:
- Call `ConversationPermissionDubboService.check`
- Reject unauthorized send before persistence or dispatch

- [x] **Step 3: Update message entrypoint to use `SendMessageCommand`**

Requirements:
- No business entrypoint trusts raw client sender identity
- Permission denial maps to stable error code

- [x] **Step 4: Compile `postman`**

Run: `./gradlew :postman:compileJava`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add postman/src/main/java/com/cheeseocean/im/postman
git commit -m "feat: add send message authorization"
```

## Task 5: Implement conversation authorization in `postbox`

**Files:**
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/permission/ConversationPermissionDubboServiceImpl.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/permission/ConversationPermissionService.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/policy/DirectChatPolicy.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/policy/GroupChatPolicy.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/policy/ChannelPolicy.java`

- [x] **Step 1: Inspect existing conversation, group, and membership models**

Run: `rg -n "conversation|group|channel|member|mute" postbox/src/main/java`
Expected: Existing repositories or entities to reuse are identified.

- [x] **Step 2: Implement a single service entry for conversation actions**

Requirements:
- Support `READ`
- Support `SEND`
- Support `RECALL`
- Support `UPLOAD_ATTACHMENT`

- [x] **Step 3: Split policy by conversation type**

Requirements:
- Direct chat checks participants and blacklist constraints
- Group checks membership and mute status
- Channel checks membership and write role

- [x] **Step 4: Compile `postbox`**

Run: `./gradlew :postbox:compileJava`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add postbox/src/main/java/com/cheeseocean/im/postbox
git commit -m "feat: add conversation authorization service"
```

## Task 6: Implement resource authorization for history and attachments

**Files:**
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/permission/ResourcePermissionDubboServiceImpl.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/permission/HistoryAccessService.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/permission/AttachmentAccessService.java`
- Modify: history message controller/service files
- Modify: attachment controller/service files

- [x] **Step 1: Implement history access checks**

Requirements:
- Validate conversation `READ`
- Support per-message or time-window filtering if current repo already models it

- [x] **Step 2: Implement attachment access checks**

Requirements:
- Resolve `attachment -> message -> conversation`
- Reuse conversation `READ` authorization
- Return deny when ownership chain is invalid

- [ ] **Step 3: Move attachment download to auth-then-sign flow**

Requirements:
- Download endpoint returns short-lived signed URL or equivalent storage token
- No permanent public attachment URL is exposed

- [x] **Step 4: Compile affected modules**

Run: `./gradlew :postbox:compileJava`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add postbox/src/main/java/com/cheeseocean/im/postbox
git commit -m "feat: add history and attachment authorization"
```

## Task 7: Add active revocation and kickoff execution

**Files:**
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/kickoff/KickoffSubscriber.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/kickoff/KickoffExecutor.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionManager.java`
- Add or modify session/security-state providers in auth/session domain

- [x] **Step 1: Define `KickoffCommand` in `common` if not already added**

Fields must include:
- `reason`
- `userId`
- `sessionId`
- `deviceId`

- [x] **Step 2: Implement connection lookup for user/session/device**

Requirements:
- Find all related authenticated connections
- Send `KICKOFF`
- Close channel cleanly

- [ ] **Step 3: Trigger revocation on password reset, ban, logout, and admin kick**

Requirements:
- Update session/security state first
- Publish or invoke kickoff command second

- [x] **Step 4: Compile `postoffice` and the auth/session provider module**

Run: `./gradlew :postoffice:compileJava`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add postoffice/src/main/java/com/cheeseocean/im/postoffice
git commit -m "feat: add active session revocation and kickoff"
```

## Task 8: Add observability and finalize docs

**Files:**
- Modify: relevant auth, gateway, permission, and resource service classes
- Modify: `docs/architecture/im-auth-design.md`

- [ ] **Step 1: Add structured logs for auth, deny, and kickoff events**

Fields:
- `request_id`
- `user_id`
- `session_id`
- `device_id`
- `conn_id`
- `conversation_id`
- `reason_code`

- [ ] **Step 2: Add metrics for auth and permission failures**

Metrics:
- ticket issue success/failure
- socket auth success/failure
- permission deny count
- kickoff count

- [x] **Step 3: Update docs if implementation details diverge from design**

Requirements:
- Keep `docs/architecture/im-auth-design.md` aligned with actual code

- [x] **Step 4: Run full compile verification**

Run: `./gradlew :common:compileJava :postoffice:compileJava :postman:compileJava :postbox:compileJava :push:compileJava :bootstrap-all:compileJava`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add docs/architecture/im-auth-design.md common postoffice postman postbox push
git commit -m "chore: add IM auth observability and docs"
```

## Acceptance Checklist

- [x] WebSocket only authenticates with `ws_ticket`
- [x] Authenticated connections store `uid/session_id/device_id/conn_id`
- [x] Sender identity always comes from `ConnectionContext`
- [x] Conversation `SEND` and `READ` are enforced server-side
- [x] History and attachment access use resource authorization
- [x] Session revoke actively disconnects matching connections
- [ ] Password change and ban invalidate active IM access quickly
- [x] All affected modules compile cleanly

## Suggested End-to-End Validation

- [x] Login and receive `access_token`
- [x] Exchange `access_token` for `ws_ticket`
- [ ] Authenticate WebSocket with `AUTH(ticket)`
- [ ] Send direct message successfully
- [ ] Reject forged `senderId`
- [ ] Reject group send from non-member
- [ ] Reject message send for muted member
- [x] Reject history read for removed member
- [x] Reject attachment download without resource permission
- [ ] Kick off connection after session revoke
