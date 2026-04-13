# Conversation Seq Allocator Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace message-seq allocation with a Mongo-truth conversation allocator that uses Redis in cluster mode and RocksDB in standalone mode as cache segments.

**Architecture:** Keep `ConversationRangeRepository` as the only durable high-watermark source. Add a `ConversationSeqCacheStore` abstraction for Redis/RocksDB cache segments, then route `postmaster` seq allocation through a new `ConversationSeqAllocator`. Preserve `SequenceIdGenerator` for non-message IDs only.

**Tech Stack:** Java, Spring Boot, MongoTemplate, StringRedisTemplate, RocksDB support, JUnit 5, Mockito.

---

### Task 1: Add allocator contracts and cache-segment model

**Files:**
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqRangeState.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqCacheResult.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqCacheStore.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqAllocatorContractTest.java`

- [ ] Step 1: Write the failing contract test for cache result semantics
- [ ] Step 2: Run `./gradlew :common-core:test --tests com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqAllocatorContractTest`
- [ ] Step 3: Add the minimal model and interface types
- [ ] Step 4: Re-run the same test and confirm it passes

### Task 2: Implement RocksDB cache-segment store

**Files:**
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/rocksdb/RocksDbConversationSeqCacheStore.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/store/sequence/conversation/rocksdb/RocksDbConversationSeqCacheStoreTest.java`

- [ ] Step 1: Write a failing test for first allocation, exhausted allocation, and getMaxSeq
- [ ] Step 2: Run `./gradlew :common-core:test --tests com.cheeseocean.im.common.core.store.sequence.conversation.rocksdb.RocksDbConversationSeqCacheStoreTest`
- [ ] Step 3: Implement the minimal RocksDB store with local locking and persisted `CURR/LAST/TIME`
- [ ] Step 4: Re-run the RocksDB test and confirm it passes

### Task 3: Implement Redis cache-segment store

**Files:**
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/redis/RedisConversationSeqCacheStore.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/store/sequence/conversation/redis/RedisConversationSeqCacheStoreScriptTest.java`

- [ ] Step 1: Write failing tests for redis-state transitions `0/1/2/3`
- [ ] Step 2: Run `./gradlew :common-core:test --tests com.cheeseocean.im.common.core.store.sequence.conversation.redis.RedisConversationSeqCacheStoreScriptTest`
- [ ] Step 3: Implement Lua-backed Redis store
- [ ] Step 4: Re-run the Redis store test and confirm it passes

### Task 4: Implement ConversationSeqAllocator

**Files:**
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqAllocator.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqAllocatorTest.java`

- [ ] Step 1: Write failing tests for redis-miss initialization, cache exhaustion expansion, mongo-rewrite-on-mismatch, and standalone RocksDB path
- [ ] Step 2: Run `./gradlew :common-core:test --tests com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqAllocatorTest`
- [ ] Step 3: Implement allocator against `ConversationRangeRepository` and `ConversationSeqCacheStore`
- [ ] Step 4: Re-run the allocator test and confirm it passes

### Task 5: Wire allocator into runtime configuration

**Files:**
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqAllocatorConfigurer.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqAllocatorProperties.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/store/sequence/id/SequenceIdGeneratorConfigurer.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/store/sequence/conversation/ConversationSeqAllocatorConfigurerTest.java`

- [ ] Step 1: Write failing tests for `cluster` fail-fast without Redis and `standalone` RocksDB fallback
- [ ] Step 2: Run `./gradlew :common-core:test --tests com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqAllocatorConfigurerTest`
- [ ] Step 3: Implement config properties and bean wiring
- [ ] Step 4: Re-run the configurer test and confirm it passes

### Task 6: Switch postmaster message seq allocation to the new allocator

**Files:**
- Modify: `server/postmaster/src/main/java/com/cheeseocean/im/postmaster/service/ConversationSeqService.java`
- Modify: `server/postmaster/src/test/java/com/cheeseocean/im/postmaster/service/ConversationSeqServiceTest.java`

- [ ] Step 1: Write the failing service test expecting `ConversationSeqAllocator`
- [ ] Step 2: Run `./gradlew :postmaster:test --tests com.cheeseocean.im.postmaster.service.ConversationSeqServiceTest`
- [ ] Step 3: Replace `SequenceIdGenerator` dependency with `ConversationSeqAllocator`
- [ ] Step 4: Re-run the same test and confirm it passes

### Task 7: Run focused verification

**Files:**
- Modify: none

- [ ] Step 1: Run `./gradlew :common-core:test --tests com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqAllocatorContractTest --tests com.cheeseocean.im.common.core.store.sequence.conversation.rocksdb.RocksDbConversationSeqCacheStoreTest --tests com.cheeseocean.im.common.core.store.sequence.conversation.redis.RedisConversationSeqCacheStoreScriptTest --tests com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqAllocatorTest --tests com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqAllocatorConfigurerTest`
- [ ] Step 2: Run `./gradlew :postmaster:test --tests com.cheeseocean.im.postmaster.service.ConversationSeqServiceTest`
- [ ] Step 3: Run `./gradlew :common-core:compileJava :postmaster:compileJava`
- [ ] Step 4: Record any residual failures outside this scope before completion
