# Java TCP Client Demo Design

## Goal

Build a Java client-side demo against the current server implementation so that two CLI terminals can log in with existing test accounts, establish TCP connections, and exchange IM messages.

The primary goal is communication verification, not account-system completeness.

## Scope

In scope:

- HTTP login by username and password to obtain `accessToken`
- TCP connection to `postoffice`
- TCP auth using payload:
  `{"token":"...","userID":"...","platformID":2}`
- single-chat text send
- inbound message receive and terminal display
- a simple interactive CLI demo

Out of scope:

- user registration
- account bootstrap
- Android UI
- media/file message support
- group chat demo
- reconnect/resume sophistication
- local message persistence

## Architecture

The client demo will be implemented as a separate app under `apps/im_java_client_demo` with two layers:

- `client-core`
- `cli-demo`

### `client-core`

Reusable Java client logic intended to be portable into a future Android client:

- HTTP login
- TCP socket lifecycle
- protocol encode/decode
- auth
- send message
- request/response tracking
- inbound event callbacks

### `cli-demo`

A thin terminal shell around `client-core`:

- parses commands
- displays status
- prints inbound messages
- invokes `client-core`

## CLI Demo UX

The demo runs as one terminal process per account. Two terminals are used for the final demo.

Expected startup:

```bash
./gradlew :apps:im_java_client_demo:cli-demo:run --args="--host 127.0.0.1 --tcp-port 5148 --base-url http://127.0.0.1:8080"
```

Supported commands:

- `login <userId> <password> <platformId>`
- `connect`
- `send <peerUserId> <text>`
- `heartbeat`
- `status`
- `quit`

Behavior:

- `login` calls the auth HTTP API and stores `accessToken`
- `connect` opens TCP, waits for initial connect push, then sends `TCP_AUTH_REQ`
- `send` emits a single-chat text message with:
  - `contentType=101`
  - `chatType=1`
  - generated `clientMsgID`
- inbound `TCP_RECV_MSG_NOTIFY` is printed asynchronously
- `status` prints current user, token presence, socket state, auth state, and latest send ack

## Protocol Assumptions

The client uses the current server TCP contract documented in:

- `/Users/xxxcrel/Develop/backend/java/CheeseIM/server/postoffice/docs/TCP_PROTOCOL.md`

The current integration path is:

1. HTTP login through `authcenter`
2. TCP connect to `postoffice`
3. receive `TCP_CONNECT_SUCCESS`
4. send `TCP_AUTH_REQ`
5. receive `TCP_AUTH_SUCCESS`
6. send `TCP_SEND_MSG_REQ`
7. receive `TCP_SEND_MSG_RESP`
8. peer receives `TCP_RECV_MSG_NOTIFY`

## Module Layout

Proposed structure:

- `apps/im_java_client_demo/build.gradle`
- `apps/im_java_client_demo/client-core/build.gradle`
- `apps/im_java_client_demo/cli-demo/build.gradle`

Primary packages:

- `com.cheeseocean.im.client.auth`
- `com.cheeseocean.im.client.protocol`
- `com.cheeseocean.im.client.tcp`
- `com.cheeseocean.im.client.session`
- `com.cheeseocean.im.client.cli`

## `client-core` Design

### `AuthHttpClient`

Responsibilities:

- call `/api/auth/login`
- parse login response
- return access token and basic session metadata

### `TcpImClient`

Responsibilities:

- open/close socket
- read packets in a background loop
- send auth packet
- send chat packet
- dispatch incoming packets to listeners

### `CheesePacketCodec`

Responsibilities:

- encode TCP frames
- decode TCP frames from `InputStream`
- enforce fixed header semantics and length handling

### `TcpPacket`

Simple transport model:

- `msgType`
- `operationId`
- `timestamp`
- `data`

### `ClientSession`

Runtime state holder:

- `userId`
- `platformId`
- `accessToken`
- `connected`
- `authenticated`
- latest send ack summary

### `RequestTracker`

Responsibilities:

- track outbound `operationId`
- correlate send request / auth request with responses
- expose lightweight timeout/error hooks

### `IncomingMessageListener`

Callback contract for upper layers:

- `onConnected`
- `onAuthSuccess`
- `onAuthFailed`
- `onSendAck`
- `onMessage`
- `onError`
- `onDisconnected`

## `cli-demo` Design

### `CliApplication`

Bootstraps config and starts the command loop.

### `CliCommandLoop`

Reads user input and maps commands to client actions.

### `ConsolePrinter`

Prints structured terminal output without mixing transport logic into the command loop.

### `DemoState`

Holds the current CLI session state and command context.

## Key Constraints

- Do not depend on server production or test classes directly
- Re-declare protocol constants and payload DTOs locally inside the client app
- Keep the transport logic plain Java so it stays Android-portable later
- Keep the first version synchronous on write and background-threaded on read
- Generate `operationId` within the protocol’s 16-byte constraint
- Print every inbound packet in a debuggable form

## Demo Scenario

Terminal A:

1. `login userA passwordA 2`
2. `connect`
3. `send userB hello`

Terminal B:

1. `login userB passwordB 2`
2. `connect`
3. observe inbound message from A
4. `send userA hi`

Expected outcome:

- both terminals authenticate successfully
- sender sees send ack
- receiver sees inbound notify
- both directions work in the same session

## Success Criteria

- username/password login returns a usable token
- TCP auth succeeds against the current server
- single-chat text send returns `TCP_SEND_MSG_RESP`
- peer terminal receives `TCP_RECV_MSG_NOTIFY`

## Verification Plan

Automated:

- `CheesePacketCodecTest`
- `AuthHttpClientTest`
- `TcpImClientTest`
- CLI command parsing tests

Manual:

- run server stack locally
- open two terminals
- log in with two existing test accounts
- connect both clients
- exchange messages in both directions

## Risks

- current auth HTTP response shape may differ from assumptions and must be verified before implementation
- CLI async printing may interfere with prompt readability
- future Android reuse may require replacing plain JDK HTTP/TCP APIs with Android-friendly wrappers, but the logic boundary should remain reusable

## Implementation Direction

Recommended implementation order:

1. create app/module structure
2. implement protocol codec and packet model
3. implement auth HTTP login client
4. implement TCP client and inbound dispatch
5. implement CLI shell and commands
6. add tests
7. verify with a two-terminal manual demo
