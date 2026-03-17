# CheeseIM Flutter and Web Client Design

## Background

The existing `CheeseIM/postoffice` module already exposes two gateway protocols:

- TCP custom binary protocol for mobile and desktop style clients
- WebSocket JSON protocol for browser clients

The current request is to build a simple but usable first client release aligned with the rebuilt CheeseIM gateway design:

- a reusable Dart TCP SDK
- a Flutter client using that SDK
- a Web client using WebSocket

The first release must support:

- login with manual connection parameters and token
- a future business-login adapter entry point
- single chat message send/receive
- conversation list and unread aggregation
- heartbeat and connection keepalive
- automatic reconnect
- message ACK and delivery-state presentation
- basic error feedback

## Goals

- Reuse `postoffice` as the single online access gateway
- Keep the first release intentionally small and testable
- Avoid coupling UI code to raw TCP/WS protocol details
- Keep Flutter mobile and desktop support in the same client codebase
- Let Web connect directly to the current `WSMessage` protocol when possible

## Non-Goals

- group chat
- contacts and social graph
- media/file messages
- history pagination and server-side history pull
- local database persistence
- rich read receipt UX
- introducing a separate BFF or proxy service

## Current Backend Constraints

From `postoffice`, the relevant transport contracts are:

- TCP: `CheeseMessage` with fixed 32-byte header and JSON payload
- WebSocket: `WSMessage` with `msgType`, `operationID`, `data`, and optional metadata

Current default ports in `postoffice/src/main/resources/application.yml`:

- WebSocket: `5147`
- TCP: `5148`

The backend should remain the source of truth for authentication, online route refresh, and message delivery orchestration. This project may add only thin compatibility fixes to `postoffice` if needed, without changing core message semantics.

## Recommended Architecture

### Option Summary

Three candidate implementation strategies were considered:

1. Put all transport and state logic directly in each client
2. Build client-side transport SDK layers and keep app layers thin
3. Add a new backend adapter/BFF and simplify both clients

The recommended approach is option 2 because it preserves delivery speed while preventing transport/state logic from being duplicated across UI code.

### Final Structure

Create four main units:

1. `packages/im_tcp_sdk`
   A pure Dart package for the TCP protocol, connection state machine, reconnect policy, heartbeat, ACK tracking, and typed events.

2. `apps/im_flutter_client`
   A Flutter app for Android, iOS, macOS, and Windows. It consumes `im_tcp_sdk` and only owns presentation, view state, and input flows.

3. `apps/im_web_client`
   A browser client built on WebSocket. It wraps `WSMessage` in a small frontend transport adapter and keeps protocol details out of the page components.

4. `CheeseIM/postoffice`
   Existing gateway service. Prefer direct compatibility with the current TCP and WS protocols. Only introduce thin compatibility changes when client implementation reveals concrete protocol friction.

## Client Domain Model

Both clients should expose the same domain-level concepts even though they use different transports.

### Core Entities

- `AuthSession`
  - server address / URL
  - user ID
  - platform ID
  - token
  - optional business-login provider metadata

- `ConversationSummary`
  - conversation ID
  - session type
  - peer user ID
  - last message preview
  - last message time
  - unread count

- `ChatMessageItem`
  - local ID
  - client message ID
  - optional server message ID
  - sender ID
  - receiver ID
  - content
  - content type
  - send time
  - delivery status

- `ConnectionSnapshot`
  - lifecycle state
  - last heartbeat success time
  - reconnect attempt count
  - last error

### Delivery Status Model

The first release only needs a compact, reliable user-facing status set:

- `sending`
- `sent`
- `failed`
- `received`

Interpretation:

- `sending`: local optimistic message created, waiting for response
- `sent`: gateway acknowledged by `SEND_MSG_RESP`
- `failed`: request error, timeout, or reconnect loss before confirmation
- `received`: inbound message notify accepted into local conversation state

This model is intentionally smaller than the protocol surface area and can later expand to include read/played/recalled states.

## Transport Design

### Flutter / Dart TCP SDK

The Dart SDK owns:

- binary encode/decode for `CheeseMessage`
- socket lifecycle
- partial-frame buffering for half-packet / sticky-packet cases
- request correlation by `operationID`
- auth bootstrap
- heartbeat timer
- reconnect backoff
- typed transport and domain event streams

The SDK should not contain Flutter UI code or persistence logic.

### Web WebSocket Adapter

The Web client owns a small adapter that:

- serializes outbound `WSMessage`
- parses inbound JSON messages
- maps `msgType` values to typed frontend events
- keeps request/response correlation by `operationID`
- runs heartbeat and reconnect logic consistent with Flutter behavior

The adapter should directly consume the current `postoffice` `WSMessage` format whenever possible. If a concrete gap appears, the preferred fix is a thin server compatibility adjustment, not a protocol redesign.

## Connection Lifecycle State Machine

Both clients should share the same lifecycle model:

- `idle`
- `connecting`
- `connected`
- `authenticating`
- `ready`
- `reconnecting`
- `closed`

### Lifecycle Rules

1. Client starts in `idle`
2. User login or auto-resume starts `connecting`
3. Low-level socket established moves to `connected`
4. Client sends transport connect request, then auth request, and moves to `authenticating`
5. Auth success moves to `ready`
6. `ready` starts heartbeat scheduling and normal message handling
7. Socket loss or repeated heartbeat failure moves to `reconnecting`
8. Explicit force logout or auth rejection moves to `closed`

### Reconnect Policy

Use exponential backoff with capped steps:

- 1s
- 2s
- 5s
- 10s
- 20s

