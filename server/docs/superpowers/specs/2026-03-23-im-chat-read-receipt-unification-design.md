# IM Chat Read Receipt Unification Design

## Goal

Unify read receipt handling onto the chat command path and remove the dedicated receipt handler/event pipeline. At the same time, unify TCP and WebSocket business message models so transport differences stay in codec layers only, and replace broad integer/string constants with explicit enums wherever the type is known.

## Scope

This design covers:

- Routing read receipts through the chat command path with `READ_CURSOR` semantics
- Removing `ReceiptMessageHandler` and the async receipt topic flow
- Unifying TCP and WebSocket inbound/outbound business message models
- Refactoring `MessageConstants` type-like constants into enums

This design does not cover:

- Backward compatibility for legacy `msgType=2004` clients
- Multi-stage compatibility rollout
- Broader non-message protocol redesign outside the current command set

## Current Problems

### Duplicate read receipt entry points

The system currently has two separate receipt paths:

- A dedicated receipt protocol path: `msgType=2004/TCP 33 -> ReceiptMessageHandler -> receipt topic -> ReceiptEventListener -> ReceiptAckRpc`
- A chat message path: `msgType=2001 + contentType=2004 -> ChatMessageHandler -> MessageSendRpcImpl -> ingress`

These paths are not semantically equivalent. The dedicated receipt path updates read state based on client-supplied `READ_CURSOR(seq)` semantics. The chat path currently treats `contentType=2004` as a special message type and applies message-routing defaults rather than receipt semantics.

### Protocol and business model are mixed

TCP messages are converted into `WSMessage` so business handlers can be reused. This leaks a transport-specific model into shared handler logic. In practice, both TCP and WebSocket connections hold a Netty `Channel`; only the framing/encoding layer differs.

### Type systems rely on raw integers and strings

The codebase uses raw values for:

- command/message types
- content types
- session types
- receipt types
- error codes

This causes semantic ambiguity. The clearest current example is `2004`, which appears in both protocol command type space and content type space even though those are different layers.

## Design Summary

### Chosen direction

Use a single transport-agnostic business envelope for inbound and outbound messages. TCP and WebSocket codecs map frames to that envelope and back. Business handlers dispatch by an enum-backed command type. Read receipts enter through the chat command, but `ContentType.READ_RECEIPT` is handled as a read-state update use case, not as a normal message send.

### Key outcomes

- `ReceiptMessageHandler` is removed
- `TopicNames.RECEIPT`, `GatewayReceiptPublisher`, and `ReceiptEventListener` are removed
- `READ_CURSOR(seq)` semantics are preserved
- `READ_RECEIPT` no longer allocates a new message sequence
- TCP/WS business handling no longer depends on `WSMessage`
- explicit enums replace type-like constants across the main flow

## Architecture

### Transport layer

Introduce transport-specific codecs only:

- `WsTransportCodec`
- `TcpTransportCodec`

Responsibilities:

- WebSocket frame or TCP byte frame decoding
- JSON/binary payload extraction
- conversion into a transport-agnostic business envelope
- encoding outbound business envelopes back into transport frames

No business routing decisions happen here.

### Unified business envelope

Introduce transport-agnostic message models:

- `ClientEnvelope`
- `ServerEnvelope`

Suggested shape:

```java
public final class ClientEnvelope {
    private CommandType command;
    private String requestId;
    private Object body;
}
```

```java
public final class ServerEnvelope {
    private CommandType command;
    private String requestId;
    private Object body;
}
```

Handlers and connection pipeline operate only on these envelope types.

### Command dispatch

Replace transport-specific message type dispatch with `CommandType` dispatch:

- `CONNECT`
- `AUTH`
- `HEARTBEAT`
- `CHAT_SEND`
- `CHAT_RECV`
- `CHAT_REVOKE`
- `ERROR`

The inbound server handlers for both TCP and WebSocket should decode transport frames into `ClientEnvelope`, then delegate to a shared command handler factory keyed by `CommandType`.

### Chat send branching

`ChatCommandHandler` becomes the single entry point for chat-originated client operations. It must branch by `ContentType`:

- non-receipt content -> `ChatMessageService`
- `ContentType.READ_RECEIPT` -> `ConversationReceiptService`

