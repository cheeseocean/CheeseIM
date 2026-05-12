# Repository Rebuild Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Java Mongo repository layer for conversation, sequence, user, friendship, and friend request storage to match the Go Mongo semantics while preserving current Java domain meaning.

**Architecture:** Replace the current repository contracts and implementations for the six target areas with MongoTemplate-driven repositories modeled after the Go `mgo` package. Keep document models aligned to existing Java fields where they already match business meaning, but remove stale conversation preview fields and split friendship versus friend request persistence into separate repositories.

**Tech Stack:** Java, Spring Boot, Spring Data MongoDB, MongoTemplate, JUnit 5, Mockito, Gradle

---

## File Structure

### Repository contracts

- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/UserConversationRepository.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/ConversationRangeRepository.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/UserConversationSyncPointRepository.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/UserRepository.java`
- Delete: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/FriendRepository.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/FriendshipRepository.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/FriendRequestRepository.java`

### Mongo implementations

- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationRepositoryImpl.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationRangeRepositoryImpl.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationSyncPointRepositoryImpl.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/UserRepositoryImpl.java`
- Delete: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendRepositoryImpl.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendshipRepositoryImpl.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendRequestRepositoryImpl.java`

### Document models

- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/conversation/UserConversationDoc.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/conversation/ConversationRangeDoc.java` only if current shape is insufficient
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/conversation/UserConversationSyncPointDoc.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/user/UserDoc.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/user/FriendshipDoc.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/user/FriendRequestDoc.java`

### Service consumers

- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationLifecycleService.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationQueryServiceImpl.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationWriteServiceImpl.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ReadSeqPersistenceWriter.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/friend/FriendService.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/user/UserServiceImpl.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/config/CommonMongoPersistenceConfiguration.java`

### Tests

- Modify: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationRepositoryImplTest.java`
- Modify: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationSyncPointRepositoryImplTest.java`
- Create: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationRangeRepositoryImplTest.java`
- Create: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserRepositoryImplTest.java`
- Create: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendshipRepositoryImplTest.java`
- Create: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendRequestRepositoryImplTest.java`
- Modify: `server/business/src/test/java/com/cheeseocean/im/business/service/conversation/ConversationQueryServiceImplTest.java`
- Modify: `server/business/src/test/java/com/cheeseocean/im/business/service/conversation/ConversationLifecycleServiceTest.java`
- Modify: `server/business/src/test/java/com/cheeseocean/im/business/service/FriendServiceTest.java`

### Docs

- Modify: `docs/superpowers/specs/2026-04-09-repository-rebuild-design.md` only if implementation discovers spec drift

## Task 1: Rebuild Conversation Repository Contracts

**Files:**
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/UserConversationRepository.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/ConversationRangeRepository.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/UserConversationSyncPointRepository.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationRepositoryImplTest.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationSyncPointRepositoryImplTest.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationRangeRepositoryImplTest.java`

- [ ] **Step 1: Write the failing repository contract tests**

Add tests covering:
- `UserConversationRepository.findPinnedConversationIds(...)`
- `UserConversationRepository.updateBatchFields(...)`
- `ConversationRangeRepository.allocate(...)`
- `UserConversationSyncPointRepository.getReadSeqMap(...)`
- `UserConversationSyncPointRepository.updateReadSeq(...)` no-regression behavior

- [ ] **Step 2: Run the focused tests to verify missing methods or wrong behavior**

Run:
```bash
cd server
./gradlew :common-core:test --tests com.cheeseocean.im.common.core.business.mongo.impl.UserConversationRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.UserConversationSyncPointRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.ConversationSequenceRepositoryImplTest
```

Expected:
- test compile failures for missing repository methods or missing implementation

- [ ] **Step 3: Rewrite the repository interfaces**

Implement the new method sets from the spec:
- `UserConversationRepository`
- `ConversationRangeRepository`
- `UserConversationSyncPointRepository`

Keep methods narrowly scoped to the actual Go-equivalent semantics that Java needs now.

- [ ] **Step 4: Run `:common-core:compileJava`**

Run:
```bash
cd server
./gradlew :common-core:compileJava
```

Expected:
- compile failures only in implementations and callers, not in the interface declarations

- [ ] **Step 5: Commit**

