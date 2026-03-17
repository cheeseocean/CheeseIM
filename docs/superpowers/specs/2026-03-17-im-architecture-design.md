# IM Architecture Design

## Context

CheeseIM is a Gradle multi-module Java 17 repository organized around IM service boundaries. The relevant existing modules are:

- `postoffice`: gateway and long-connection access
- `postman`: message delivery and routing
- `postbox`: message storage
- `push`: offline push
- `common`: shared DTOs and infrastructure code

The current repository structure already matches the target system split, so the design should refine module boundaries and core flows instead of introducing a new service layout.

## Goal

Design a Dubbo + Netty IM architecture for large-scale production use with these constraints:

- Conversation types: single chat, group chat, system notification
- Delivery semantics: near exactly-once through idempotency, deduplication, and compensation
- Device model: full multi-device sync for the same account
- Offline model: inbox persistence, push trigger, unread badge sync, recall/read backfill after reconnect
- Scale target: around 1 million concurrent users and 1 billion+ daily messages

## Non-Goals

To keep the design focused and plannable, the following are explicitly out of scope for this spec:

- Audio/video calling
- End-to-end encryption key management
- Search, moderation, and back-office workflows inside the core message path
- Social recommendation or non-IM product features

## Refined Requirements

### Functional Requirements

- Support single chat, group chat, and system notification conversations
- Support message send, online delivery, offline storage, reconnect pull, ack, read receipt, and recall
- Synchronize message and conversation state across all online devices of the same user
- Maintain conversation ordering semantics suitable for chat UX
- Trigger vendor push when the target user is offline or not confirmed online in time

### Reliability Requirements

- Every client request carries a client-generated idempotency key
- Every accepted message receives a server-generated message ID
- Duplicate sends, retries, and repeated event consumption must converge to one logical message outcome
- Temporary infrastructure failure must not cause irreversible message loss
- Delivery timeouts must be detectable and compensable

### Performance and Operations Requirements

- Services must scale horizontally
- Hot users, hot conversations, and hot groups must be isolatable and rate-limited
- Core message lifecycle must be traceable end-to-end
- Failure states must be visible through metrics, logs, and replayable task records

## Architecture Options

### Option A: Centralized Delivery Core

`postoffice` handles only transport, while all routing, storage coordination, deduplication, and push decisions happen in one strongly centralized delivery service.

Pros:

- Clear ownership of consistency
- Simplest reasoning model for near exactly-once behavior

Cons:

- Delivery service becomes very heavy very quickly
- Harder to scale cleanly at million-connection and billion-message targets

### Option B: Lightweight Gateway Routing + Reliable Delivery Core

`postoffice` owns connections and online route state, while `postman` owns message truth, idempotency, delivery state machine, compensation, and orchestration with storage and push.

Pros:

- Good balance between latency and consistency
- Fits the current repository module split
- Scales better than a fully centralized runtime while keeping consistency logic concentrated

Cons:

- Boundary between gateway online state and delivery truth must be designed carefully

### Option C: Fully Event-Driven Partitioned IM

All message handling is modeled around Kafka-partitioned event streams keyed by conversation or user, with storage and push derived from events.

Pros:

- Strong scalability characteristics
- Natural fit for ordered processing within partitions

Cons:

- Highest implementation and operational complexity
- Easy to over-engineer before the first reliable production version exists

## Recommended Approach

Choose Option B as the primary architecture, while borrowing partitioning ideas from Option C.

This means:

- `postoffice` stays focused on connection lifecycle and online route registration
- `postman` becomes the reliability core
- `postbox` becomes the recoverable message and inbox foundation
- `push` remains a downstream touchpoint service rather than a source of truth
- Kafka is used for asynchronous fanout, buffering, and compensation, not as the only business truth model

This is the best fit for the stated requirements because it preserves clear module responsibilities while keeping the hardest consistency problems in one place.

## System Architecture

### Layers

- Access layer: `postoffice`
- Reliable delivery layer: `postman`
- Storage layer: `postbox`
- Touch layer: `push`
- Shared infrastructure: Redis, Kafka, MongoDB, Dubbo

### Service Responsibilities

#### postoffice

- Netty connection handling
- Authentication and session binding
- Heartbeat and disconnect handling
- Protocol encode/decode and request normalization
- Local connection registry and route publication
- Basic rate limiting and invalid request rejection

Not responsible for:

