# IM Multi-Client Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a first usable CheeseIM Flutter client, Dart TCP SDK, and Web client aligned with `postoffice` TCP/WS protocols, including login, single chat, conversation aggregation, reconnect, and delivery-state UX.

**Architecture:** Add a reusable Dart TCP package under `packages/im_tcp_sdk`, a Flutter app under `apps/im_flutter_client`, and a React + TypeScript Web client under `apps/im_web_client`. Keep `CheeseIM/postoffice` as the direct integration point and only patch it where concrete client compatibility gaps appear.

**Tech Stack:** Dart, Flutter, TCP sockets, WebSocket, React, TypeScript, Vite, Vitest, existing Java `postoffice` tests via Gradle.

---

## File Structure

### New top-level units

- `packages/im_tcp_sdk/`
  - `pubspec.yaml` — SDK package manifest
  - `lib/src/protocol/cheese_message.dart` — TCP frame encode/decode
  - `lib/src/protocol/message_types.dart` — TCP message type constants
  - `lib/src/model/auth_session.dart` — auth/session input model
  - `lib/src/model/chat_message_item.dart` — message view/domain model
  - `lib/src/model/conversation_summary.dart` — conversation aggregate model
  - `lib/src/model/connection_snapshot.dart` — connection state model
  - `lib/src/client/im_tcp_client.dart` — public SDK entry point
  - `lib/src/client/im_tcp_connection.dart` — raw socket lifecycle and framing
  - `lib/src/client/im_tcp_session_manager.dart` — auth/bootstrap/heartbeat/reconnect
  - `lib/src/client/request_tracker.dart` — operation correlation and timeout tracking
  - `lib/src/client/message_store.dart` — in-memory message/conversation aggregation
  - `lib/im_tcp_sdk.dart` — package exports
  - `test/protocol/cheese_message_test.dart`
  - `test/client/im_tcp_connection_test.dart`
  - `test/client/im_tcp_session_manager_test.dart`
  - `test/client/message_store_test.dart`

- `apps/im_flutter_client/`
  - `pubspec.yaml` — Flutter app manifest
  - `lib/main.dart` — app bootstrap
  - `lib/src/app.dart` — MaterialApp/router shell
  - `lib/src/core/bootstrap.dart` — dependency wiring
  - `lib/src/features/auth/login_controller.dart`
  - `lib/src/features/auth/login_screen.dart`
  - `lib/src/features/chat/chat_controller.dart`
  - `lib/src/features/chat/chat_shell.dart`
  - `lib/src/features/chat/conversation_list_pane.dart`
  - `lib/src/features/chat/message_pane.dart`
  - `lib/src/features/chat/connection_banner.dart`
  - `test/features/auth/login_controller_test.dart`
  - `test/features/chat/chat_controller_test.dart`
  - `test/features/chat/chat_shell_test.dart`

- `apps/im_web_client/`
  - `package.json` — Vite/React manifest
  - `tsconfig.json`
  - `vite.config.ts`
  - `src/main.tsx`
  - `src/app/App.tsx`
  - `src/app/providers.tsx`
  - `src/domain/types.ts`
  - `src/transport/wsMessage.ts`
  - `src/transport/wsClient.ts`
  - `src/state/sessionStore.ts`
  - `src/state/conversationStore.ts`
  - `src/features/auth/LoginView.tsx`
  - `src/features/chat/ChatLayout.tsx`
  - `src/features/chat/ConversationList.tsx`
  - `src/features/chat/MessagePanel.tsx`
  - `src/features/chat/ConnectionStatus.tsx`
  - `src/test/wsClient.test.ts`
  - `src/test/conversationStore.test.ts`
  - `src/test/App.test.tsx`

### Existing backend files that may need changes

