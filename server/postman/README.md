# Postman

`postman` is the delivery core of the rebuilt IM architecture.

## Responsibility

- accept normalized send commands from the gateway path
- enforce idempotency and generate stable delivery outcomes
- orchestrate message persistence through `postbox`
- coordinate online fanout through `postoffice`
- converge ack, read, and recall state
- schedule compensation and dead-letter handling for incomplete delivery

## Core Types

- `MessageDeliveryServiceImpl`
- `MessageIdempotencyService`
- `DeliveryStateMachine`
- `DeliveryCompensationService`
- `GroupFanoutPlanner`

## Not In Scope

The module does not carry query-side or storage-side ownership:

- history-query APIs
- Mongo persistence models
- conversation sequence utilities

Storage truth now lives in `postbox`, and delivery truth lives in the service set above.

## Verification

```bash
./gradlew :postman:test
```

Key regressions are concentrated in `postman/src/test/java/com/cheeseocean/im/postman/service`.
