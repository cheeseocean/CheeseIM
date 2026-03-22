# Cheese IM Full Refactor Design

**Date:** 2026-03-22

**Goal:** Replace the current IM architecture in a single branch with a clean target design based on `docs/architecture/refactor`, without backward-compatibility layers, and remove legacy, expired, and redundant designs during the refactor.

## Source Baseline

The design baseline is [docs/architecture/refactor/im_design_final.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/docs/architecture/refactor/im_design_final.md).

The other files in `docs/architecture/refactor` are treated as supporting drafts:

- `im_skeleton.md`: shared module and package skeleton
- `im_detail_design.md`: contract and topic detail
- `im_task_design.md`: milestone/task breakdown
- `im_mongo_design.md`: Mongo history model detail

This refactor uses `im_design_final.md` as the final architecture source and folds in the useful parts of the supporting drafts where they do not conflict.

## Confirmed Constraints

- Single-branch, one-shot replacement
- No compatibility layer required
- History storage is also replaced in the same refactor
- `common` is split into `common-core` and `common-api`

## Section 1: Target Module Architecture

The final module layout is:

- `common-core`
- `common-api`
- `postoffice`
- `postbox`
- `postman`
- `push`

### `common-core`

Contains pure shared primitives only:

- constants
- enums
- Redis key builders
- topic names
- conversation ID helpers
- block index helpers
- common error codes
- basic response and paging models

### `common-api`

Contains cross-service contracts only:

- Dubbo RPC interfaces
- request/response DTOs
- Kafka event DTOs
- shared message and conversation DTOs

### Business Modules

- `postoffice`: access layer and online session manager
- `postbox`: message ingress RPC, history persistence consumer, history/query RPC
- `postman`: orchestration center
- `push`: online dispatch orchestration and offline push

### Cleanup Rule

Current shared classes under `common` that mix old DTOs, old entities, old APIs, and new async flow contracts are removed or relocated into the two new module responsibilities. Cross-service protocol duplication is not allowed after the refactor.

## Section 2: Message Flow and Responsibility Boundaries

### Main Message Flow

`Client -> postoffice -> postbox(MessageSendRpc) -> Kafka ingress -> postman -> Kafka history + Kafka delivery -> postbox(history consumer) + push(delivery consumer) -> postoffice(dispatch) -> client`

### Offline Push Flow

`push(delivery consumer) -> Kafka offlinepush -> push(offline consumer) -> vendor push channel`

### `postoffice`

Responsibilities:

- WebSocket / HTTP / TCP access
- authentication
- connection binding
- heartbeat and disconnect cleanup
- online state maintenance
- call `postbox` send RPC
- expose online dispatch RPC to `push`

Non-responsibilities:

- no direct Kafka publishing for message ingress
- no seq allocation
- no history persistence
- no message policy decision

### `postbox`

Responsibilities:

- send message RPC
- generate `conversationId`
- generate `serverMsgId`
- ingress idempotency preprocessing
- build and publish `IngressEvent`
- consume `history`
- persist Mongo history blocks
- expose history and conversation query RPC

Non-responsibilities:

- no orchestration
- no online dispatch
- no online state truth

### `postman`

Responsibilities:

- consume `ingress`
- group by `conversationId`
- run `MessagePolicyEngine`
- assign conversation seq
- write Redis hot state
- initialize and update conversation state
- build `HistoryEvent`
- build `DeliveryEvent`
- fan out to `history` and `delivery`

Non-responsibilities:

- no long-connection access
- no direct Mongo history writes
- no vendor push calls

### `push`

Responsibilities:

- consume `delivery`
- resolve target users and online routes
- call `postoffice.dispatchMessage(...)`
- interpret dispatch results
- generate `OfflinePushEvent` when needed
- consume `offlinepush`
- call APNs / FCM / vendor adapters

Non-responsibilities:

- no connection truth ownership
- no seq allocation
- no conversation hot-state mutation

## Section 3: Shared Contract Model

### `common-core`

Suggested package contents:

- `constants/TopicNames`
- `constants/RedisKeys`
- `constants/ErrorCodes`
- `enums/SessionType`
- `enums/MessageStatus`
- `util/ConversationIdUtil`
- `util/BlockIndexUtil`
- `model/BaseResponse`
- `model/PageQuery`
- `model/PageResult`

### `common-api`

Suggested package contents:

- `rpc/MessageSendRpc`
- `rpc/MessageQueryRpc`
- `rpc/OnlineDispatchRpc`
- `dto/message/SendMessageReq`
- `dto/message/SendMessageResp`
- `dto/message/MessageOptions`
- `dto/message/SequencedMessage`
- `dto/dispatch/DispatchMessageReq`
- `dto/dispatch/DispatchMessageResp`
- `dto/dispatch/DispatchPayload`
- `dto/dispatch/DispatchResult`
- `dto/query/...`
- `event/IngressEvent`
- `event/HistoryEvent`
- `event/DeliveryEvent`
- `event/OfflinePushEvent`

### Unified Message Policy

`MessageOptions` is the single policy carrier:

- `needHistory`
- `needConversation`
- `needUnreadCount`
- `needOnlinePush`
- `needOfflinePush`
- `senderSync`
- `notification`
- `needLastMessage`