- `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/CheeseMessage.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WSMessage.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/AuthMessageHandler.java`
- `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- `postoffice/src/test/java/com/cheeseocean/im/postoffice/client/TcpClientTest.java`
- `postoffice/src/test/java/com/cheeseocean/im/postoffice/WebSocketTestClient.java`
- `postoffice/docs/TCP_PROTOCOL.md`

Only touch these backend files when client contract testing reveals a concrete compatibility gap.

## Task 1: Scaffold Client Workspace

**Files:**
- Create: `packages/im_tcp_sdk/pubspec.yaml`
- Create: `packages/im_tcp_sdk/lib/im_tcp_sdk.dart`
- Create: `apps/im_flutter_client/pubspec.yaml`
- Create: `apps/im_flutter_client/lib/main.dart`
- Create: `apps/im_web_client/package.json`
- Create: `apps/im_web_client/tsconfig.json`
- Create: `apps/im_web_client/vite.config.ts`
- Create: `apps/im_web_client/src/main.tsx`
- Modify: `settings.gradle`
- Modify: `.gitignore`
- Test: repository-level smoke commands only

- [ ] **Step 1: Create the package/app directory skeleton**

```text
CheeseIM/
  packages/im_tcp_sdk/
  apps/im_flutter_client/
  apps/im_web_client/
```

- [ ] **Step 2: Add SDK package manifest**

```yaml
name: im_tcp_sdk
environment:
  sdk: ">=3.4.0 <4.0.0"
dependencies:
  collection: ^1.18.0
  meta: ^1.12.0
dev_dependencies:
  lints: ^4.0.0
  test: ^1.25.0
```

- [ ] **Step 3: Add Flutter app manifest with local path dependency**

```yaml
dependencies:
  flutter:
    sdk: flutter
  im_tcp_sdk:
    path: ../../packages/im_tcp_sdk
dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^4.0.0
```

- [ ] **Step 4: Add Web app manifest**

```json
{
  "name": "im_web_client",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "test": "vitest run"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.4.0",
    "@testing-library/react": "^15.0.0",
    "@testing-library/user-event": "^14.5.0",
    "@types/react": "^18.3.0",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.0",
    "jsdom": "^24.0.0",
    "typescript": "^5.5.0",
    "vite": "^5.3.0",
    "vitest": "^2.0.0"
  }
}
```

- [ ] **Step 5: Create the initial entry points referenced by later tasks**

```dart
// packages/im_tcp_sdk/lib/im_tcp_sdk.dart
library im_tcp_sdk;
```

```dart
// apps/im_flutter_client/lib/main.dart
void main() {}
```

```tsx
// apps/im_web_client/src/main.tsx
console.log('im_web_client bootstrap');
```

- [ ] **Step 6: Update repository ignores for Flutter, Dart, and Node outputs**

```gitignore
apps/im_flutter_client/.dart_tool/
apps/im_flutter_client/build/
apps/im_web_client/node_modules/
apps/im_web_client/dist/
packages/im_tcp_sdk/.dart_tool/
```

- [ ] **Step 7: Verify the new tree is present**

Run: `find packages apps -maxdepth 2 -type f | sort`
Expected: manifests and entry files exist under all three units

- [ ] **Step 8: Resolve package dependencies from a clean checkout**

Run: `dart pub get`
Workdir: `packages/im_tcp_sdk`
Expected: Dart packages resolve successfully

Run: `flutter pub get`
Workdir: `apps/im_flutter_client`
Expected: Flutter app dependencies resolve successfully

Run: `npm install`
Workdir: `apps/im_web_client`
Expected: Web app dependencies and test tooling install successfully

## Task 2: Lock Backend Contract Fixtures Before Client Code

**Files:**
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/client/TcpClientTest.java`
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/WebSocketTestClient.java`
- Create: `postoffice/src/test/java/com/cheeseocean/im/postoffice/client/ProtocolContractFixtures.java`
- Modify: `postoffice/docs/TCP_PROTOCOL.md`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/client/TcpClientTest.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/WebSocketTestClient.java`

