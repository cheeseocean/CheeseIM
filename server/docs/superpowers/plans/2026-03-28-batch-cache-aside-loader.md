# Batch Cache-Aside Loader Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the repeated Redis `multiGet -> miss -> batch DB query -> cache fill -> ordered return` pattern into a reusable utility and migrate the first wave of social repositories to it.

**Architecture:** Add a small generic loader in `common-core` that is storage-agnostic except for caller-provided getter/writer functions. Keep repository ownership of key construction, DB queries, and type mapping while removing repeated batch cache-aside boilerplate.

**Tech Stack:** Java 17, Spring Data Redis, JUnit 5, Mockito, Gradle

---

### Task 1: Add utility tests

**Files:**
- Create: `common-core/src/test/java/com/cheeseocean/im/common/core/cache/CacheAsideBatchLoaderTest.java`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation**
- [ ] **Step 4: Run test to verify it passes**

### Task 2: Add utility implementation

**Files:**
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/cache/CacheAsideBatchLoader.java`
- Test: `common-core/src/test/java/com/cheeseocean/im/common/core/cache/CacheAsideBatchLoaderTest.java`

- [ ] **Step 1: Implement de-duplication, miss detection, cache fill, and ordered return**
- [ ] **Step 2: Re-run utility tests**

### Task 3: Migrate repository usages

**Files:**
- Modify: `business/src/main/java/com/cheeseocean/im/social/infra/mongo/impl/UserRepositoryImpl.java`
- Modify: `business/src/main/java/com/cheeseocean/im/social/infra/mongo/impl/GroupRepositoryImpl.java`
- Modify: `business/src/main/java/com/cheeseocean/im/social/infra/mongo/impl/UserConversationStateRepositoryImpl.java`
- Modify: `business/src/main/java/com/cheeseocean/im/social/infra/mongo/impl/GroupMemberRepositoryImpl.java`

- [ ] **Step 1: Refactor one repository to validate API fit**
- [ ] **Step 2: Apply same pattern to the remaining repositories**
- [ ] **Step 3: Keep behavior unchanged and avoid unrelated refactors**

### Task 4: Verify

**Files:**
- Test: `common-core/src/test/java/com/cheeseocean/im/common/core/cache/CacheAsideBatchLoaderTest.java`

- [ ] **Step 1: Run focused `common-core` tests**
- [ ] **Step 2: Run targeted `business` tests if present or compile relevant modules**
- [ ] **Step 3: Summarize residual risks**