### Explicit Removals

The following old shared models should be deleted or replaced directly instead of kept as compatibility shims:

- old `common.dto.DeliveryCommand`
- old `common.dto.IngressEvent`
- old `common.dto.HistoryTask`
- old `common.dto.MessageProto`
- old `common.dto.OfflinePushTask`
- old `common.entity.Message`
- old `common.entity.StoredMessage`
- old `common.entity.InboxMessage`
- old `common.entity.DeliveryTask`
- old `common.entity.DeliveryState`
- old service contracts whose responsibilities do not match the new architecture, including `MessageDeliveryService`, `MessageStoreService`, `MessagePushService`, and `GatewayPushService`

### Contract Rule

- Only `common-api` DTOs may cross service boundaries
- Internal persistence models stay in their owning module
- Internal entities must not be reused as RPC or Kafka contracts

## Section 4: Redis Hot Data and Mongo History Model

### Redis Hot Data

Redis stores hot-path state and short-term message cache only.

The canonical Redis key set is:

- `conv:maxSeq:{conversationId}`
- `conv:minSeq:{conversationId}`
- `conv:lastMsg:{conversationId}`
- `msg:{conversationId}:{seq}`
- `uc:read:{userId}:{conversationId}`
- `uc:min:{userId}:{conversationId}`
- `uc:max:{userId}:{conversationId}`
- `uc:unread:{userId}:{conversationId}`
- `idem:ingress:{conversationId}:{clientMsgId}`
- `idem:postman:{conversationId}:{clientMsgId}`
- `idem:delivery:{serverMsgId}:{userId}:{connectionId}`

`postman` is the only writer of conversation hot state.

### Mongo Collections

Mongo history is reduced to two collections only:

- `message_block`
- `message_id_mapping`

### `message_block`

Document identity:

- `_id = {conversationId}:{blockNo}`

Block rules:

- `BLOCK_SIZE = 100`
- `blockNo = (seq - 1) / BLOCK_SIZE`
- `index = (seq - 1) % BLOCK_SIZE`

Each block contains a fixed-size `messages` array so `seq -> block/index` lookup is O(1).

### `message_id_mapping`

Stores:

- `conversationId`
- `clientMsgId`
- `serverMsgId`
- `seq`
- `senderId`
- `sendTime`

Use cases:

- idempotent retry lookup
- recall/compensation/troubleshooting lookup
- duplicate history prevention

### Explicit Storage Cleanup

The current `postbox` storage model centered on `MessageDocument`, `InboxDocument`, and `ConversationReadCursorDocument` is removed as the source of truth. After refactor:

- history truth is in `message_block`
- read and conversation hot state live in Redis
- the old inbox-per-user history model is not retained

### Query Path

`postbox` query flow is:

1. Read Redis hot cache and conversation state first
2. Fill gaps from Mongo block history when needed
3. Assemble unified history/query DTO response

## Section 5: Orchestration and Delivery Rules

### `postman` Processing Order

For each `IngressEvent`, `postman` runs:

1. validate and hit postman idempotency
2. serialize processing by `conversationId`
3. evaluate `MessagePolicyEngine`
4. resolve target users
5. build `SequencedMessage`
6. write Redis hot state
7. build `HistoryEvent` when policy requires history
8. build `DeliveryEvent` when policy requires online/offline delivery
9. fan out to `history` and `delivery`

### Target Resolution

- direct chat: sender and receiver, with sender included depending on `senderSync`
- group chat: sender and group members, excluding users disallowed by policy

### `MessagePolicyEngine`

The policy engine answers:

- whether to persist history
- whether to update conversation projection
- whether to update unread count
- whether to perform online push
- whether to allow offline push
- whether sender sync is enabled
- whether to update last message

This makes transient, notification, and normal chat messages all flow through the same orchestration framework.

### `push` Processing Order

For each `DeliveryEvent`, `push` runs:

1. resolve target user online routes
2. group `connectionIds` by `userId`
3. call `OnlineDispatchRpc.dispatchMessage`
4. collect per-connection dispatch results
5. create `OfflinePushEvent` for users who need offline push because they are offline or online dispatch failed
6. offline consumer sends vendor push through adapters

### Fixed Decision Rules

- `needOnlinePush=false`: skip online dispatch
- `needOfflinePush=false`: do not produce offline push even on online failure
- `senderSync=true`: sender participates in delivery targets
- `notification=true`: use notification conversation semantics through the same orchestration engine
- `needHistory=false`: skip `history` fanout
- `needUnreadCount=false`: skip unread count mutation

### Integration Rule

`push` must not depend on `postoffice` internal implementation services such as route services directly. Interaction is through shared RPC contracts only. `postoffice` remains the owner of online-state truth.

## Cleanup Strategy

The refactor removes:

- redundant shared message models
- legacy sync delivery path remnants
- old inbox-style history persistence
- direct cross-module coupling to internal services
- expired/duplicated abstractions whose responsibility conflicts with the final design

The result should be a single, explicit architecture where each module owns one domain and all cross-service traffic is expressed through stable shared contracts.