This keeps the client entry path unified without misclassifying read state updates as sent chat messages.

## Read Receipt Design

### Required semantics

The chosen semantics are:

- the client sends a chat command
- the content type is `READ_RECEIPT`
- the payload expresses `READ_CURSOR`
- the client explicitly provides the conversation and target sequence to mark as read

This is not modeled as a new persisted message.

### Payload model

Introduce a strong receipt payload DTO:

```java
public final class ReadReceiptPayload {
    private ReceiptType receiptType;
    private String conversationId;
    private Long seq;
}
```

For this refactor, `ReceiptType.READ_CURSOR` is the primary supported operation. If delivered/received receipts remain needed later, they should be handled by the same receipt service boundary rather than by reintroducing a separate protocol entry point.

### Processing flow

1. Client sends `CommandType.CHAT_SEND`
2. `ChatCommandHandler` parses chat body
3. `contentType == ContentType.READ_RECEIPT`
4. Handler validates receipt payload and authenticated actor
5. Handler calls `ConversationReceiptService.applyReadCursor(...)`
6. Service updates conversation read state directly
7. Handler returns success response without publishing a normal ingress event

### Explicit non-goals for read receipt flow

When `contentType == READ_RECEIPT`, the system must not:

- allocate a new message sequence
- publish a normal ingress message event
- persist message history
- update conversation last-message summary
- increase unread counts
- send normal message delivery events

This is the critical semantic difference from the current chat content-type path.

## Services

### ChatMessageService

Responsible for normal message send behavior:

- ingress event publication
- conversation sequence allocation through the existing message path
- history persistence flow
- online delivery flow
- conversation/unread/last-message state mutation

### ConversationReceiptService

Responsible for read state mutation:

- validate receipt payload
- validate actor vs connection context
- update `userReadSeq(userId, conversationId)`
- optionally support future receipt side effects through the same service boundary

This service replaces the role previously split across `ReceiptMessageHandler`, `ReceiptEventListener`, and `ReceiptAckRpcImpl` for read cursor updates.

### Receipt persistence/application boundary

The current `ReceiptAckRpcImpl` contains the core state mutation logic but is named around a transport/RPC concern. Refactor its responsibility into a better-named domain service, for example:

- `ConversationReceiptService`
- `ReadStateService`

The exact class name may follow local naming conventions, but the design intent is to make read-state application a direct business service rather than a leftover RPC adapter.

## Enum Refactor

### New enums

Introduce explicit enums for type-like domains:

- `CommandType`
- `ContentType`
- `SessionType`
- `ReceiptType`
- `ErrorCode`

Potential future enums may include:

- `NotificationType`
- `ConnectionState` normalization if more protocol-specific values appear

### Enum rules

Each enum should:

- own its protocol/storage code value
- provide `fromCode(...)` lookup
- fail fast for unknown values at boundaries
- be used directly in core business logic

Boundary adapters may still translate raw ints/strings from network payloads, Redis, Mongo, or RPC DTOs.

### MessageConstants migration

`MessageConstants` should stop owning type-like domain values. Migrate the following to enums:

- `CONTENT_TYPE_*`
- `SESSION_TYPE_*`
- `ERR_CODE_*`

Keep only true global constants that are not better represented as enums, such as selected Redis key prefixes if they remain useful there.

### Preferred usage rule

Where the domain is known, code should use enums rather than primitive values. Raw integer/string codes are allowed only:

- at transport codecs
- at DTO compatibility boundaries
- in persistence serialization/deserialization helpers

## TCP / WebSocket Model Unification

### Current boundary

Today TCP is translated into `WSMessage` to reuse handlers. This is a code reuse trick rather than a correct model boundary.

### Target boundary

The target structure is:

- WebSocket frame -> `ClientEnvelope`
- TCP frame -> `ClientEnvelope`
- shared command handling
- `ServerEnvelope` -> WebSocket frame
- `ServerEnvelope` -> TCP frame

This keeps transport-specific frame differences isolated to codecs.

### Handler impact

Shared server-side business handlers should no longer refer to:

- `WSMessage`
- `WSMessageType`
- `CheeseMessageType`