```bash
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM add server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM commit -m "refactor: rebuild conversation repository contracts"
```

## Task 2: Implement Conversation and Sequence Mongo Repositories

**Files:**
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationRepositoryImpl.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationRangeRepositoryImpl.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/conversation/UserConversationDoc.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/conversation/UserConversationSyncPointDoc.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/conversation/ConversationRangeDoc.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationRepositoryImplTest.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserConversationSyncPointRepositoryImplTest.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/ConversationRangeRepositoryImplTest.java`

- [ ] **Step 1: Write failing tests for MongoTemplate behavior**

Cover:
- user conversation upsert and batch field update
- pinned conversation ID query
- conversation range allocate/set/get semantics
- sync point readSeq only moves forward
- readSeq map fills missing conversations with zero

- [ ] **Step 2: Run the tests to verify failure**

Run:
```bash
cd server
./gradlew :common-core:test --tests com.cheeseocean.im.common.core.business.mongo.impl.UserConversationRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.UserConversationSyncPointRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.ConversationSequenceRepositoryImplTest
```

Expected:
- failures for missing implementation or wrong update/query behavior

- [ ] **Step 3: Implement the MongoTemplate repositories**

Implement:
- `UserConversationRepositoryImpl` with owner/conversation filters, projection queries, `updateFields`, `updateBatchFields`, pinned lookup, and not-receive lookup
- `ConversationRangeRepositoryImpl` with atomic `allocate`
- `UserConversationSyncPointRepositoryImpl` with per-user per-conversation min/max/read seq methods and map lookup

Use explicit `Query`, `Criteria`, `Update`, and `FindAndModifyOptions` where atomicity matters.

- [ ] **Step 4: Register the new repositories in `CommonMongoPersistenceConfiguration`**

Replace old bean definitions or add new ones so only the rebuilt implementations are wired.

- [ ] **Step 5: Run the focused tests again**

Run the same command from Step 2.

Expected:
- PASS

- [ ] **Step 6: Commit**

```bash
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM add server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM commit -m "feat: rebuild conversation and sequence mongo repositories"
```

## Task 3: Rebuild User Repository

**Files:**
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/UserRepository.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/UserRepositoryImpl.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/user/UserDoc.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserRepositoryImplTest.java`

- [ ] **Step 1: Write failing tests for the user queries**

Cover:
- batch create
- map-based update
- single and batch find
- exists
- nickname lookup
- notification/app-manager-level queries
- all-user-id paging
- global receive option lookup

- [ ] **Step 2: Run the focused user repository test**

Run:
```bash
cd server
./gradlew :common-core:test --tests com.cheeseocean.im.common.core.business.mongo.impl.UserRepositoryImplTest
```

Expected:
- FAIL for missing methods or mismatched query semantics

- [ ] **Step 3: Implement the user repository**

Use `MongoTemplate` rather than derived repository methods for the query paths that need projection or flexible keyword matching.

Do not include Go `userCommands` logic.

- [ ] **Step 4: Run the focused test again**

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM add server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/UserRepository.java server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/UserRepositoryImpl.java server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/UserRepositoryImplTest.java
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM commit -m "feat: rebuild user mongo repository"
```

## Task 4: Split Friendship and Friend Request Repositories

**Files:**
- Delete: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/FriendRepository.java`
- Delete: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendRepositoryImpl.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/FriendshipRepository.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/FriendRequestRepository.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendshipRepositoryImpl.java`
- Create: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendRequestRepositoryImpl.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/user/FriendshipDoc.java`
- Modify: `server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/user/FriendRequestDoc.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendshipRepositoryImplTest.java`
- Test: `server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl/FriendRequestRepositoryImplTest.java`

- [ ] **Step 1: Write failing tests for friendship and friend request behaviors**

Cover:
- friendship create, find, reverse find, owner list, updateFields, batch update, delete
- friend request create, find, both-direction lookup, incoming/outgoing queries, update, delete, unhandled count

- [ ] **Step 2: Run the focused tests to confirm failure**

Run:
```bash
cd server
./gradlew :common-core:test --tests com.cheeseocean.im.common.core.business.mongo.impl.FriendshipRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.FriendRequestRepositoryImplTest
```

Expected:
- FAIL due to missing split repositories

