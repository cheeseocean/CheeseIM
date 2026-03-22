# Postbox

`postbox` is the storage boundary for the rebuilt IM architecture.

## Responsibility

- persist one durable message fact per accepted logical message
- persist per-user inbox projections for offline recovery and unread state
- serve offline pull and projection updates for ack/read/recall convergence

## Not In Scope

The module no longer owns:

- online routing
- Kafka-based transfer fanout
- online-user statistics
- gateway-facing REST endpoints

Those responsibilities now live in the validated `postoffice` and `postman` flow.

## Core Types

- `message_block`
- `message_id_mapping`
- `BlockMessageQueryService`

## Verification

```bash
./gradlew :postbox:test
```

Primary regression coverage lives in `postbox/src/test/java/com/cheeseocean/im/postbox/history/BlockHistoryPersistenceServiceTest.java`
and `postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java`.