- Final delivery semantics
- Persistent message truth
- Cross-device state convergence

#### postman

- Send idempotency validation
- Receiver resolution for single, group, and system messages
- Delivery state machine ownership
- Online fanout orchestration
- Storage orchestration with `postbox`
- Timeout scanning and compensation
- Ack, read receipt, and recall state convergence

This is the control plane of the message lifecycle.

#### postbox

- Message fact persistence
- User inbox projection persistence
- Offline pull and roaming read model
- Partial state persistence used for recovery and reconciliation

The storage model should separate message facts from user-facing inbox projections to avoid overloading one collection with incompatible access patterns.

#### push

- Vendor push orchestration
- Push deduplication and rate limiting
- Device and channel-aware push strategy
- Push result callback and failure feedback

Push success is not treated as read success or final message success. It is only a delivery attempt signal.

## Core Data Flow

### Send Path

1. Client sends a message request with `clientMsgId`
2. `postoffice` authenticates, normalizes the request, and forwards it to `postman`
3. `postman` checks idempotency and generates `serverMsgId`
4. `postman` persists the recoverable message through `postbox`
5. `postman` resolves recipients and online device routes
6. `postman` fans out to online devices through `postoffice`
7. Unconfirmed or offline targets enter inbox and push workflows

### State Return Path

1. Client sends receive ack, displayed ack, read receipt, or recall request
2. `postoffice` forwards the normalized event to `postman`
3. `postman` advances the delivery state machine
4. `postman` updates necessary projections in `postbox` and conversation state systems
5. `postman` suppresses stale push attempts or schedules recovery work as needed

## Internal Subsystems Required

These are logical subsystems. They may start as packages or components inside existing modules, but they must stay clearly separated by responsibility.

### Online Route Center

- Tracks `userId -> deviceId -> gateway node/channel`
- Supports fast lookup and expiration
- Must tolerate stale route entries and gateway crashes

### Delivery State Machine

Suggested high-level states:

- `INIT`
- `PERSISTED`
- `ROUTED`
- `ONLINE_DELIVERING`
- `ONLINE_CONFIRMED`
- `INBOXED`
- `PUSH_TRIGGERED`
- `READ`
- `RECALLED`
- `FAILED_RECOVERABLE`
- `FAILED_FINAL`

State transitions must be explicit and auditable.

### Idempotency and Deduplication

- Deduplicate client retries by `clientMsgId + sender + conversation`
- Deduplicate downstream repeated events by event ID or delivery task key
- Ensure handlers are safe under Dubbo retry, gateway replay, and Kafka redelivery

### Conversation State Aggregator

- Maintains latest message preview
- Maintains unread count
- Maintains ordering key
- Maintains mention and notification flags
- Maintains cross-device convergence inputs

This should not be mixed casually into raw message storage writes.

### Compensation Scheduler

- Scans for timeout or incomplete delivery states
- Replays online delivery
- Triggers push fallback
- Moves unrecoverable tasks to dead-letter or manual investigation queues

### Push Decision Engine

- Determines whether to push
- Determines which device/channel to push
- Cancels useless push after reconnect or cross-device confirmation

## Key Technical Challenges

### Near Exactly-Once Delivery

Exactly-once cannot be guaranteed end-to-end across Netty, Dubbo, Kafka, Redis, and MongoDB. The design therefore must approximate exactly-once through:

- Stable IDs
- Idempotent APIs
- Explicit state machine transitions
- Replay-safe consumers
- Reconciliation and compensation

### Multi-Device State Convergence

All online devices receive the same logical message, but ack, read, and recall events may race. The design must define conflict resolution rules and version ordering so stale device events do not overwrite newer state.

### Ordering Semantics

- Single chat should maintain per-conversation ordering
- Group chat should prefer group sequence ordering with scalable asynchronous fanout

The spec does not require total global order. It requires user-visible conversation order that remains stable under retry and reconnect.

### Online/Offline Boundary

Users may disconnect during delivery. The system must avoid these failure modes:

- online delivered but not persisted recoverably
- inbox persisted but push never triggered
- push triggered after another device has already confirmed delivery

### Group Fanout at Scale

Large groups cannot rely on naive per-member synchronous writes. The design should use:

- group-level message sequencing
- asynchronous member fanout
- hot-group isolation
- backpressure and batching

### Conversation Projection Hotspots

Unread count, latest message, mention state, pinning, and sorting can become a bigger write hotspot than raw messages. These updates require separate design and must support delayed reconciliation where exact immediate projection is too expensive.