- [ ] **Step 1: Write a failing TCP contract fixture test for connect/auth/send frame expectations**
- [ ] **Step 1: Write failing TCP contract fixture tests for connect/auth/send/request-response expectations**

```java
@Test
void shouldEncodeStableTcpContractFixture() {
    CheeseMessage message = new CheeseMessage(
            CheeseMessageType.TCP_AUTH_REQ,
            "op-auth-00000001",
            "{\"token\":\"t\",\"userID\":\"u1\",\"platformID\":2}"
    );
    assertThat(message.encode()).isNotEmpty();
}

@Test
void shouldDecodeStableTcpSendResponseFixture() {
    CheeseMessage message = new CheeseMessage(
            CheeseMessageType.TCP_SEND_MSG_RESP,
            "op-send-00000001",
            "{\"serverMsgID\":\"s1\",\"clientMsgID\":\"c1\",\"sendTime\":1710000000000}"
    );
    assertThat(CheeseMessage.decode(message.encode()).getData()).contains("serverMsgID");
}
```

- [ ] **Step 2: Write failing WebSocket fixture tests for connect/auth/send/notify payload shape**

```java
@Test
void shouldSerializeStableWsAuthPayload() {
    WSMessage message = new WSMessage(WSMessageType.WS_AUTH_REQ, "op-1",
            Map.of("token", "t", "userID", "u1", "platformID", 5));
    assertThat(objectMapper.writeValueAsString(message)).contains("\"msgType\":1101");
}

@Test
void shouldSerializeStableWsInboundNotifyPayload() {
    WSMessage message = WSMessage.recvMsgNotify("op-notify-1",
            Map.of("serverMsgID", "s1", "clientMsgID", "c1", "sendID", "u2", "recvID", "u1", "content", "hi"));
    assertThat(objectMapper.writeValueAsString(message)).contains("\"msgType\":2003");
}
```

- [ ] **Step 3: Add `ProtocolContractFixtures` helper with canonical request, response, and notify payloads**

```java
public final class ProtocolContractFixtures {
    public static Map<String, Object> connectPayload() { ... }
    public static Map<String, Object> authPayload() { ... }
    public static Map<String, Object> authSuccessPayload() { ... }
    public static Map<String, Object> authFailedPayload() { ... }
    public static Message singleChatPayload() { ... }
    public static Map<String, Object> sendAckPayload() { ... }
    public static Map<String, Object> recvNotifyPayload() { ... }
}
```

- [ ] **Step 4: Update protocol documentation to match the tested canonical request/response/notify payloads**

```markdown
{
  "token": "jwt-token",
  "userID": "user123",
  "platformID": 2
}
```

```markdown
{
  "serverMsgID": "msg-456",
  "clientMsgID": "client-123",
  "sendTime": 1710000000000
}
```

- [ ] **Step 5: Run focused backend contract tests**

Run: `./gradlew :postoffice:test --tests com.cheeseocean.im.postoffice.client.TcpClientTest --tests com.cheeseocean.im.postoffice.WebSocketTestClient`
Expected: PASS, giving stable payload references for the SDK and Web client

## Task 3: Implement Dart TCP Protocol and Framing

**Files:**
- Create: `packages/im_tcp_sdk/lib/src/protocol/cheese_message.dart`
- Create: `packages/im_tcp_sdk/lib/src/protocol/message_types.dart`
- Create: `packages/im_tcp_sdk/lib/src/client/im_tcp_connection.dart`
- Create: `packages/im_tcp_sdk/test/protocol/cheese_message_test.dart`
- Create: `packages/im_tcp_sdk/test/client/im_tcp_connection_test.dart`

- [ ] **Step 1: Write the failing codec tests**

```dart
test('encodes and decodes a CheeseMessage frame', () {
  final frame = CheeseMessage(
    msgType: TcpMessageTypes.authReq,
    operationId: 'op-auth-00000001',
    data: '{"token":"t"}',
  );

  final decoded = CheeseMessage.decode(frame.encode());
  expect(decoded.msgType, TcpMessageTypes.authReq);
  expect(decoded.data, '{"token":"t"}');
});
```

