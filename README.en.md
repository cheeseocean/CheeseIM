# CheeseIM

CheeseIM is a self-hosted open-source IM system. The repository contains the Java server, a reusable Go client SDK, the CheeseBox TUI client, and design documents. Server-side responsibilities are split into HTTP API, auth/session, business domain services, long-connection gateway, message ingress, message orchestration, and delivery/push modules. For local development, `bootstrap-all` is the recommended entry point because it runs the full server stack in one JVM.

[中文](README.md)

## Architecture

The diagram below is organized around the product experience: user-facing clients, access channels, IM capabilities, the message loop, and the data foundation.

![CheeseIM product architecture](docs/assets/cheeseim-architecture.svg)

## Modules

| Module | Responsibility |
| --- | --- |
| `server/api-server` | Unified HTTP entry. Controllers handle REST requests, principal resolution, facade orchestration, and response mapping only. |
| `server/authcenter` | Access/refresh tokens, WS/TCP tickets, session lifecycle, device kickoff, and connection auth. |
| `server/business` | User, friend, blacklist, group membership, conversation, and sync-point domain services. |
| `server/postoffice` | TCP/WebSocket gateway, Protobuf codecs, connection management, online routes, heartbeats, kickoff, and online delivery. |
| `server/postbox` | Message sending entry and history query entry. Implements `MessageSender` and publishes ingress events. |
| `server/postmaster` | Message orchestration. Consumes ingress events, allocates conversation/user seq, writes history blocks, and emits delivery events. |
| `server/postman` | Delivery and offline push. Consumes delivery/offline events and dispatches online messages or vendor push requests. |
| `server/common-api` | Cross-module APIs, domain models, enums, events, and Protobuf definitions. |
| `server/common-core` | Shared ports/models and state infrastructure: repository and queue contracts, typed CacheStore, notifications, and seq state. |
| `server/infra-queue` | Queue runtime infrastructure: Kafka/Chronicle adapters, listener wiring, topic contract validation, and Kafka DLT operations. |
| `server/storage-business` | Mongo adapters for users, relationships, groups, conversations, control events, fanout jobs, and DLT audit. |
| `server/config` | Spring/YAML configuration for all-in-one and module deployments. |
| `server/bootstrap-all` | Recommended local development entry. Runs all modules in one JVM with Dubbo injvm. |
| `sdks/go` | Reusable Go IM client SDK. |
| `apps/CheeseBox` | TUI chat application built on top of the Go SDK. |

## Current Status

| Area | Status | Notes |
| --- | --- | --- |
| Java server | Core pipeline implemented | all-in-one local integration is the main path; split-module deployment still needs environment-specific config and verification. |
| Go SDK | Usable for real integration | Wraps HTTP auth, ticket issuing, TCP long connection, message sending, conversation sync, and social queries. |
| CheeseBox TUI | Usable integration client | Supports login, conversation/friend/group navigation, text messages, realtime events, history sync, and gap repair. Friend request handling UI, conversation deletion UI, and rich media remain future work. |
| Documentation | Being consolidated | Root READMEs, module READMEs, protocol docs, and the client runbook are the maintained entry points. Historical plans/specs are process references only. |

## Implemented Features

- HTTP auth: login, refresh, logout, device kickoff, and WS/TCP ticket issuing.
- TCP/WS long connection protocol based on Protobuf envelopes.
- Message pipeline: ingress, option policy, seq allocation, history persistence, online delivery, and offline push events.
- Conversation sync: visible conversations, conversation ID hash, max seq, read snapshots, pull by seq ranges, and read seq ACK.
- Social APIs: user settings, friend requests, friendships, blacklist, and group member query.
- Notification sending through `NotificationSender` and `MessageSender`.
- Go SDK and CheeseBox TUI client for real end-to-end testing.

## Key Constraints

