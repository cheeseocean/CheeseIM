# Web Client Envelope Protocol Alignment Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the web client WebSocket transport to use the server's unified `ClientEnvelope` / `ServerEnvelope` / `CommandType` protocol instead of the legacy browser-only `WSMessage.msgType` contract.

**Architecture:** Replace the legacy `wsMessage` transport model with a browser-side envelope model, route all WebSocket handshake and message sends through `command/requestId/body`, and keep app-facing behavior stable by mapping inbound `ServerEnvelope` values into the existing `GatewayEvent` abstraction. Scope stays inside the transport and gateway service layer plus their tests.

**Tech Stack:** TypeScript, Vite, Vitest, browser WebSocket API

---

## File Map

### Create

- `../apps/im_web_client/src/transport/envelope.ts`
  - Browser-side `CommandType`, `ClientEnvelope`, `ServerEnvelope`, command builders, and envelope helpers.

### Modify

- `../apps/im_web_client/src/transport/wsClient.ts`
  - Replace legacy `WSMessage` parsing/sending with envelope-only transport logic.
- `../apps/im_web_client/src/transport/sendMessageError.ts`
  - Classify errors from `CommandType.ERROR` and envelope bodies instead of `msgType`.
- `../apps/im_web_client/src/services/realGatewayClient.ts`
  - Switch send/receive flow to envelope protocol and keep `GatewayEvent` mapping stable.
- `../apps/im_web_client/src/test/wsClient.test.ts`
  - Replace `msgType`-based tests with envelope serialization/handshake tests.
- `../apps/im_web_client/src/test/realServices.test.ts`
  - Update gateway tests to assert `command/requestId/body`.

### Delete

- `../apps/im_web_client/src/transport/wsMessage.ts`
  - Remove the legacy `msgType/operationID/data` transport contract after migration.

## Task 1: Introduce Browser Envelope Types

**Files:**
- Create: `../apps/im_web_client/src/transport/envelope.ts`
- Modify: `../apps/im_web_client/src/test/wsClient.test.ts`

- [ ] **Step 1: Write the failing serialization tests**

Add tests in `../apps/im_web_client/src/test/wsClient.test.ts` that expect:

```ts
expect(payload).toEqual({
  command: 1,
  requestId: 'op-connect-1',
  body: {},
});
```

and equivalent assertions for:

```ts
expect(payload).toEqual({
  command: 10,
  requestId: 'op-auth-1',
  body: { ticket: 'wst_123' },
});
```

and:

```ts
expect(payload).toEqual({
  command: 30,
  requestId: 'op-send-1',
  body: {
    clientMsgID: 'client-1',
    recvID: 'u2',
    content: 'hello',
    contentType: 1,
    chatType: 1,
  },
});
```

- [ ] **Step 2: Run the serialization tests to verify they fail**

Run: `cd ../apps/im_web_client && npm test -- --run src/test/wsClient.test.ts`

Expected: FAIL because current transport builders still return `msgType/operationID/data`.

- [ ] **Step 3: Write minimal envelope transport types and builders**

Create `../apps/im_web_client/src/transport/envelope.ts` with:

```ts
export const commandTypes = {
  connect: 1,
  auth: 10,
  heartbeat: 20,
  chatSend: 30,
  chatRecv: 32,
  chatRevoke: 34,
  forceLogout: 35,
  error: 90,
} as const;

export interface ClientEnvelope<T = unknown> {
  command: number;
  requestId: string;
  body: T;
}

export interface ServerEnvelope<T = unknown> {
  command: number;
  requestId: string;
  body: T;
}
```

and builder helpers for connect, ws-ticket auth, heartbeat, and send-message requests.

- [ ] **Step 4: Run the serialization tests to verify they pass**

Run: `cd ../apps/im_web_client && npm test -- --run src/test/wsClient.test.ts`

Expected: serialization assertions pass, while remaining transport tests may still fail.

- [ ] **Step 5: Commit**

```bash
git add ../apps/im_web_client/src/transport/envelope.ts ../apps/im_web_client/src/test/wsClient.test.ts
git commit -m "refactor: add web envelope transport types"
```

## Task 2: Rewrite wsClient Handshake to Envelopes

**Files:**
- Modify: `../apps/im_web_client/src/transport/wsClient.ts`
- Modify: `../apps/im_web_client/src/test/wsClient.test.ts`
- Delete: `../apps/im_web_client/src/transport/wsMessage.ts`

- [ ] **Step 1: Write the failing handshake and heartbeat assertions**

Update `../apps/im_web_client/src/test/wsClient.test.ts` so handshake messages assert:

```ts
expect(JSON.parse(sent[0])).toMatchObject({ command: 1, requestId: 'op-connect-000001' });
expect(JSON.parse(sent[1])).toMatchObject({ command: 10 });
```

and inbound handshake fixtures use:

```ts
{ command: 1, requestId: 'system', body: { status: 'connected' } }
{ command: 10, requestId: 'op-auth-1', body: { connId: 'conn-1', userID: 'u1' } }
```

Heartbeat assertions should now filter by `command === 20`.

- [ ] **Step 2: Run wsClient tests to verify they fail**

Run: `cd ../apps/im_web_client && npm test -- --run src/test/wsClient.test.ts`

Expected: FAIL because `wsClient.ts` still speaks `msgType`.

- [ ] **Step 3: Implement minimal envelope-only wsClient transport**