- [ ] **Step 2: Write the failing sticky-packet / half-packet buffering test**

```dart
test('emits complete frames when bytes arrive in chunks', () async {
  final connection = ImTcpConnection.test();
  connection.addIncomingBytes(chunkA);
  connection.addIncomingBytes(chunkB);
  expect(await connection.frames.first, hasLength(2));
});
```

- [ ] **Step 3: Implement message type constants and `CheeseMessage` framing**

```dart
class TcpMessageTypes {
  static const int connectReq = 1;
  static const int authReq = 10;
  static const int heartbeatReq = 20;
  static const int sendMsgReq = 30;
}
```

- [ ] **Step 4: Implement buffered frame parsing in `ImTcpConnection`**

```dart
while (_buffer.length >= CheeseMessage.headerLength) {
  final frameLength = CheeseMessage.peekFrameLength(_buffer);
  if (_buffer.length < frameLength) return;
  _frames.add(CheeseMessage.decode(_take(frameLength)));
}
```

- [ ] **Step 5: Run Dart SDK protocol tests**

Run: `dart test packages/im_tcp_sdk/test/protocol packages/im_tcp_sdk/test/client`
Expected: PASS for encode/decode and reassembly behavior

## Task 4: Implement Dart Session Manager, Request Tracking, and In-Memory Conversation Store

**Files:**
- Create: `packages/im_tcp_sdk/lib/src/model/auth_session.dart`
- Create: `packages/im_tcp_sdk/lib/src/model/chat_message_item.dart`
- Create: `packages/im_tcp_sdk/lib/src/model/conversation_summary.dart`
- Create: `packages/im_tcp_sdk/lib/src/model/connection_snapshot.dart`
- Create: `packages/im_tcp_sdk/lib/src/client/request_tracker.dart`
- Create: `packages/im_tcp_sdk/lib/src/client/message_store.dart`
- Create: `packages/im_tcp_sdk/lib/src/client/im_tcp_session_manager.dart`
- Create: `packages/im_tcp_sdk/lib/src/client/im_tcp_client.dart`
- Modify: `packages/im_tcp_sdk/lib/im_tcp_sdk.dart`
- Create: `packages/im_tcp_sdk/test/client/im_tcp_session_manager_test.dart`
- Create: `packages/im_tcp_sdk/test/client/message_store_test.dart`

- [ ] **Step 1: Write the failing session lifecycle test**

```dart
test('moves idle -> connecting -> authenticating -> ready', () async {
  final manager = buildManagerWithFakeConnection();
  await manager.connect(authSession);
  expect(manager.snapshot.state, ConnectionState.ready);
});
```

- [ ] **Step 2: Write the failing reconnect backoff test**

```dart
test('retries with capped backoff', () async {
  final manager = buildManagerWithDisconnectingSocket();
  await manager.connect(authSession);
  expect(manager.debugRetrySchedule, [1, 2, 5, 10, 20]);
});
```

- [ ] **Step 3: Write the failing heartbeat-timeout test**

```dart
test('enters reconnecting after heartbeat timeout', () async {
  final manager = buildManagerWithSilentHeartbeat();
  await manager.connect(authSession);
  manager.elapseHeartbeatGracePeriod();
  expect(manager.snapshot.state, ConnectionState.reconnecting);
});
```

- [ ] **Step 4: Write the failing message aggregation and dedupe test**

```dart
test('aggregates conversation preview and avoids duplicate inbound messages', () {
  final store = MessageStore();
  store.applyInbound(serverMsgId: 's1', clientMsgId: 'c1', peerId: 'u2', content: 'hi');
  store.applyInbound(serverMsgId: 's1', clientMsgId: 'c1', peerId: 'u2', content: 'hi');
  expect(store.conversations.single.unreadCount, 1);
});
```

