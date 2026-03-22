# Postoffice

`postoffice` is the access and online-route layer of the rebuilt IM architecture.

## Responsibility

- accept and authenticate long-lived client connections
- maintain per-user, per-device online route snapshots
- normalize inbound chat and ack traffic before forwarding to `postbox` / `postman`
- execute connection-level online dispatch for `push` through `OnlineDispatchRpc`

## Core Types

- `ConnectionManager`
- `ChatMessageHandler`
- `HeartbeatMessageHandler`
- `RedisOnlineRouteService`
- `OnlineDispatchRpcImpl`
- `OnlineRouteQueryRpcImpl`

## Not In Scope

`postoffice` does not own durable message truth, offline storage, or delivery orchestration. Those responsibilities belong to `postbox`, `push`, and `postman`.

## Verification

```bash
./gradlew :postoffice:test
```

The authoritative end-to-end fixture is `postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java`.
