# Postman

`postman` is the orchestration core of the rebuilt IM architecture.

## Responsibility

- consume normalized ingress events from `postbox`
- enforce idempotency and allocate stable conversation `seq`
- orchestrate history fanout to `postbox`
- orchestrate delivery fanout to `push`
- converge ack, read, and recall state
- schedule compensation and dead-letter handling for incomplete delivery

## Core Types

- `IngressEventListener`
- `ConversationSeqService`
- `MessageIdempotencyService`
- `DeliveryCompensationService`
- `ReceiptEventListener`

## Not In Scope

The module does not carry query-side or storage-side ownership:

- history-query APIs
- Mongo persistence models
- history persistence models

History truth lives in `postbox`, online dispatch lives in `postoffice`, and delivery execution lives in `push`.

## Verification

```bash
./gradlew :postman:test
```

Key regressions are concentrated in `postman/src/test/java/com/cheeseocean/im/postman/service`.