- [ ] **Step 5: Implement the session manager around connect/auth/heartbeat/reconnect**

```dart
Future<void> connect(AuthSession session) async {
  _emit(ConnectionLifecycle.connecting);
  await _connection.open(session.host, session.port);
  _emit(ConnectionLifecycle.connected);
  await _sendConnect();
  await _sendAuth(session);
}
```

- [ ] **Step 6: Implement request tracking and timeout cleanup**

```dart
final completer = Completer<ResponseEnvelope>();
_pending[operationId] = completer;
timeoutTimer = Timer(requestTimeout, () => completer.completeError(RequestTimeout()));
```

- [ ] **Step 7: Implement in-memory message and conversation state**

```dart
ConversationSummary _upsertConversation(ChatMessageItem item) { ... }
ChatMessageItem markSent(String clientMsgId, String serverMsgId) { ... }
```

- [ ] **Step 8: Normalize transport errors into client-facing categories**

```dart
sealed class ClientError {
  const ClientError();
}

final class AuthRejected extends ClientError {}
final class PermissionDenied extends ClientError {}
final class NetworkInterrupted extends ClientError {}
```

- [ ] **Step 9: Export a single public SDK surface**

```dart
library im_tcp_sdk;

export 'src/client/im_tcp_client.dart';
export 'src/model/auth_session.dart';
export 'src/model/chat_message_item.dart';
```

- [ ] **Step 10: Run all Dart SDK tests**

Run: `dart test packages/im_tcp_sdk/test`
Expected: PASS for lifecycle, retry, timeout, and store behavior

## Task 5: Build Flutter Login Flow and Connection Shell

**Files:**
- Create: `apps/im_flutter_client/lib/src/app.dart`
- Create: `apps/im_flutter_client/lib/src/core/bootstrap.dart`
- Create: `apps/im_flutter_client/lib/src/features/auth/business_login_adapter.dart`
- Create: `apps/im_flutter_client/lib/src/features/auth/login_controller.dart`
- Create: `apps/im_flutter_client/lib/src/features/auth/login_screen.dart`
- Create: `apps/im_flutter_client/lib/src/features/chat/connection_banner.dart`
- Create: `apps/im_flutter_client/test/features/auth/login_controller_test.dart`
- Create: `apps/im_flutter_client/test/features/auth/auth_error_flow_test.dart`

- [ ] **Step 1: Write the failing login controller test**

```dart
test('submits manual session parameters into the SDK', () async {
  final controller = LoginController(fakeClient);
  await controller.login(host: '127.0.0.1', port: 5148, userId: 'u1', token: 'jwt', platformId: 2);
  expect(fakeClient.lastSession.userId, 'u1');
});
```

- [ ] **Step 2: Write the failing connection banner rendering test**

```dart
testWidgets('shows reconnecting banner', (tester) async {
  await tester.pumpWidget(buildApp(snapshot: reconnectingSnapshot));
  expect(find.text('Reconnecting...'), findsOneWidget);
});
```

- [ ] **Step 3: Write the failing auth rejection / force logout test**

```dart
testWidgets('returns to login when auth is rejected', (tester) async {
  await tester.pumpWidget(buildAppWithAuthRejectedClient());
  expect(find.byType(LoginScreen), findsOneWidget);
  expect(find.text('Authentication failed'), findsOneWidget);
});
```

- [ ] **Step 4: Implement app bootstrap and dependency wiring**

```dart
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final dependencies = await bootstrap();
  runApp(ImFlutterApp(dependencies: dependencies));
}
```

- [ ] **Step 5: Implement manual login form with future adapter seam**

```dart
abstract class BusinessLoginAdapter {
  Future<AuthSession> login();
}

class LoginController {
  Future<void> login({...}) => client.connect(AuthSession(...));
  Future<void> loginWithBusinessProvider(BusinessLoginAdapter adapter) => adapter.login();
}
```

