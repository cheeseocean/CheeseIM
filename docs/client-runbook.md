# Client Runbook

## Workspace

- `packages/im_tcp_sdk`: Dart TCP SDK for Flutter/mobile/desktop
- `apps/im_flutter_client`: Flutter client shell
- `apps/im_web_client`: React + Vite Web client
- `postoffice`: gateway contract source of truth

## Protocol Boundary

- Flutter/Dart SDK uses TCP framed `CheeseMessage`
- Web client uses `WSMessage` JSON over WebSocket
- Current gateway behavior pushes `CONNECT_SUCCESS` from server first, then client auths

## Common Commands

### Dart SDK

```bash
cd packages/im_tcp_sdk
dart test
```

### Flutter Client

```bash
cd apps/im_flutter_client
flutter test test/features
flutter run
```

### Web Client

```bash
cd apps/im_web_client
npm test
npm run dev
```

### Backend Contract Tests

```bash
cd .
./gradlew :postoffice:test --tests com.cheeseocean.im.postoffice.client.TcpClientTest --tests com.cheeseocean.im.postoffice.WebSocketTestClient
```

## Current Coverage

- Dart SDK: framing, protocol mapping, reconnect guard, normalized errors, buffer reset
- Flutter: login flow, auth/network state handling, real SDK-backed text send, inbound stream consumption, chat shell layout, failed-send retry
- Web: WS message builders, WebSocket lifecycle callbacks, session store transitions, conversation aggregation, login/chat UI smoke tests

## Current Limitations

- Neither client includes persistence, history pagination, or file/image messages yet
- Web UI currently keeps presentation intentionally minimal; no styling system or responsive polish yet

## Suggested Next Integration Step

1. Add real gateway-backed end-to-end manual runs for Flutter and Web against local `postoffice`
2. Extend SDK/Web transport to cover message failure responses, force logout, and heartbeat scheduling in UI runtime
3. Add history loading, persistence, and richer message types on top of the current client state model