In `../apps/im_web_client/src/transport/wsClient.ts`:

- import envelope builders from `envelope.ts`
- parse incoming JSON as `ServerEnvelope`
- treat `command === connect` as handshake continuation
- treat `command === auth` as ready/auth result
- send heartbeat with `command === heartbeat`
- resolve send acks and force-logout handling by `requestId` and `command`

Delete `../apps/im_web_client/src/transport/wsMessage.ts` once no imports remain.

- [ ] **Step 4: Run wsClient tests to verify they pass**

Run: `cd ../apps/im_web_client && npm test -- --run src/test/wsClient.test.ts`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ../apps/im_web_client/src/transport/wsClient.ts ../apps/im_web_client/src/test/wsClient.test.ts ../apps/im_web_client/src/transport/envelope.ts ../apps/im_web_client/src/transport/wsMessage.ts
git commit -m "refactor: migrate web ws client to envelopes"
```

## Task 3: Migrate Real Gateway Client to Envelope Commands

**Files:**
- Modify: `../apps/im_web_client/src/services/realGatewayClient.ts`
- Modify: `../apps/im_web_client/src/test/realServices.test.ts`

- [ ] **Step 1: Write the failing real gateway protocol tests**

Update gateway-facing tests to assert:

```ts
expect(JSON.parse(sentPayload)).toMatchObject({
  command: 30,
  requestId: expect.stringMatching(/^op-send-/),
  body: expect.objectContaining({
    clientMsgID: 'local-1',
  }),
});
```

and inbound fixtures use:

```ts
{ command: 32, requestId: 'srv-1', body: { ...dispatchPayload } }
{ command: 35, requestId: 'force-1', body: { reason: 'Force logout' } }
{ command: 90, requestId: 'op-send-00000001', body: { message: 'invalid request' } }
```

- [ ] **Step 2: Run the real gateway tests to verify they fail**

Run: `cd ../apps/im_web_client && npm test -- --run src/test/realServices.test.ts`

Expected: FAIL because `realGatewayClient.ts` still uses `WsEnvelope` and numeric `msgType` routing.

- [ ] **Step 3: Implement minimal envelope-based gateway client**

In `../apps/im_web_client/src/services/realGatewayClient.ts`:

- remove `WS_MESSAGE_TYPES` and `WsEnvelope`
- reuse builders and command constants from `transport/envelope.ts`
- parse inbound gateway JSON as `ServerEnvelope<Record<string, unknown> | string>`
- map:
  - `CHAT_RECV` -> existing `GatewayEvent` mapping logic
  - `FORCE_LOGOUT` -> `forceLogout`
  - `ERROR` -> reject pending sends
  - `AUTH` -> resolve/reject initial handshake

Keep `GatewayEvent` unchanged unless a failing test proves an adjustment is required.

- [ ] **Step 4: Run the real gateway tests to verify they pass**

Run: `cd ../apps/im_web_client && npm test -- --run src/test/realServices.test.ts`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ../apps/im_web_client/src/services/realGatewayClient.ts ../apps/im_web_client/src/test/realServices.test.ts
git commit -m "refactor: align web gateway client with envelopes"
```

## Task 4: Rework Error Classification Around Envelope Errors

**Files:**
- Modify: `../apps/im_web_client/src/transport/sendMessageError.ts`
- Modify: `../apps/im_web_client/src/test/sendError.test.ts`

- [ ] **Step 1: Write the failing error-classification tests**

Add assertions such as:

```ts
expect(classifySendMessageError('bad request', 90)).toMatchObject({ kind: 'invalidRequest' });
expect(classifySendMessageError('forbidden', 90)).toMatchObject({ kind: 'permissionDenied' });
```

If the envelope body exposes a structured code/message field, add one test for that exact shape too.

- [ ] **Step 2: Run send-error tests to verify they fail**

Run: `cd ../apps/im_web_client && npm test -- --run src/test/sendError.test.ts`

Expected: FAIL because current classification still assumes `9002/9003/9004`.

- [ ] **Step 3: Implement minimal envelope-aware classification**

Change `classifySendMessageError` so it keys off the unified error command plus message text/body semantics rather than legacy numeric WebSocket message types.

- [ ] **Step 4: Run send-error tests to verify they pass**

Run: `cd ../apps/im_web_client && npm test -- --run src/test/sendError.test.ts`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ../apps/im_web_client/src/transport/sendMessageError.ts ../apps/im_web_client/src/test/sendError.test.ts
git commit -m "refactor: classify web gateway envelope errors"
```

## Task 5: Run Full Web Client Verification

**Files:**
- Verify only

- [ ] **Step 1: Run focused transport and gateway tests**

Run:

```bash
cd ../apps/im_web_client && npm test -- --run src/test/wsClient.test.ts src/test/realServices.test.ts src/test/sendError.test.ts
```

Expected: PASS.

- [ ] **Step 2: Run the full web client test suite**

Run:

```bash
cd ../apps/im_web_client && npm test -- --run
```

Expected: PASS.

- [ ] **Step 3: Run a production build**

Run:

```bash
cd ../apps/im_web_client && npm run build
```

Expected: build succeeds with no transport import errors after deleting `wsMessage.ts`.

- [ ] **Step 4: Commit final cleanup**

```bash
git add ../apps/im_web_client
git commit -m "test: verify web envelope protocol migration"
```