- [ ] **Step 6: Surface normalized auth/network errors to the login and shell UI**

```dart
if (snapshot.error case AuthRejected()) {
  state = state.copyWith(showLogin: true, errorMessage: 'Authentication failed');
}
```

- [ ] **Step 7: Run focused Flutter auth tests**

Run: `flutter test test/features/auth/login_controller_test.dart`
Expected: PASS, proving the login screen can drive SDK connection setup

## Task 6: Build Flutter Conversation and Chat UX

**Files:**
- Create: `apps/im_flutter_client/lib/src/features/chat/chat_controller.dart`
- Create: `apps/im_flutter_client/lib/src/features/chat/chat_shell.dart`
- Create: `apps/im_flutter_client/lib/src/features/chat/conversation_list_pane.dart`
- Create: `apps/im_flutter_client/lib/src/features/chat/message_pane.dart`
- Create: `apps/im_flutter_client/test/features/chat/chat_controller_test.dart`
- Create: `apps/im_flutter_client/test/features/chat/chat_shell_test.dart`

- [ ] **Step 1: Write the failing send/retry controller test**

```dart
test('marks a failed message and retries it', () async {
  final controller = ChatController(fakeClient);
  await controller.sendText('u2', 'hello');
  fakeClient.failPendingSend();
  expect(controller.messages.single.status, DeliveryStatus.failed);
  await controller.retry(controller.messages.single);
  expect(fakeClient.sendCallCount, 2);
});
```

- [ ] **Step 2: Write the failing responsive shell test**

```dart
testWidgets('uses split pane on wide screens', (tester) async {
  tester.view.physicalSize = const Size(1440, 900);
  await tester.pumpWidget(buildChatShell());
  expect(find.byType(ConversationListPane), findsOneWidget);
  expect(find.byType(MessagePane), findsOneWidget);
});
```

- [ ] **Step 3: Write the failing narrow-layout navigation test**

```dart
testWidgets('uses stacked navigation on narrow screens', (tester) async {
  tester.view.physicalSize = const Size(390, 844);
  await tester.pumpWidget(buildChatShell());
  expect(find.byType(ConversationListPane), findsOneWidget);
  await tester.tap(find.text('user-u2'));
  await tester.pumpAndSettle();
  expect(find.byType(MessagePane), findsOneWidget);
});
```

- [ ] **Step 4: Implement chat controller around SDK streams**

```dart
client.messages.listen(_storeInbound);
client.connectionSnapshots.listen(_updateConnection);
Future<void> sendText(String peerId, String text) => client.sendSingleChat(...);
```

- [ ] **Step 5: Implement conversation list and message panel widgets**

```dart
ListView.builder(
  itemCount: conversations.length,
  itemBuilder: (_, index) => ConversationTile(summary: conversations[index]),
)
```

- [ ] **Step 6: Implement narrow-screen stacked navigation and wide-screen split layout**

```dart
return layoutWidth < 720
    ? MobileConversationNavigator(...)
    : DesktopChatSplitView(...);
```

- [ ] **Step 7: Implement failed-send retry and unread reset behavior**

```dart
void openConversation(String conversationId) {
  state = state.markConversationActive(conversationId).clearUnread(conversationId);
}
```

- [ ] **Step 8: Run Flutter chat tests**

Run: `flutter test test/features/chat`
Expected: PASS for responsive layout, send state, retry, and unread handling

## Task 7: Build WebSocket Transport and State Stores

**Files:**
- Create: `apps/im_web_client/src/domain/types.ts`
- Create: `apps/im_web_client/src/transport/wsMessage.ts`
- Create: `apps/im_web_client/src/transport/wsClient.ts`
- Create: `apps/im_web_client/src/state/sessionStore.ts`
- Create: `apps/im_web_client/src/state/conversationStore.ts`
- Create: `apps/im_web_client/src/auth/businessLoginAdapter.ts`
- Create: `apps/im_web_client/src/test/wsClient.test.ts`
- Create: `apps/im_web_client/src/test/conversationStore.test.ts`

