# Push Architecture

The rebuilt push module is no longer a Kafka listener pipeline. It is a delivery-side service that is invoked after `postman` decides a message still needs offline push.

## Current Flow

1. `postman` advances delivery state for a message
2. when online confirmation is missing, `postman` invokes the push boundary
3. `PushDecisionService` decides whether a push attempt should exist
4. `MessagePushServiceImpl` creates or suppresses a `PushAttempt`
5. `OfflinePushServiceImpl` hands the request to provider-specific adapters
6. reconnect, receipt, or read convergence can cancel stale push attempts

## Core Types

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
./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.ImFlowSmokeTest"
```