except inside transport codecs or compatibility glue scheduled for deletion.

The current `WebSocketServerHandler` and `CheeseServerHandler` should converge toward a shared inbound dispatch path once decoding is complete.

## Data Flow

### Normal chat message

1. transport frame decoded into `ClientEnvelope`
2. `CommandType.CHAT_SEND` dispatched
3. `contentType != READ_RECEIPT`
4. `ChatMessageService` publishes ingress event
5. existing message pipeline handles sequencing/history/delivery/state updates
6. success response encoded back to transport

### Read cursor update

1. transport frame decoded into `ClientEnvelope`
2. `CommandType.CHAT_SEND` dispatched
3. `contentType == READ_RECEIPT`
4. `ReadReceiptPayload` validated
5. `ConversationReceiptService` updates `userReadSeq`
6. success response encoded back to transport

No normal message ingress/delivery/history processing occurs.

## Error Handling

### Boundary parsing

Codecs must reject:

- unknown command codes
- malformed body payloads
- content types that cannot be mapped

These should produce explicit protocol errors using `ErrorCode`.

### Receipt validation

Read receipt handling must reject:

- missing `conversationId`
- missing `seq`
- unsupported receipt type
- unauthenticated or invalid session
- attempts to apply receipts to a conversation the actor should not mutate

### Unknown enum values

Unknown enum code/string inputs should fail at the boundary rather than silently flowing into the handler layer as raw primitives.

## Migration Plan

### Phase 1: enum and envelope introduction

- add enums with code mapping helpers
- add `ClientEnvelope` / `ServerEnvelope`
- add payload DTOs for chat and read receipt
- add transport codec conversion helpers

### Phase 2: command handler migration

- adapt handler factory to dispatch by `CommandType`
- update auth/heartbeat/chat handlers to consume unified envelopes
- remove dependence on `WSMessage` in shared business logic

### Phase 3: receipt path switch

- add `ConversationReceiptService`
- branch `ChatCommandHandler` on `ContentType.READ_RECEIPT`
- bypass normal ingress flow for read receipts
- update tests to assert no new message seq/history/delivery side effects

### Phase 4: delete legacy receipt path

- remove `ReceiptMessageHandler`
- remove `GatewayReceiptPublisher`
- remove `ReceiptEventListener`
- remove receipt topic references and dead code

### Phase 5: constants cleanup

- replace content/session/error raw constants with enums across main modules
- keep boundary translation utilities for codec and persistence

## Testing Strategy

### Transport codec tests

- WebSocket frame decodes to expected `ClientEnvelope`
- TCP frame decodes to the same `ClientEnvelope`
- outbound `ServerEnvelope` encodes correctly for both transports

### Handler routing tests

- `CHAT_SEND + TEXT` routes to normal message service
- `CHAT_SEND + READ_RECEIPT` routes to receipt service
- unknown command/content types fail fast

### Receipt behavior tests

- `READ_CURSOR(seq)` updates `userReadSeq`
- no ingress event is published for read receipts
- no new sequence is allocated for read receipts
- no history record is written
- no unread count is incremented
- no last-message summary is updated

### Regression tests

- normal chat send behavior remains unchanged
- TCP and WebSocket both exercise the same business handler path
- old dedicated receipt protocol type is rejected after cutover

## Risks

### Risk: accidental semantic regression

If `READ_RECEIPT` continues through the normal message pipeline by mistake, the system will still allocate a new sequence and mutate message state incorrectly. Tests must explicitly guard against this.

### Risk: enum migration churn

Replacing raw constants with enums will touch many modules. This should be done in a focused pass with boundary adapters to avoid mixing partial enum usage and raw codes in the same core paths.

### Risk: transport refactor breadth

TCP/WS model unification affects core gateway handling. The implementation should preserve transport codec boundaries and migrate handlers incrementally behind stable tests.

## Acceptance Criteria

- clients send read cursor updates through chat command path only
- `ReceiptMessageHandler` and receipt topic pipeline are removed
- read cursor updates do not create normal message side effects
- TCP and WebSocket share a single transport-agnostic business envelope
- business handlers do not depend on `WSMessage` or `CheeseMessage`
- content/session/error/receipt type logic uses enums in core paths

