# Postbox

`postbox` is the storage boundary for the rebuilt IM architecture.

## Responsibility

- persist one durable message fact per accepted logical message
- persist history truth into `message_block` and `message_id_mapping`
- serve history pull, conversation views, and resource checks from block history plus Redis hot state
- update receipt/read convergence state through Redis-backed conversation state

## Not In Scope

The module no longer owns:

- online routing
- Kafka-based transfer fanout
- online-user statistics
- gateway-facing REST endpoints

Those responsibilities now live in the validated `postoffice`, `postman`, and `push` flow.

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
