# Web Client Envelope Protocol Alignment Design

## Goal

Refactor the web client to use the same unified gateway protocol model as the server-side protocol refactor from March 23, 2026: `ClientEnvelope` for outbound messages, `ServerEnvelope` for inbound messages, and `CommandType` as the only transport command discriminator.

This removes the browser-side `WSMessage.msgType` model entirely. The web client should stop carrying a protocol-translation compatibility layer and instead speak the same `command/requestId/body` shape that the gateway already uses internally.

## Current State

The web client still has a legacy transport contract:

- `src/transport/wsMessage.ts` defines `msgType`, `operationID`, and hard-coded WebSocket integer constants.
- `src/transport/wsClient.ts` speaks those integer message types directly.
- `src/services/realGatewayClient.ts` duplicates another similar `WsEnvelope` model and maps integer `msgType` values to `GatewayEvent`.

On the server side, the protocol contract has already shifted:

- `common-api/.../ClientEnvelope.java`
- `common-api/.../ServerEnvelope.java`
- `common-core/.../CommandType.java`

The gateway now dispatches handlers and outbound traffic by `CommandType`, not by the old browser-only numeric `WSMessage` shape.

## Chosen Approach

Use a transport-layer-first migration:

1. Introduce TypeScript transport models that mirror the server contract:
   - `CommandType`
   - `ClientEnvelope<T>`
   - `ServerEnvelope<T>`
2. Rewrite WebSocket transport code to send and receive only envelope JSON.
3. Keep the existing app-facing `GatewayEvent` contract stable where possible.
4. Add a narrow mapping layer from `ServerEnvelope` to `GatewayEvent`, so conversation state and UI do not need a broad rewrite.
5. Delete the old `wsMessage.ts` message-type contract once the new transport tests pass.

This keeps the protocol boundary clean without turning the work into a whole front-end architecture rewrite.

## Architecture

### 1. Transport Protocol Model

Create browser-side protocol types under the web app transport layer:

- `CommandType` TS enum or literal map aligned to the server numeric codes
- `ClientEnvelope<T>`
- `ServerEnvelope<T>`

All gateway sends and receives in the browser must use:

- `command`
- `requestId`
- `body`

The browser must stop constructing or interpreting:

- `msgType`
- `operationID`
- `data`

### 2. Outbound Command Builders

Replace `buildConnectRequest`, `buildAuthRequest`, `buildSendMessageRequest` in their old WSMessage form with envelope builders:

- `CONNECT`
- `AUTH`
- `HEARTBEAT`
- `CHAT_SEND`

These builders should live near the transport code and produce plain envelope JSON matching the server contract.

### 3. Inbound Envelope Mapping

`realGatewayClient.ts` should parse incoming JSON as `ServerEnvelope<unknown>` and branch on `command`.

The expected mapping boundary:

- `CHAT_RECV` -> `GatewayEvent.messageReceived` or other existing chat events (`typing`, `read`, `revoke`) based on payload content
- `FORCE_LOGOUT` -> `GatewayEvent.forceLogout`
- `ERROR` -> reject pending send or connection promises with normalized gateway errors
- `AUTH` response envelopes -> resolve or reject the initial connection/auth handshake depending on body semantics
- `HEARTBEAT` response envelopes -> maintain liveness only; no app-level event

The compatibility point is `GatewayEvent`, not a legacy wire format.

### 4. Request Tracking

Pending operations should continue to use `requestId` as the correlation key. That means:

- connect/auth/send requests use generated `requestId`
- inbound envelopes resolve/reject pending requests by `requestId`

The web client should not preserve the old `operationID` naming in its internal API.

## Error Handling

Error normalization should be tightened around envelope commands instead of raw integer types.

Rules:

- If `command === ERROR`, reject the matching pending request
- If auth/connect returns an error-shaped envelope, fail connection setup deterministically
- Unknown commands should not crash the app; they should be ignored or logged at the transport boundary
- Malformed envelope JSON should reject the affected pending flow and surface a transport error

## Testing Strategy

Use protocol contract tests to lock the browser to the server envelope model.

Required coverage:

1. Envelope builder tests
- `CONNECT`, `AUTH`, `CHAT_SEND` serialize as `command/requestId/body`

2. WebSocket transport tests
- successful connect/auth handshake using envelopes
- heartbeat send loop after auth success
- reconnect behavior after socket close
- force logout handling through `ServerEnvelope`

3. Real gateway client tests
- send path uses `ClientEnvelope`
- inbound `ServerEnvelope` values map to current `GatewayEvent`
- send ack and error flows resolve/reject by `requestId`

4. Residual integration tests
- keep current high-level service tests green so app-facing behavior stays stable

## Non-Goals

This change does not:

- redesign chat UI or state stores
- change REST service contracts
- introduce code generation from Java protocol classes
- keep a browser-side `WSMessage` compatibility adapter

## Acceptance Criteria

The work is complete when:

- the web client no longer defines or uses `msgType/operationID/data`
- WebSocket transport uses only `ClientEnvelope/ServerEnvelope`
- browser `CommandType` values match the server contract
- existing gateway-facing app behavior still works through the `GatewayEvent` abstraction
- tests cover envelope serialization and transport behavior
