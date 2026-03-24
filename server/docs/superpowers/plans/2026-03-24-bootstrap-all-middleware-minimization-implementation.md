# Bootstrap-All Middleware Minimization Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build embedded-first queue, cache, and state-store abstractions so `bootstrap-all` can run without Kafka and Redis while preserving distributed adapters for Kafka and Redis deployments.

**Architecture:** Add new middleware-neutral interfaces and auto-configuration in `common-core`, backed by Chronicle Queue, Kafka, Caffeine, Redis, RocksDB, and dedicated embedded state stores. Then migrate bootstrap-path business modules off direct `KafkaTemplate`, `@KafkaListener`, and cache-style Redis access, while replacing Redis-based sequence generation with a conversation-scoped range reservation model.

**Tech Stack:** Spring Boot 3.2, BeanPostProcessor, Chronicle Queue, Kafka, Caffeine, RocksDB, Jackson, JUnit 5, Mockito

---

## File Structure

### New queue abstraction and configuration

- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/QueueAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/QueueMessageHandler.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/Subscription.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/annotation/QueueListener.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/annotation/QueueProducer.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/config/QueueProperties.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/config/QueueAutoConfigurer.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/config/QueueListenerBeanPostProcessor.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/chronicle/ChronicleQueueAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/kafka/KafkaQueueAdapter.java`

### New cache and state-store abstraction

- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/L2CacheAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/MultiLevelCacheService.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/config/CacheProperties.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/config/CacheAutoConfigurer.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/redis/RedisL2CacheAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/rocksdb/RocksDbL2CacheAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/session/SessionStateStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/session/redis/RedisSessionStateStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/session/rocksdb/RocksDbSessionStateStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/idempotency/IdempotencyStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/idempotency/redis/RedisIdempotencyStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/idempotency/rocksdb/RocksDbIdempotencyStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/SequenceStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/SequenceRange.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/ConversationSequenceAllocator.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/redis/RedisSequenceStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/rocksdb/RocksDbSequenceStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/rocksdb/RocksDbSupport.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/rocksdb/ExpiringValue.java`

### Migrations in business modules

- Modify: `postman/src/main/java/com/cheeseocean/im/postman/listener/IngressEventListener.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/listener/DeliveryCompensationListener.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/listener/HistoryEventListener.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/IngressEventPublisher.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/ConversationSeqService.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageIdempotencyService.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/ConsumerDedupService.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/repository/SessionRepository.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/repository/UserSecurityRepository.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/session/SessionIssueServiceImpl.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/session/SessionQueryServiceImpl.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/session/SessionRevocationServiceImpl.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/RedisOnlineRouteService.java`
- Modify: `bootstrap-all/src/main/java/com/cheeseocean/im/bootstrap/all/AllInOneApplication.java`
- Modify: `bootstrap-all/build.gradle`
- Modify: `common-core/build.gradle`
- Modify: `config/src/main/resources/application-all.yml`

### Tests

- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/queue/config/QueueListenerBeanPostProcessorTest.java`
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/queue/chronicle/ChronicleQueueAdapterTest.java`
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/cache/MultiLevelCacheServiceTest.java`
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/cache/rocksdb/RocksDbL2CacheAdapterTest.java`
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/store/idempotency/rocksdb/RocksDbIdempotencyStoreTest.java`
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/store/sequence/rocksdb/RocksDbSequenceStoreTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/ConversationSeqServiceTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/ConsumerDedupServiceTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/DeliveryCompensationServiceTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/listener/HistoryEventListenerTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/IngressEventPublisherTest.java`

### Task 1: Add Queue Abstractions and Failing Tests

**Files:**
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/queue/config/QueueListenerBeanPostProcessorTest.java`
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/queue/chronicle/ChronicleQueueAdapterTest.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/QueueAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/QueueMessageHandler.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/Subscription.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/annotation/QueueListener.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/annotation/QueueProducer.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/config/QueueListenerBeanPostProcessor.java`

- [ ] **Step 1: Write the failing listener binding test**

