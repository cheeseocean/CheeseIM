# Push

`push` is the offline push execution boundary for the rebuilt IM architecture.

## Responsibility

- decide whether a delivery result still needs vendor push
- deduplicate push attempts by message identity
- cancel stale push attempts after reconnect or receipt convergence
- execute vendor push through provider-specific adapters

## Not In Scope

The module does not own:

- online push routing
- Kafka listener-based retry orchestration
- transport-envelope translation for gateway protocols

Online delivery belongs to `postoffice`. Delivery truth and compensation belong to `postman`.

## Core Types

- `MessagePushServiceImpl`
- `PushDecisionService`
- `PushAttempt`
- `PushStatisticsService`

## Verification

```bash
./gradlew :push:test
./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.ImFlowSmokeTest"
```
