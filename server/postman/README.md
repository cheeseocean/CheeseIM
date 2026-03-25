# Push

`push` is the delivery-execution and offline-push boundary for the rebuilt IM architecture.

## Responsibility

- consume `DeliveryEvent` and execute online dispatch through shared RPC seams
- decide whether a delivery result still needs vendor push
- deduplicate push attempts by message identity
- cancel stale push attempts after reconnect or receipt convergence
- execute vendor push through provider-specific adapters

## Not In Scope

The module does not own:

- online push routing
- transport-envelope translation for gateway protocols

Online delivery belongs to `postoffice`. Delivery truth and compensation belong to `postman`.

## Core Types

- `DeliveryEventListener`
- `OfflinePushEventListener`
- `MessagePushServiceImpl`
- `PushDecisionService`
- `PushAttempt`
- `PushStatisticsService`

## Verification

```bash
./gradlew :push:test
```
