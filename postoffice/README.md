# Postoffice

`postoffice` is the access and online-route layer of the rebuilt IM architecture.

## Responsibility

- accept and authenticate long-lived client connections
- maintain per-user, per-device online route snapshots
- normalize inbound chat and ack traffic before forwarding to the delivery core
- deliver online fanout results back to `postman`

## Core Types

- `ConnectionManager`
- `ChatMessageHandler`
- `HeartbeatMessageHandler`
- `RedisOnlineRouteService`
- `GatewayPushServiceImpl`

## Not In Scope

`postoffice` does not own durable message truth, offline storage, or final delivery convergence. Those responsibilities belong to `postbox`, `push`, and `postman`.

## Verification

```bash
./gradlew :postoffice:test
```

The authoritative end-to-end fixture is `postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java`.