### Push Feedback Loop

Push providers do not provide uniform semantics. Push handling must be modeled as best-effort touch, with local deduplication, rate limiting, and cancellation logic.

### Recoverability and Observability

A production-grade IM system must support:

- message lifecycle tracing by message ID
- replay of failed tasks
- dead-letter visibility
- queue lag monitoring
- route-state inconsistency detection

## Data Model Guidance

### Message

Purpose:

- Immutable fact record for a logical message

Suggested keys:

- `serverMsgId`
- `conversationId`
- `senderId`
- `messageType`
- `sendTime`
- `conversationSeq`

### Inbox

Purpose:

- User-facing recoverable view of messages by conversation

Suggested access key:

- `userId + conversationId + seq`

### DeliveryTask

Purpose:

- Per-target or per-device delivery tracking

Fields should include:

- task ID
- target user/device
- current state
- retry count
- last error
- next retry time

### SessionRoute

Purpose:

- Current online route mapping

Fields should include:

- `userId`
- `deviceId`
- `gatewayNodeId`
- `channelId`
- `lastHeartbeatTime`

### ConversationState

Purpose:

- Aggregated conversation projection

Fields should include:

- latest message preview
- unread count
- pinned status
- notification preference
- mention flag
- state version

### PushTask

Purpose:

- Offline push attempt record

Fields should include:

- target device or vendor token
- channel
- dedup key
- retry policy
- attempt result

## Scaling and Partitioning Guidance

- Partition single-chat and conversation-sensitive flows by `conversationId`
- Partition user inbox projections by `userId`
- Treat large-group fanout as a dedicated scalable workflow rather than a normal point-to-point path
- Keep partition strategy stable early, because later migration is expensive

## Failure Handling

### Recoverable Failures

- gateway transient unavailability
- Dubbo timeout
- Kafka redelivery
- temporary Mongo write failure
- push provider timeout

Handling principle:

- persist recovery intent
- retry idempotently
- surface lag and retry pressure through metrics

### Non-Recoverable or Escalated Failures

- malformed message payload after validation boundary
- illegal conversation target
- irreconcilable state machine violation

Handling principle:

- reject early if possible
- record diagnostic detail
- move task to dead-letter or manual intervention path when necessary

## Testing Strategy

### Chain Validation

- send
- persist
- online deliver
- offline inbox fallback
- push trigger
- reconnect pull

### Consistency Validation

- client retry with same `clientMsgId`
- Dubbo retry
- Kafka repeated consumption
- duplicate ack event
- cross-device read and recall races

### Load Validation

- hotspot single chat
- large-group fanout
- queue backlog
- Redis route churn
- Mongo slow write behavior

### Failure Drills

- gateway node crash
- delivery node restart
- temporary Kafka congestion
- Redis route loss
- push provider failure

## Risks

- `postman` can become a monolith if internal subsystem boundaries are not enforced
- Group fanout can dominate cost and latency earlier than expected
- Conversation projection updates can become the real bottleneck instead of message facts
- Incorrect Kafka partitioning will be very expensive to change later
- Mixing message truth and aggregation state in one storage model will cause write amplification and repair difficulty

## Recommended First Planning Boundaries

This design is still broad enough that implementation planning should be broken into sequential workstreams:

1. Message lifecycle contract and state machine
2. Gateway route model and online delivery path
3. Postbox persistence model for message fact and inbox projection
4. Ack/read/recall convergence
5. Offline push and cancellation loop
6. Large-group fanout and hotspot control
7. Observability, replay, and compensation tooling

These workstreams are related, but they are coherent enough to plan incrementally under one architecture.

Recommended planning order:

- First define the message contract, IDs, sequencing rules, and state machine
- Then define the gateway route model and the synchronous online delivery path
- Then define durable storage and replay/recovery semantics
- Then add cross-device state convergence for ack, read, and recall
- Then add push fallback and cancellation
- Finally address large-group scaling and observability hardening

## Final Recommendation

Proceed with a reliability-centered IM architecture where:

- `postoffice` is transport and online-route focused
- `postman` is the consistency and orchestration core
- `postbox` stores recoverable truth and inbox projections
- `push` is a best-effort touch service with feedback

This design is focused, implementable, and aligned with the current repository structure. It is intentionally conservative about where truth lives and explicit about where complexity belongs.