- `api-server` owns HTTP Request/Response models. Lower-level services should return domain models or primitive results.
- `authcenter` owns token, ticket, session, and connection-auth logic. `postoffice` owns connection state and online routes.
- TCP/WS protocol is defined by `common-api/src/main/proto/message_protocol.proto`; JSON command payloads are no longer the source of truth.
- Conversation lists do not store latest-message snapshots. Clients should cache the latest message or pull messages on demand.
- Message seq allocation must use `ConversationSeqAllocator`; Redis and MongoDB jointly maintain its state. RocksDB is only a development fallback for limited local state, not a replacement for Redis in all-in-one mode.
- `bootstrap-all` is the recommended development mode. See [deployment modes](docs/DEPLOYMENT.md) for split-module and cluster contracts.

## Development

Recommended prerequisites:

- JDK 17
- Repository Gradle Wrapper
- MongoDB 6.x+
- Redis 6.x+
- Optional Kafka/Nacos for split-module or queue experiments
- Go 1.24.2 for `sdks/go` and `apps/CheeseBox`

Start the full server locally:

```bash
cd server
./gradlew :bootstrap-all:bootRun
```

Default ports:

| Service | Port |
| --- | --- |
| HTTP API | `18079` |
| WebSocket | `5147`, default path `/ws` |
| TCP | `5148` |
| Dubbo | injvm in all-in-one mode |

Example login:

```bash
curl -sS -X POST http://127.0.0.1:18079/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"u100","platformId":1,"deviceId":"dev-u100","clientVersion":"dev"}'
```

Example ticket request:

```bash
curl -sS -X POST http://127.0.0.1:18079/api/im/ws-ticket \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Set `CHEESEIM_AUTH_JWT_SECRET` to a value of at least 32 characters before starting all-in-one or any split module. See [deployment modes](docs/DEPLOYMENT.md) for the environment variables, profiles, ports, and middleware matrix.

Run CheeseBox:

```bash
cd sdks/go
go generate ./proto
go test ./...
```

```bash
cd apps/CheeseBox
go test ./...
go run ./cmd/cheesebox
```

## Testing

Compile the server:

```bash
cd server
./gradlew compileJava
```

Run module tests:

```bash
./gradlew :api-server:test
./gradlew :business:test
./gradlew :postoffice:test
./gradlew :postmaster:test
./gradlew :postbox:test
./gradlew :postman:test
```

For end-to-end testing, start MongoDB and Redis, run `:bootstrap-all:bootRun`, then launch two CheeseBox clients with different users and verify online delivery plus history sync after restart.

## Documentation

Maintained entry points:

- `README.md` / `README.en.md`: project entry, architecture, module boundaries, startup, and testing.
- `docs/CheeseIM-数据同步设计文档.md`: current conversation/message sync design.
- `docs/client-runbook.md`: Go SDK and CheeseBox integration runbook.
- `server/postoffice/docs/TCP_PROTOCOL.md`: TCP/WS Protobuf protocol.
- `server/postoffice/README.md`, `server/postbox/README.md`, `server/postmaster/README.md`, `server/postman/README.md`: server module responsibilities.
- `apps/CheeseBox/README.md`, `apps/CheeseBox/arch.md`: TUI client documentation.

Reference-only documents:

- `server/postmaster/docs/ConversationArch.md` and `server/postmaster/docs/SeqArch.md`: conversation and seq design background.
- `server/docs/architecture/**`: earlier server architecture drafts.

## Repository Layout

```text
.
├── apps
│   ├── CheeseBox          # TUI client, current primary integration client
├── distro                 # Local middleware and helper scripts
├── docs                   # Design documents and historical plans
├── sdks
│   └── go                 # Go IM client SDK
└── server                 # Java server
    ├── api-server
    ├── authcenter
    ├── business
    ├── bootstrap-all
    ├── common-api
    ├── common-core
    ├── infra-queue
    ├── storage-history
    ├── storage-business
    ├── config
    ├── ops-cli
    ├── postbox
    ├── postman
    ├── postmaster
    └── postoffice
```
