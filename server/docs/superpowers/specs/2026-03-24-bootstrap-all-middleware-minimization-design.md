# Bootstrap-All Middleware Minimization Design

## Goal

Minimize external middleware dependencies in `bootstrap-all` mode by introducing a unified queue abstraction with an embedded default implementation, a two-level cache that auto-degrades to embedded storage when Redis is absent, and embedded state stores for session, idempotency, and sequence allocation.

## Scope

This design covers:

- A unified queue abstraction with annotation-driven listener binding
- `ChronicleQueueAdapter` as the default embedded queue implementation
- `KafkaQueueAdapter` as the distributed queue implementation
- Queue auto-configuration based on `app.queue.type`
- A two-level cache with fixed L1 `Caffeine` and auto-selected L2 `Redis` or `RocksDB`
- Embedded replacements for Redis-backed session, idempotency, and sequence state in `bootstrap-all`
- Migration of existing `KafkaTemplate`, `@KafkaListener`, and cache-style Redis usage onto the new abstractions

This design does not cover:

- Replacing MongoDB persistence
- Replacing Redis usages whose semantics are not cache/state-store compatible until explicit domain abstractions are introduced
- Cross-node embedded queue sharing in distributed deployment

## Current Problems

### Business modules depend directly on middleware clients

Current business code directly uses:

- `KafkaTemplate`
- `@KafkaListener`
- `RedisTemplate`
- `StringRedisTemplate`

This makes `bootstrap-all` mode depend on Kafka and Redis semantics at the business layer even when the runtime goal is an all-in-one embedded deployment.

### Cache and state semantics are mixed

Some Redis usages are ordinary cache lookups. Others represent stateful semantics:

- session state and session indexes
- idempotency markers
- sequence allocation

Treating all of these as generic cache operations would blur critical consistency boundaries.

### Queue consumption is coupled to Kafka infrastructure

Consumers are currently discovered and bound through Kafka annotations. This prevents a default embedded queue from being swapped in transparently for `bootstrap-all`.

## Approaches Considered

### Approach A: Abstract queue and cache/state boundaries, keep Kafka/Redis as optional implementations

Introduce new domain-neutral interfaces in `common-core`, migrate business modules to them, and select concrete implementations through auto-configuration.

Pros:

- clean dependency boundaries
- real middleware minimization in `bootstrap-all`
- straightforward distributed fallback with Kafka and Redis

Cons:

- larger migration surface now

### Approach B: Keep business code on Kafka/Redis APIs and add bootstrap-only bridges

Add bootstrap-only emulation layers while preserving direct middleware usage in modules.

Pros:

- smaller immediate code change

Cons:

- two parallel programming models
- bootstrap mode still conceptually depends on Kafka/Redis
- future maintenance cost stays high

### Chosen direction

Use Approach A. The point of `bootstrap-all` is not only to swap startup wiring, but to remove middleware coupling from business code paths.

## Design Summary

### Queue

- Introduce `QueueAdapter` with `send` and `subscribe`
- Add `@QueueListener(topic, group, concurrency)` on consumer methods
- Add `@QueueProducer` as a marker annotation for queue-producing beans
- Implement:
  - `ChronicleQueueAdapter` for embedded default usage
  - `KafkaQueueAdapter` for distributed usage
- Add `QueueAutoConfigurer` to select the implementation through `app.queue.type`
- Add a Spring `BeanPostProcessor` to scan `@QueueListener` methods and register subscriptions at startup

### Cache

- L1 is always `Caffeine`
- L2 is selected automatically:
  - `RedisL2CacheAdapter` when `spring.redis.host` is configured
  - `RocksDbL2CacheAdapter` otherwise
- Business code reads through `MultiLevelCacheService`, not through Redis or RocksDB directly

### State stores

Use dedicated abstractions rather than forcing stateful semantics through the cache layer:

- `SessionStateStore`
- `IdempotencyStore`
- `SequenceStore`

For `bootstrap-all`, these default to embedded implementations backed by `RocksDB` and local memory where appropriate.

## Queue Design

### Unified interface

The queue boundary is:

```java
public interface QueueAdapter {
    <T> void send(String topic, String key, T message);

    <T> Subscription subscribe(
            String topic,
            String group,
            int concurrency,
            Class<T> payloadType,
            QueueMessageHandler<T> handler
    );
}
```

Supporting types:

- `QueueMessageHandler<T>` for typed callbacks
- `Subscription` for shutdown/unbind lifecycle
- `QueueEnvelope` if needed internally for metadata such as topic, key, timestamp, and raw payload

The external contract is "arbitrary object payload serialized as Jackson JSON".

### Listener annotations

`@QueueListener(topic, group, concurrency)` is method-level.

Rules:

- exactly one business payload parameter is required
- payload type is inferred from the method parameter
- `group` defines the consumer group semantics
- `concurrency` controls worker count for the subscription

`@QueueProducer` is type-level. It does not hide send semantics behind AOP. It marks a bean as a queue-producing component so queue usage is discoverable and can later be instrumented consistently.

### Queue listener binding

`QueueListenerBeanPostProcessor` scans beans after initialization:

1. discover methods annotated with `@QueueListener`
2. validate method signature
3. infer payload type
4. register a subscription against the active `QueueAdapter`
5. invoke the method when a typed message is deserialized successfully

Listener binding stays independent of Kafka annotations, so the same business method can run on Chronicle or Kafka.

### Chronicle adapter

`ChronicleQueueAdapter` is the default `bootstrap-all` queue.

Behavior:

- embedded local persistence per topic under a configurable queue directory
- JSON serialization through the shared `ObjectMapper`
- in-process subscriber workers per topic/group
- at-least-once local delivery semantics

Limits:

- suitable for single-node embedded deployment
- not a distributed broker replacement

### Kafka adapter

`KafkaQueueAdapter` maps the same boundary to Kafka:

- `send` delegates to Kafka producer infrastructure
- `subscribe` spins up message listener containers programmatically per `topic/group`
- payloads are JSON, matching the Chronicle path

This preserves distributed deployment support without keeping Kafka-specific annotations in business code.

### Auto-configuration

`QueueAutoConfigurer` chooses the active adapter:

- `app.queue.type=chronicle` -> `ChronicleQueueAdapter`
- `app.queue.type=kafka` -> `KafkaQueueAdapter`

`chronicle` is the `bootstrap-all` default.

## Cache Design

### L2 cache boundary

The L2 contract is intentionally narrow:

```java
public interface L2CacheAdapter {
    <T> T get(String key, Class<T> type);

    void put(String key, Object value, Duration ttl);

    void evict(String key);
}
```

This remains "string key + arbitrary object value + optional TTL".

### Unified read path

Business code should read through `MultiLevelCacheService`:

1. query L1 `Caffeine`
2. on L1 miss, query L2
3. on L2 hit, refill L1 and return
4. on L2 miss, call the loader
5. write the loaded value into both L1 and L2

This gives one place to encode cache chaining and refill policy.

### L1

L1 is always enabled with `Caffeine`:

- low-latency process-local hits
- fixed enablement regardless of runtime mode

### L2 Redis

`RedisL2CacheAdapter` is selected when `spring.redis.host` is present.

Behavior:

- serialize values as JSON
- preserve TTL
- remain purely an L2 cache implementation, not a general Redis escape hatch

### L2 RocksDB

`RocksDbL2CacheAdapter` is the embedded fallback when Redis is absent.

Behavior:

- store serialized JSON values plus expiration metadata
- apply lazy expiration on read and write
- use a local data directory in `bootstrap-all`

## State Store Design

Cache auto-degrade is not sufficient for stateful Redis usages. These require explicit contracts.

### Session state

Introduce `SessionStateStore`.

Responsibilities:

- `sessionId -> session` lookup
- `userId -> sessionId set` index
- `userId + deviceId -> sessionId` lookup
- TTL-aware expiration

`bootstrap-all` default:

- durable state in `RocksDB`
- hot lookups optionally accelerated by `Caffeine`
- expiration represented as stored metadata with lazy cleanup and scheduled sweeping

Distributed deployment can keep a Redis-backed implementation.

### Idempotency

Introduce `IdempotencyStore`.

Required contract:

```java
boolean putIfAbsent(String key, Duration ttl);
```