Reconnect retains:

- last valid auth session
- unsatisfied request tracking needed for UI completion
- conversation and message view state already loaded in memory

Reconnect does not retain:

- raw socket instance
- expired or rejected auth assumptions after force logout

If the server explicitly rejects auth or sends force logout, the client must stop reconnecting and surface the reason to the user.

## Request / Response / Notify Semantics

### Shared Design

Both transports are normalized into three categories:

- request
- response
- notify

Each request must create a unique `operationID`. Message send requests also create a unique `clientMsgID`.

### Send Message Flow

1. User composes text and submits
2. Client creates local optimistic message with status `sending`
3. Client sends outbound request
4. Gateway replies with send response
5. Client matches by `operationID` or `clientMsgID`
6. Client fills `serverMsgID` and marks the message as `sent`

### Receive Message Flow

1. Gateway pushes inbound notify
2. Client maps payload into `ChatMessageItem`
3. Client deduplicates by `serverMsgID`, falling back to `clientMsgID` when needed
4. Client inserts into target conversation
5. Client increments unread count if the conversation is not active

### ACK Handling

The first release treats ACK primarily as delivery confirmation and duplicate control:

- outbound ACK is represented by successful send response handling
- inbound notify acceptance updates local delivery state
- reconnect recovery must not create duplicate messages

If backend support for richer delivery or read acknowledgements already exists, the clients should be designed so the domain model can grow without reworking the presentation layer.

## Error Handling

Errors should be normalized into categories understood by the UI:

- authentication error
- validation error
- permission error
- network error
- protocol parse error
- server internal error

### Handling Rules

- authentication error: stop reconnect, clear ready state, return user to login context
- validation error: fail only the current action
- permission error: fail only the current action and keep the connection if the socket is still valid
- network error: enter reconnect flow and preserve in-memory UI state
- protocol parse error: drop the bad packet and log it; if repeated, force reconnect
- internal error: fail the current action and show a generic error state

## App UX Scope

The first release should use a compact three-screen structure in both clients.

### Login Screen

Required inputs:

- host and port for TCP client, or full WS URL for Web client
- user ID
- platform ID
- token

Also include:

- a manual login action for immediate testing
- a reserved adapter boundary for future business-login integration

Do not implement a full real business login flow in this first release. Only define the seam where such a provider plugs in later.

### Conversation Screen

Show:

- conversation list
- last message preview
- unread count
- connection status indicator

For the first release, conversation summaries can be derived from locally observed messages and send results. No dedicated backend conversation API is required.

### Chat Screen

Show:

- text message list
- local send-in-progress state
- send success / failure state
- manual retry for failed messages
- reconnect banner or status

## Platform-Specific UI Behavior

### Flutter

Use one Flutter app for Android, iOS, macOS, and Windows.

- narrow layout: stack navigation between conversation list and chat view
- wide layout: split-pane conversation list and message panel

UI implementation should remain transport-agnostic and subscribe to a view model or controller layer instead of operating directly on sockets.

### Web

Prefer a desktop-first layout that still adapts to narrow screens.

- wide layout: left conversation rail plus right chat pane
- narrow layout: switch between list view and conversation view

The Web app should match Flutter semantics closely enough that later shared product behavior remains predictable.

## Backend Compatibility Policy

Default policy:

- do not redesign the gateway protocol
- do not introduce a new proxy service
- keep `postoffice` as the direct integration point

Allowed backend work:

- add small compatibility fixes for current client protocol friction
- add message-shape helpers if they preserve existing semantics
- improve protocol documentation where current behavior is ambiguous

Disallowed in this scope:

- reworking delivery core architecture
- adding a new dedicated frontend aggregation service
- expanding beyond single chat first-release requirements

## Testing Strategy

### Dart TCP SDK

Must cover:

- `CheeseMessage` encode/decode
- packet fragmentation and reassembly
- message type mapping
- connection lifecycle transitions
- heartbeat timeout behavior
- reconnect backoff logic
- request correlation and timeout handling

### Flutter Client

Must cover:

- login form behavior
- connection status rendering
- optimistic send state
- send failure and retry flow
- conversation unread aggregation
- layout behavior for narrow and wide screens where practical

### Web Client

Must cover:

- `WSMessage` mapping
- connection store transitions
- optimistic message send state
- inbound message deduplication
- conversation aggregation

### Integration Verification

Local integration should verify:

- Web client can connect to `postoffice` WebSocket on port `5147`
- Flutter client can connect to `postoffice` TCP on port `5148`
- auth succeeds with a valid token
- single chat send/receive works across reconnect boundaries

## Delivery Sequence

Implementation should proceed in this order:

1. Dart TCP SDK foundation
2. Flutter app shell and state wiring
3. Web transport adapter and app shell
4. Thin backend compatibility fixes only if integration exposes a concrete issue

This order reduces protocol risk first, then builds presentation on top of a stable transport layer.

## Open Decisions Already Resolved

The design has already fixed these points:

- first release level: include reconnect, ACK/delivery state, and basic error feedback
- Flutter targets: Android, iOS, macOS, and Windows
- login mode: support manual token login now and keep a future business-login adapter seam
- Web protocol strategy: choose the lowest-risk path, preferring direct `WSMessage` compatibility with thin backend adjustment only when necessary

## Final Recommendation

Proceed with a layered client design:

- reusable Dart TCP SDK
- Flutter app consuming the SDK
- Web client with a parallel WebSocket adapter
- minimal, compatibility-only backend changes

This gives the project a usable first client release without locking transport rules, reconnection logic, and message semantics inside UI code.