```java
@Test
void shouldSubscribeAnnotatedMethodUsingPayloadParameterType() {
    TestQueueAdapter adapter = new TestQueueAdapter();
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(QueueAdapter.class, () -> adapter);
    context.registerBean(QueueListenerBeanPostProcessor.class);
    context.registerBean(TestConsumer.class);
    context.refresh();

    adapter.dispatch("topic-a", new DemoPayload("v1"));

    assertThat(context.getBean(TestConsumer.class).received()).containsExactly("v1");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.queue.config.QueueListenerBeanPostProcessorTest"`
Expected: FAIL because queue annotations and binder do not exist yet

- [ ] **Step 3: Write the failing Chronicle adapter delivery test**

```java
@Test
void shouldDeliverJsonPayloadToSubscriber() throws Exception {
    ChronicleQueueAdapter adapter = new ChronicleQueueAdapter(objectMapper, properties);
    List<String> received = new CopyOnWriteArrayList<>();
    adapter.subscribe("ingress", "g1", 1, DemoPayload.class, payload -> received.add(payload.value()));
    adapter.send("ingress", "key1", new DemoPayload("ok"));

    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
            assertThat(received).containsExactly("ok"));
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.queue.chronicle.ChronicleQueueAdapterTest"`
Expected: FAIL because Chronicle adapter does not exist yet

- [ ] **Step 5: Implement minimal queue interfaces, annotations, and listener binder**

- [ ] **Step 6: Implement minimal Chronicle adapter to pass the delivery test**