- [ ] **Step 1: Write the failing WebSocket auth/send mapping test**
- [ ] **Step 1: Write the failing WebSocket connect/auth/send mapping test**

```ts
it('serializes connect requests in postoffice WSMessage shape', () => {
  const payload = buildConnectRequest();
  expect(payload.msgType).toBe(1001);
});

it('serializes auth requests in postoffice WSMessage shape', () => {
  const payload = buildAuthRequest({ token: 'jwt', userID: 'u1', platformID: 5 });
  expect(payload.msgType).toBe(1101);
  expect(payload.data.userID).toBe('u1');
});
```

- [ ] **Step 2: Write the failing reconnect store test**

```ts
it('transitions to reconnecting after socket close', () => {
  const store = createSessionStore();
  store.handleSocketClosed();
  expect(store.getState().lifecycle).toBe('reconnecting');
});
```

- [ ] **Step 3: Write the failing heartbeat-timeout / auth-failure store test**

```ts
it('stops reconnect and exposes login-required state after auth rejection', () => {
  const store = createSessionStore();
  store.handleAuthRejected('bad token');
  expect(store.getState().lifecycle).toBe('closed');
  expect(store.getState().loginRequired).toBe(true);
});
```

- [ ] **Step 4: Write the failing conversation aggregation test**

```ts
it('increments unread for inactive conversations and dedupes by serverMsgID', () => {
  const store = createConversationStore();
  store.applyInbound(messageA);
  store.applyInbound(messageA);
  expect(store.getState().conversations[0].unreadCount).toBe(1);
});
```

- [ ] **Step 5: Implement WS message builders and parsers**
- [ ] **Step 5: Implement WS message builders and parsers, explicitly mirroring the TCP bootstrap with `WS_CONNECT_REQ` then `WS_AUTH_REQ`**

```ts
export interface WSMessage<T = unknown> {
  msgType: number;
  operationID: string;
  data: T;
}
```

- [ ] **Step 6: Implement WebSocket lifecycle, heartbeat, reconnect, and normalized errors**

```ts
socket.onopen = () => transition('connected');
sendConnectRequest();
sendAuthRequest(session);
socket.onclose = () => scheduleReconnect();
heartbeatTimer = window.setInterval(sendHeartbeat, 30000);
```

- [ ] **Step 7: Implement session and conversation stores**

```ts
applySendAck(clientMsgID: string, serverMsgID: string) { ... }
applyInbound(message: ChatMessageItem) { ... }
```

- [ ] **Step 8: Add the Web business-login adapter seam without implementing real business auth**

```ts
export interface BusinessLoginAdapter {
  login(): Promise<{ userID: string; platformID: number; token: string }>;
}
```

- [ ] **Step 9: Run Web transport/store tests**

Run: `npm test`
Workdir: `apps/im_web_client`
Expected: PASS for mapping, reconnect transitions, and dedupe behavior

## Task 8: Build Web Login and Chat UI

**Files:**
- Create: `apps/im_web_client/src/app/providers.tsx`
- Create: `apps/im_web_client/src/app/App.tsx`
- Create: `apps/im_web_client/src/features/auth/LoginView.tsx`
- Create: `apps/im_web_client/src/features/chat/ChatLayout.tsx`
- Create: `apps/im_web_client/src/features/chat/ConversationList.tsx`
- Create: `apps/im_web_client/src/features/chat/MessagePanel.tsx`
- Create: `apps/im_web_client/src/features/chat/ConnectionStatus.tsx`
- Create: `apps/im_web_client/src/test/App.test.tsx`

- [ ] **Step 1: Write the failing App integration test**

```tsx
it('switches from login to chat after a ready session', async () => {
  render(<App />);
  await user.type(screen.getByLabelText(/user id/i), 'u1');
  expect(await screen.findByText(/conversations/i)).toBeInTheDocument();
});
```

