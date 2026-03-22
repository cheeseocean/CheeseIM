# Push Architecture

`push` is the delivery-execution boundary in the rebuilt IM architecture.
It consumes `DeliveryEvent`, executes online dispatch via shared RPC contracts, and emits or consumes `OfflinePushEvent` when vendor push is still required.

## Current Flow

1. `postman` publishes `DeliveryEvent`
2. `DeliveryEventListener` queries online routes through `OnlineRouteQueryRpc`
3. `DeliveryEventListener` dispatches online payloads through `OnlineDispatchRpc`
4. when the user is offline or online dispatch fails and policy allows it, `push` publishes `OfflinePushEvent`
5. `OfflinePushEventListener` suppresses stale attempts after reconnect
6. `MessagePushServiceImpl` and `OfflinePushServiceImpl` hand vendor requests to provider-specific adapters

## Core Types

- `DeliveryEventListener`
- `OfflinePushEventListener`
- `MessagePushServiceImpl`
- `PushDecisionService`
- `PushAttempt`
- `OfflinePushServiceImpl`
- provider adapters under `push.provider`

## Explicitly Removed

The following legacy flow has been deleted:

- `PushMessageListener`
- `OfflinePushListener`
- old Kafka push payload envelopes
- in-listener retry loops across multiple push topics

## Verification

```bash
./gradlew :push:test
```