- [ ] **Step 7: Run both tests to verify they pass**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.queue.config.QueueListenerBeanPostProcessorTest" --tests "com.cheeseocean.im.common.core.queue.chronicle.ChronicleQueueAdapterTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/queue common-core/src/test/java/com/cheeseocean/im/common/core/queue
git commit -m "feat: add queue abstraction and chronicle adapter"
```

### Task 2: Add Kafka Queue Adapter and Queue Auto-Configuration

**Files:**
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/config/QueueProperties.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/config/QueueAutoConfigurer.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/queue/kafka/KafkaQueueAdapter.java`
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/config/KafkaSerializationConfig.java`
- Modify: `common-core/build.gradle`

- [ ] **Step 1: Write the failing auto-configuration test for chronicle default**

```java
@Test
void shouldCreateChronicleAdapterByDefault() {
    new ApplicationContextRunner()
            .withUserConfiguration(QueueAutoConfigurer.class)
            .run(context -> assertThat(context).hasSingleBean(ChronicleQueueAdapter.class));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests "*QueueAutoConfigurer*"`
Expected: FAIL because queue properties and auto-configuration do not exist yet

- [ ] **Step 3: Write the failing auto-configuration test for kafka selection**

```java
@Test
void shouldCreateKafkaAdapterWhenQueueTypeIsKafka() {
    new ApplicationContextRunner()
            .withPropertyValues("app.queue.type=kafka", "spring.kafka.bootstrap-servers=localhost:9092")
            .withUserConfiguration(QueueAutoConfigurer.class, KafkaSerializationConfig.class)
            .run(context -> assertThat(context).hasSingleBean(KafkaQueueAdapter.class));
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests "*QueueAutoConfigurer*"`
Expected: FAIL because Kafka adapter selection is not implemented

- [ ] **Step 5: Implement queue properties, auto-configuration, and Kafka adapter**

- [ ] **Step 6: Add Chronicle Queue dependency and any required Kafka container wiring**

- [ ] **Step 7: Run queue auto-configuration tests to verify they pass**

Run: `./gradlew :common-core:test --tests "*QueueAutoConfigurer*"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add common-core/build.gradle common-core/src/main/java/com/cheeseocean/im/common/core/queue common-core/src/test/java/com/cheeseocean/im/common/core/queue
git commit -m "feat: add queue auto configuration"
```

### Task 3: Add Cache Abstractions and RocksDB Fallback

**Files:**
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/cache/MultiLevelCacheServiceTest.java`
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/cache/rocksdb/RocksDbL2CacheAdapterTest.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/L2CacheAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/MultiLevelCacheService.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/config/CacheProperties.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/config/CacheAutoConfigurer.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/redis/RedisL2CacheAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/rocksdb/RocksDbL2CacheAdapter.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/rocksdb/RocksDbSupport.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/rocksdb/ExpiringValue.java`
- Modify: `common-core/build.gradle`

- [ ] **Step 1: Write the failing multi-level cache chain test**

```java
@Test
void shouldLoadFromL2AndRefillL1() {
    FakeL2CacheAdapter l2 = new FakeL2CacheAdapter(Map.of("k1", new DemoValue("v1")));
    MultiLevelCacheService service = new MultiLevelCacheService(caffeineCache(), l2);

    DemoValue value = service.getOrLoad("k1", DemoValue.class, Duration.ofMinutes(5), () -> fail("loader should not run"));

    assertThat(value.value()).isEqualTo("v1");
    assertThat(service.peekL1("k1", DemoValue.class)).isPresent();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.cache.MultiLevelCacheServiceTest"`
Expected: FAIL because cache abstraction does not exist yet

- [ ] **Step 3: Write the failing RocksDB TTL expiration test**

```java
@Test
void shouldReturnNullAfterTtlExpires() {
    RocksDbL2CacheAdapter adapter = new RocksDbL2CacheAdapter(objectMapper, properties);
    adapter.put("k1", new DemoValue("v1"), Duration.ofMillis(50));
    Thread.sleep(100);

    assertThat(adapter.get("k1", DemoValue.class)).isNull();
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.cache.rocksdb.RocksDbL2CacheAdapterTest"`
Expected: FAIL because RocksDB adapter does not exist yet

- [ ] **Step 5: Implement cache interfaces, service, RocksDB support classes, and RocksDB L2 adapter**

- [ ] **Step 6: Implement Redis L2 adapter and conditional auto-configuration with fixed Caffeine L1**

- [ ] **Step 7: Run cache tests to verify they pass**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.cache.MultiLevelCacheServiceTest" --tests "com.cheeseocean.im.common.core.cache.rocksdb.RocksDbL2CacheAdapterTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add common-core/build.gradle common-core/src/main/java/com/cheeseocean/im/common/core/cache common-core/src/main/java/com/cheeseocean/im/common/core/store/rocksdb common-core/src/test/java/com/cheeseocean/im/common/core/cache
git commit -m "feat: add multi level cache with rocksdb fallback"
```

### Task 4: Add Idempotency and Sequence Stores with Embedded Implementations

**Files:**
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/store/idempotency/rocksdb/RocksDbIdempotencyStoreTest.java`
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/store/sequence/rocksdb/RocksDbSequenceStoreTest.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/idempotency/IdempotencyStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/idempotency/redis/RedisIdempotencyStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/idempotency/rocksdb/RocksDbIdempotencyStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/SequenceStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/SequenceRange.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/ConversationSequenceAllocator.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/redis/RedisSequenceStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/rocksdb/RocksDbSequenceStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/config/StateStoreAutoConfigurer.java`

- [ ] **Step 1: Write the failing idempotency duplicate test**

```java
@Test
void shouldRejectDuplicateMarkerBeforeExpiry() {
    RocksDbIdempotencyStore store = new RocksDbIdempotencyStore(properties);

    assertThat(store.putIfAbsent("dup:1", Duration.ofMinutes(1))).isTrue();
    assertThat(store.putIfAbsent("dup:1", Duration.ofMinutes(1))).isFalse();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.store.idempotency.rocksdb.RocksDbIdempotencyStoreTest"`
Expected: FAIL because idempotency store does not exist yet

- [ ] **Step 3: Write the failing sequence range reservation test**

```java
@Test
void shouldReserveNonOverlappingRangesForSameConversation() {
    RocksDbSequenceStore store = new RocksDbSequenceStore(properties);

    SequenceRange first = store.reserve("c1", 100);
    SequenceRange second = store.reserve("c1", 100);

    assertThat(first.startInclusive()).isEqualTo(1L);
    assertThat(first.endInclusive()).isEqualTo(100L);
    assertThat(second.startInclusive()).isEqualTo(101L);
    assertThat(second.endInclusive()).isEqualTo(200L);
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.store.sequence.rocksdb.RocksDbSequenceStoreTest"`
Expected: FAIL because sequence store does not exist yet

- [ ] **Step 5: Implement embedded and Redis idempotency and sequence stores, plus auto-configuration**

- [ ] **Step 6: Implement conversation-level in-memory allocator using reserved ranges**

- [ ] **Step 7: Run idempotency and sequence tests to verify they pass**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.store.idempotency.rocksdb.RocksDbIdempotencyStoreTest" --tests "com.cheeseocean.im.common.core.store.sequence.rocksdb.RocksDbSequenceStoreTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/store common-core/src/test/java/com/cheeseocean/im/common/core/store
git commit -m "feat: add embedded idempotency and sequence stores"
```

### Task 5: Add Session State Store and Migrate Auth Session Access

**Files:**
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/session/SessionStateStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/session/redis/RedisSessionStateStore.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/store/session/rocksdb/RocksDbSessionStateStore.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/repository/SessionRepository.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/session/SessionIssueServiceImpl.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/session/SessionQueryServiceImpl.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/session/SessionRevocationServiceImpl.java`
- Modify: `authcenter/src/main/java/com/cheeseocean/im/authcenter/repository/UserSecurityRepository.java`

- [ ] **Step 1: Write the failing session store repository test**

```java
@Test
void shouldLoadSessionBySessionIdThroughSessionStore() {
    SessionStateStore store = mock(SessionStateStore.class);
    SessionPrincipal principal = new SessionPrincipal();
    principal.setSessionId("s1");
    when(store.getSession("s1")).thenReturn(principal);

    SessionRepository repository = new SessionRepository(store);

    assertThat(repository.findBySessionId("s1")).isSameAs(principal);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :authcenter:test --tests "*SessionRepository*"`
Expected: FAIL because repository still depends on RedisTemplate

- [ ] **Step 3: Implement session store contract and embedded/Redis implementations**

- [ ] **Step 4: Refactor authcenter session services and repositories to depend on `SessionStateStore`**

- [ ] **Step 5: Run focused authcenter tests to verify they pass**

Run: `./gradlew :authcenter:test --tests "*SessionRepository*" --tests "*SessionQueryServiceImpl*" --tests "*SessionRevocationServiceImpl*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/store/session authcenter/src/main/java/com/cheeseocean/im/authcenter
git commit -m "feat: add session store abstraction"
```

### Task 6: Migrate Postman and Postbox to Queue and State Abstractions

**Files:**
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/listener/IngressEventListener.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/listener/DeliveryCompensationListener.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/listener/HistoryEventListener.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/IngressEventPublisher.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/ConversationSeqService.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageIdempotencyService.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/ConsumerDedupService.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/ConversationSeqServiceTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/ConsumerDedupServiceTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/DeliveryCompensationServiceTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/listener/HistoryEventListenerTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/IngressEventPublisherTest.java`

- [ ] **Step 1: Write the failing publisher migration test**

```java
@Test
void publishShouldSendIngressEventThroughQueueAdapter() {
    QueueAdapter adapter = mock(QueueAdapter.class);
    IngressEventPublisher publisher = new IngressEventPublisher(adapter);
    IngressEvent event = new IngressEvent();
    event.setConversationId("c1");

    publisher.publish(event);

    verify(adapter).send(TopicNames.INGRESS, "c1", event);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :postbox:test --tests "*IngressEventPublisherTest"`
Expected: FAIL because publisher still depends on KafkaTemplate

- [ ] **Step 3: Write the failing sequence service range-allocation test**

```java
@Test
void nextSeqShouldUseConversationAllocator() {
    ConversationSequenceAllocator allocator = mock(ConversationSequenceAllocator.class);
    when(allocator.next("c1:userA:userB")).thenReturn(1001L);
    ConversationSeqService service = new ConversationSeqService(allocator);

    assertThat(service.nextSeq("c1:userA:userB")).isEqualTo(1001L);
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :postman:test --tests "*ConversationSeqServiceTest"`
Expected: FAIL because service still depends on Redis increment

- [ ] **Step 5: Refactor publishers and listeners to use queue annotations and adapter**

- [ ] **Step 6: Refactor postman dedup and sequence services to use `IdempotencyStore` and `ConversationSequenceAllocator`**

- [ ] **Step 7: Run focused postman and postbox tests to verify they pass**

Run: `./gradlew :postman:test --tests "*IngressEventListenerTest" --tests "*ConversationSeqServiceTest" --tests "*ConsumerDedupServiceTest" --tests "*DeliveryCompensationServiceTest" :postbox:test --tests "*HistoryEventListenerTest" --tests "*IngressEventPublisherTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add postman/src/main/java/com/cheeseocean/im/postman postman/src/test/java/com/cheeseocean/im/postman postbox/src/main/java/com/cheeseocean/im/postbox postbox/src/test/java/com/cheeseocean/im/postbox
git commit -m "refactor: migrate messaging flow to queue abstractions"
```

### Task 7: Migrate Remaining Bootstrap-Path Cache and State Usage

**Files:**
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/RedisOnlineRouteService.java`
- Modify: any bootstrap-path services found by `rg "RedisTemplate|StringRedisTemplate"` after Tasks 5-6
- Add tests adjacent to each migrated service as needed

- [ ] **Step 1: Search for remaining bootstrap-path direct Redis usage**

Run: `rg -n "RedisTemplate|StringRedisTemplate" authcenter postman postbox postoffice bootstrap-all common-core`
Expected: only adapters and explicitly retained distributed implementations remain

- [ ] **Step 2: Write failing test for each remaining cache-style Redis dependency before refactoring**

- [ ] **Step 3: Refactor cache-style paths to `MultiLevelCacheService` and stateful paths to their dedicated stores**

- [ ] **Step 4: Run focused module tests to verify the migrations pass**

Run: `./gradlew :authcenter:test :postman:test :postbox:test :postoffice:test`
Expected: PASS for migrated modules

- [ ] **Step 5: Commit**

```bash
git add authcenter postman postbox postoffice common-core
git commit -m "refactor: remove bootstrap path redis dependencies"
```

### Task 8: Wire Bootstrap-All Defaults and Verify No Kafka/Redis Requirement

**Files:**
- Modify: `bootstrap-all/src/main/java/com/cheeseocean/im/bootstrap/all/AllInOneApplication.java`
- Modify: `bootstrap-all/build.gradle`
- Modify: `config/src/main/resources/application-all.yml`

- [ ] **Step 1: Write the failing bootstrap context test**

```java
@Test
void bootstrapAllShouldNotRequireEnableKafkaWhenQueueTypeIsChronicle() {
    new SpringApplicationBuilder(AllInOneApplication.class)
            .properties(
                    "spring.config.name=application-all",
                    "app.queue.type=chronicle",
                    "spring.redis.host=")
            .run()
            .close();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :bootstrap-all:test --tests "*AllInOneApplication*"`
Expected: FAIL before bootstrap defaults and bean wiring are complete

- [ ] **Step 3: Remove hard `@EnableKafka` requirement, set bootstrap defaults, and add embedded dependencies**

- [ ] **Step 4: Run bootstrap-focused verification**

Run: `./gradlew :bootstrap-all:test --tests "*AllInOneApplication*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add bootstrap-all/build.gradle bootstrap-all/src/main/java/com/cheeseocean/im/bootstrap/all/AllInOneApplication.java config/src/main/resources/application-all.yml
git commit -m "feat: make bootstrap all embedded first"
```

### Task 9: Full Verification

**Files:**
- No new files expected

- [ ] **Step 1: Run common-core tests**

Run: `./gradlew :common-core:test`
Expected: PASS

- [ ] **Step 2: Run affected module tests**

Run: `./gradlew :authcenter:test :postman:test :postbox:test :postoffice:test :bootstrap-all:test`
Expected: PASS

- [ ] **Step 3: Run whole-project verification if practical**

Run: `./gradlew test`
Expected: PASS or a documented list of unrelated existing failures

- [ ] **Step 4: Search for direct bootstrap-path Kafka and Redis usage**

Run: `rg -n "@KafkaListener|KafkaTemplate|RedisTemplate|StringRedisTemplate" authcenter postman postbox postoffice bootstrap-all`
Expected: only adapter implementations or intentionally retained distributed-only beans remain

- [ ] **Step 5: Review final diff**

Run: `git status --short && git diff --stat`
Expected: queue, cache, state-store, and migration files only