- [ ] **Step 3: Implement the split repositories**

Implement:
- `FriendshipRepositoryImpl`
- `FriendRequestRepositoryImpl`

Reference the Go query shapes for:
- owner/friend unique lookup
- reverse-friend lookup
- incoming/outgoing request filters by handle result
- unhandled-count query

- [ ] **Step 4: Run the focused tests again**

Expected:
- PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM add server/common-core/src/main/java/com/cheeseocean/im/common/core/business/repository server/common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl server/common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/impl
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM commit -m "feat: split friendship and friend request repositories"
```

## Task 5: Migrate Business Services to the New Repositories

**Files:**
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationLifecycleService.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationQueryServiceImpl.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ConversationWriteServiceImpl.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/conversation/ReadSeqPersistenceWriter.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/friend/FriendService.java`
- Modify: `server/business/src/main/java/com/cheeseocean/im/business/service/user/UserServiceImpl.java`
- Test: `server/business/src/test/java/com/cheeseocean/im/business/service/conversation/ConversationLifecycleServiceTest.java`
- Test: `server/business/src/test/java/com/cheeseocean/im/business/service/conversation/ConversationQueryServiceImplTest.java`
- Test: `server/business/src/test/java/com/cheeseocean/im/business/service/FriendServiceTest.java`

- [ ] **Step 1: Write or update failing service tests**

Adjust tests so they mock the new repository interfaces:
- `ConversationRangeRepository`
- `FriendshipRepository`
- `FriendRequestRepository`

Remove expectations tied to deleted repository methods.

- [ ] **Step 2: Run the service tests to verify failures**

Run:
```bash
cd server
./gradlew :business:test --tests com.cheeseocean.im.business.service.conversation.ConversationLifecycleServiceTest --tests com.cheeseocean.im.business.service.conversation.ConversationQueryServiceImplTest --tests com.cheeseocean.im.business.service.FriendRelationServiceImplTest
```

Expected:
- FAIL due to constructor signatures and repository method changes

- [ ] **Step 3: Migrate service constructors and calls**

Update each service to use the new repository contracts only.

Keep the semantics unchanged:
- conversation unread computation still based on sync point max/read
- friend service still implements pending-request and accept/reject/cancel flows
- user service still serves profile lookup and receive options

- [ ] **Step 4: Run the same focused tests again**

Expected:
- PASS, or only unrelated pre-existing failures remain

- [ ] **Step 5: Commit**

```bash
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM add server/business/src/main/java/com/cheeseocean/im/business/service server/business/src/test/java/com/cheeseocean/im/business/service
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM commit -m "refactor: migrate business services to rebuilt repositories"
```

## Task 6: Full Compile Verification

**Files:**
- Modify: any compile-break fixes discovered in the affected modules only

- [ ] **Step 1: Run affected module compiles**

Run:
```bash
cd server
./gradlew :common-api:compileJava :common-core:compileJava :business:compileJava :postbox:compileJava
```

Expected:
- PASS

- [ ] **Step 2: Run targeted repository and service tests**

Run:
```bash
cd server
./gradlew :common-core:test --tests com.cheeseocean.im.common.core.business.mongo.impl.UserConversationRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.UserConversationSyncPointRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.ConversationSequenceRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.UserRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.FriendshipRepositoryImplTest --tests com.cheeseocean.im.common.core.business.mongo.impl.FriendRequestRepositoryImplTest
./gradlew :business:test --tests com.cheeseocean.im.business.service.conversation.ConversationLifecycleServiceTest --tests com.cheeseocean.im.business.service.conversation.ConversationQueryServiceImplTest --tests com.cheeseocean.im.business.service.FriendRelationServiceImplTest
```

Expected:
- PASS, or only clearly unrelated pre-existing failures

- [ ] **Step 3: If unrelated pre-existing failures remain, document them**

Update the final implementation summary with:
- exact failing test class
- exact unrelated reason

- [ ] **Step 4: Final commit**

```bash
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM add server docs/superpowers/specs/2026-04-09-repository-rebuild-design.md docs/superpowers/plans/2026-04-09-repository-rebuild-implementation.md
git -C /Users/xxxcrel/Develop/backend/java/CheeseIM commit -m "refactor: rebuild mongo repositories from openim semantics"
```