- [ ] **Step 2: Implement login view with manual endpoint/token inputs**

```tsx
<input name="wsUrl" defaultValue="ws://localhost:5147/ws" />
<input name="userID" />
<input name="platformID" type="number" />
<input name="token" />
```

- [ ] **Step 3: Write the failing optimistic-send-state test**

```tsx
it('shows a sending state before send acknowledgement returns', async () => {
  render(<App />);
  await user.click(screen.getByRole('button', { name: /send/i }));
  expect(screen.getByText(/sending/i)).toBeInTheDocument();
});
```

- [ ] **Step 4: Implement desktop-first chat layout with narrow-screen fallback**

```tsx
return isNarrow
  ? <MobileChatLayout />
  : <DesktopChatLayout />;
```

- [ ] **Step 5: Implement connection status, send, retry, unread, and login-required UI**

```tsx
{message.status === 'failed' && <button onClick={() => retry(message)}>Retry</button>}
```

- [ ] **Step 6: Wire the optional business-login adapter trigger into the login view**

```tsx
{businessLoginAdapter && (
  <button onClick={() => loginWithBusinessProvider()}>Business Login</button>
)}
```

- [ ] **Step 7: Run Web app tests**

Run: `npm test`
Workdir: `apps/im_web_client`
Expected: PASS for login-to-chat transition and retry affordances

## Task 9: Integrate Clients Against Postoffice and Patch Compatibility Gaps

**Files:**
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/CheeseMessage.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WSMessage.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/AuthMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- Modify: `postoffice/docs/TCP_PROTOCOL.md`
- Create: `docs/superpowers/runbooks/2026-03-17-im-multi-client-local-runbook.md`

- [ ] **Step 1: Start with a failing manual integration note capturing any client/backend mismatch**

```text
Observed mismatch examples:
- operationID length truncation breaks client correlation
- WS auth payload field typing differs from docs
```

- [ ] **Step 2: Apply the smallest backend patch that preserves semantics**

```java
String operationID = normalizeOperationId(message.getOperationID());
Map<String, Object> authData = parseAuthData(message.getData());
```

- [ ] **Step 3: Update protocol docs only if behavior changed or was clarified**

```markdown
OperationID is UTF-8 bytes truncated/padded to 16 bytes on TCP transport.
```

- [ ] **Step 4: Write local runbook commands for all three runtimes**

```bash
./gradlew :postoffice:bootRun
flutter run -d macos
npm run dev
```

- [ ] **Step 5: Run focused backend verification after any compatibility patch**

Run: `./gradlew :postoffice:test`
Expected: PASS with no regression in protocol handlers

## Task 10: End-to-End Verification

**Files:**
- Modify: `docs/superpowers/runbooks/2026-03-17-im-multi-client-local-runbook.md`
- Test: all client and backend suites

- [ ] **Step 1: Run Dart SDK tests**

Run: `dart test`
Workdir: `packages/im_tcp_sdk`
Expected: PASS

- [ ] **Step 2: Run Flutter tests**

Run: `flutter test`
Workdir: `apps/im_flutter_client`
Expected: PASS

- [ ] **Step 3: Run Web tests**

Run: `npm test`
Workdir: `apps/im_web_client`
Expected: PASS

- [ ] **Step 4: Run backend tests**

Run: `./gradlew :postoffice:test`
Expected: PASS

- [ ] **Step 5: Execute manual local verification**

```text
1. Connect Flutter client with manual host/port/token.
2. Connect Web client with ws://localhost:5147/ws.
3. Send single chat from one client to the other.
4. Disconnect one side and verify reconnect.
5. Confirm no duplicate message appears after reconnect.
```

- [ ] **Step 6: Record final verification notes in the runbook**

```markdown
- Verified Flutter TCP login
- Verified Web WS login
- Verified cross-client single chat
- Verified reconnect and dedupe
```