This replaces `SETNX + EXPIRE` style usage directly.

`bootstrap-all` default:

- `RocksDB` persistence
- expiration metadata stored alongside the marker
- expired markers removed opportunistically

### Sequence allocation

Introduce `SequenceStore` with range reservation per conversation.

Required model:

- allocate by `conversationId`
- reserve a contiguous range
- never move backwards after restart
- gaps are acceptable

Suggested contract:

```java
public interface SequenceStore {
    SequenceRange reserve(String conversationId, int size);
}
```

`SequenceRange` contains:

- `startInclusive`
- `endInclusive`

Runtime allocation model:

1. reserve a range per `conversationId`
2. keep the active range in memory
3. issue seq numbers from memory quickly
4. when exhausted, reserve the next range

`bootstrap-all` default:

- persist the reserved upper bound in `RocksDB`
- keep current range state in memory
- after crash/restart, reserve from the persisted upper bound forward

This guarantees monotonic non-regression and tolerates gaps, which is the correct trade-off for IM session sequencing.

### Why range reservation is chosen

Alternative considered:

- synchronous durable increment on every message

That gives harder durability but imposes storage I/O on the chat ingress hot path. For IM, the stronger practical requirement is "per-conversation sequence never goes backward". Gaps are acceptable; rollback is not. Range reservation satisfies that with much better throughput.

## Distributed Deployment Semantics

In distributed deployment:

- `KafkaQueueAdapter` remains the correct queue implementation
- Redis-backed `SessionStateStore`, `IdempotencyStore`, and `L2CacheAdapter` may remain active
- `SequenceStore` should use a Redis-backed implementation for shared atomic range reservation across nodes

Redis provides stronger multi-node sequence semantics than local embedded storage:

- shared monotonic growth across instances
- no duplicate per-conversation allocation across nodes
- simpler coordination

However, Redis sequence allocation alone does not guarantee end-to-end processing order. If stricter same-conversation ordering is required from allocation through delivery, the system should also keep or introduce conversation-keyed serial processing.

## Migration Plan

### Queue migration

Replace:

- `@KafkaListener` -> `@QueueListener`
- `KafkaTemplate` sends -> `QueueAdapter.send(...)` or thin producer wrappers

Target modules include current listeners and publishers in `postman` and `postbox`, plus any other bootstrap-path Kafka usage.

### Cache migration

Replace cache-style Redis access with `MultiLevelCacheService` and `L2CacheAdapter`.

Do not blindly migrate stateful Redis logic into the cache layer. Those paths should move to the dedicated stores above.

### Session/idempotency/sequence migration

Redis-backed stateful components should be refactored by semantic role:

- session services -> `SessionStateStore`
- dedup/idempotency services -> `IdempotencyStore`
- conversation sequence services -> `SequenceStore`

### Bootstrap application changes

`bootstrap-all` should:

- default `app.queue.type` to `chronicle`
- stop requiring `@EnableKafka`
- run without Redis by selecting RocksDB-backed L2 and local state stores automatically

## Testing Strategy

### Queue

- annotation scan binds listeners correctly
- Chronicle send/subscribe delivers typed payloads
- Kafka adapter honors the same typed contract
- consumer group and concurrency settings are applied

### Cache

- L1 hit returns immediately
- L1 miss + L2 hit refills L1
- L1/L2 miss triggers loader and writes both levels
- Redis and RocksDB L2 implementations share the same behavior contract

### State stores

- session indexes stay consistent across writes and removals
- idempotency returns false on duplicate non-expired keys
- sequence allocation never returns overlapping or regressing ranges for the same conversation
- restart recovery resumes from a forward-only high watermark

### Migration safety

- existing business flows continue to publish and consume ingress, history, delivery, and retry events through the new queue abstraction
- `bootstrap-all` startup succeeds with no Kafka and no Redis configured

## Open Questions Resolved

- Queue payload format: arbitrary object payloads using shared Jackson JSON serialization
- Cache boundary: generic string-key/object-value cache with optional TTL
- Stateful Redis semantics: moved to dedicated embedded-capable store abstractions
- Sequence allocation in `bootstrap-all`: use conversation-scoped range reservation rather than per-message durable increment
