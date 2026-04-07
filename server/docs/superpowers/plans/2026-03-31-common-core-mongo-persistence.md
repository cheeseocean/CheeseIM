# Common-Core Mongo Persistence Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the shared Mongo domain models, repository abstractions, Spring Data Mongo repositories, and default Mongo-backed repository implementations from `business` into `common-core`, with explicit opt-in enablement that works for both single-module startup and unified startup.

**Architecture:** `common-core` becomes the owner of shared IM persistence for foundational business data. It will expose explicit persistence configuration through `@EnableCommonMongoPersistence`, while `business` and `postoffice` opt in without relying on broad component scanning. Business orchestration remains in module-local service layers.

**Tech Stack:** Spring Boot, Spring Data MongoDB, Spring component scanning, Gradle multi-module build, JUnit 5

---

## File Structure

### Shared persistence destination in `common-core`

- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/domain/`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/repository/`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/config/EnableCommonMongoPersistence.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/config/CommonMongoPersistenceConfiguration.java`

### Source packages to drain from `business`

- Move from: `business/src/main/java/com/cheeseocean/im/business/domain/`
- Move from: `business/src/main/java/com/cheeseocean/im/business/repository/`
- Move from: `business/src/main/java/com/cheeseocean/im/business/infra/mongo/document/`
- Move from: `business/src/main/java/com/cheeseocean/im/business/infra/mongo/repository/`
- Move from: `business/src/main/java/com/cheeseocean/im/business/infra/mongo/impl/`

### Module bootstrap files

- Modify: `business/src/main/java/com/cheeseocean/im/business/Business.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/PostOffice.java`
- Modify: any module startup class that currently needs shared Mongo persistence

### Verification targets

- Test/compile: `./gradlew :common-core:compileJava`
- Test/compile: `./gradlew :business:compileJava`
- Test/compile: `./gradlew :postoffice:compileJava`
- Test/compile: `./gradlew :common-core:test --tests '...EnableCommonMongoPersistence...Test'`

### Migration inventory

Current shared persistence candidates to move:

- Domain:
  - `Blacklist`
  - `ConversationOffsetRange`
  - `FriendRequest`
  - `Friendship`
  - `Group`
  - `GroupApplication`
  - `GroupMember`
  - `User`
  - `UserConversationState`
  - `UserSyncCheckpoint`
- Mongo documents:
  - `BlacklistDoc`
  - `ConversationOffsetRangeDoc`
  - `FriendRequestDoc`
  - `FriendshipDoc`
  - `GroupApplicationDoc`
  - `GroupDoc`
  - `GroupMemberDoc`
  - `UserConversationStateDoc`
  - `UserDoc`
- Spring Data Mongo repositories:
  - `BlacklistMongoRepository`
  - `ConversationOffsetRangeMongoRepository`
  - `FriendRequestMongoRepository`
  - `FriendshipMongoRepository`
  - `GroupApplicationMongoRepository`
  - `GroupMemberMongoRepository`
  - `GroupMongoRepository`
  - `UserMongoRepository`
- Default repository implementations:
  - `ConversationOffsetRangeRepositoryImpl`
  - `FriendRepositoryImpl`
  - `GroupApplicationRepositoryImpl`
  - `GroupMemberRepositoryImpl`
  - `GroupRepositoryImpl`
  - `UserConversationStateRepositoryImpl`
  - `UserRepositoryImpl`

### Boundary rule

Do not move:

- business application services
- Dubbo providers
- conversation/group/friend orchestration services
- module-specific facades

## Task 1: Introduce explicit shared persistence configuration

**Files:**
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/config/EnableCommonMongoPersistence.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/config/CommonMongoPersistenceConfiguration.java`
- Test: `common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/config/CommonMongoPersistenceConfigurationTest.java`

- [ ] **Step 1: Write the failing test**

Create a focused Spring context test that imports only the new configuration class and asserts:

- `ConversationOffsetRangeRepositoryImpl` bean exists
- `ConversationOffsetRangeMongoRepository` bean exists
- no `business` package bean definitions are required

Use a minimal `@SpringBootTest` or `ApplicationContextRunner` style test already consistent with the repo.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common-core:test --tests 'com.cheeseocean.im.common.core.business.mongo.config.CommonMongoPersistenceConfigurationTest'`

Expected: FAIL because the configuration and enablement annotation do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create:

- `@EnableCommonMongoPersistence`
- `CommonMongoPersistenceConfiguration`

Implementation requirements:

- use `@EnableMongoRepositories(basePackages = "com.cheeseocean.im.common.core.business.mongo.repository")`
- use narrowly scoped `@ComponentScan(basePackages = "com.cheeseocean.im.common.core.business.mongo.impl")`
- avoid any broad scan over all `common-core`

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common-core:test --tests 'com.cheeseocean.im.common.core.business.mongo.config.CommonMongoPersistenceConfigurationTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/config common-core/src/test/java/com/cheeseocean/im/common/core/business/mongo/config
git commit -m "feat: add explicit common mongo persistence enablement"
```

## Task 2: Move shared domain models and repository abstractions into `common-core`

**Files:**
- Create/Move: `common-core/src/main/java/com/cheeseocean/im/common/core/business/domain/*.java`
- Create/Move: `common-core/src/main/java/com/cheeseocean/im/common/core/business/repository/*.java`
- Modify: all imports in `business/src/main/java/**`
- Modify: all imports in `postoffice/src/main/java/**`
- Test: `./gradlew :common-core:compileJava :business:compileJava :postoffice:compileJava`

- [ ] **Step 1: Write the failing compile check**

Do not write production code yet. First choose one representative abstraction pair:

- `UserConversationState`
- `UserConversationStateRepository`

Add a narrow compile-targeted test or temporary import usage in a test fixture under `common-core` to assert the new package path compiles.

- [ ] **Step 2: Run compile/test to verify it fails**

Run: `./gradlew :common-core:compileJava`

Expected: FAIL once the test or references point to the new package before the files are moved.

- [ ] **Step 3: Move domain classes**

Move every shared domain file from `business/.../domain` to `common-core/.../business/domain`.

Rules:

- keep class contents unchanged except package/import updates
- do not mix behavior refactors into this move

- [ ] **Step 4: Move repository interfaces**

Move every shared repository interface from `business` to `common-core/.../business/repository`.

Rules:

- repository interfaces must not import `business` packages afterward
- update all downstream imports in `business` and `postoffice`

- [ ] **Step 5: Run compile verification**

Run: `./gradlew :common-core:compileJava :business:compileJava :postoffice:compileJava`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/business/domain common-core/src/main/java/com/cheeseocean/im/common/core/business/repository business/src/main/java postoffice/src/main/java
git commit -m "refactor: move shared domain repositories into common-core"
```

## Task 3: Move Mongo document classes and Spring Data repositories

**Files:**
- Create/Move: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document/*.java`
- Create/Move: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/repository/*.java`
- Modify: imports in repository implementations
- Test: `./gradlew :common-core:compileJava :business:compileJava`

- [ ] **Step 1: Write the failing compile check**

Select one document/repository pair:

- `ConversationOffsetRangeDoc`
- `ConversationOffsetRangeMongoRepository`

Add the new package references in a small shared config test or compile-only fixture so the build fails before the move.

- [ ] **Step 2: Run compile to verify it fails**

Run: `./gradlew :common-core:compileJava`

Expected: FAIL because the target package paths do not exist yet.

- [ ] **Step 3: Move document classes**

Move all Mongo document classes into `common-core/.../business/mongo/document`.

Rules:

- preserve collection names
- preserve indexes and annotations
- package/import updates only

- [ ] **Step 4: Move Spring Data repository interfaces**

Move all interfaces from `business/.../infra/mongo/repository` into `common-core/.../business/mongo/repository`.

- [ ] **Step 5: Run compile verification**

Run: `./gradlew :common-core:compileJava :business:compileJava`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/document common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/repository business/src/main/java
git commit -m "refactor: move shared mongo documents and repositories"
```

## Task 4: Move default Mongo-backed repository implementations

**Files:**
- Create/Move: `common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl/*.java`
- Modify: imports for cache helpers and shared repository abstractions
- Modify: any bean annotations needed for shared registration
- Test: `./gradlew :common-core:compileJava :business:compileJava :postoffice:compileJava`

- [ ] **Step 1: Write the failing compile check**

Choose one implementation with representative dependencies:

- `UserConversationStateRepositoryImpl`

Prepare a configuration test under `common-core` that expects this bean under the new package path.

- [ ] **Step 2: Run test/compile to verify it fails**

Run: `./gradlew :common-core:test --tests 'com.cheeseocean.im.common.core.business.mongo.config.CommonMongoPersistenceConfigurationTest'`

Expected: FAIL until the implementation moves.

- [ ] **Step 3: Move implementations**

Move all default Mongo-backed implementations into `common-core/.../business/mongo/impl`.

Rules:

- keep existing cache-aside logic intact
- keep `@Repository` only if still needed with the explicit config scan
- do not move service or workflow code

- [ ] **Step 4: Update imports and constructor wiring**

Fix imports across:

- `business`
- `postoffice`
- tests

Confirm there are no imports left pointing from shared implementations back to `business`.

- [ ] **Step 5: Run compile verification**

Run: `./gradlew :common-core:compileJava :business:compileJava :postoffice:compileJava`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/business/mongo/impl business/src/main/java postoffice/src/main/java
git commit -m "refactor: move shared mongo implementations into common-core"
```

## Task 5: Enable shared persistence explicitly in consuming modules

**Files:**
- Modify: `business/src/main/java/com/cheeseocean/im/business/Business.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/PostOffice.java`
- Modify: any bootstrap/config test fixtures
- Test: `./gradlew :business:compileJava :postoffice:compileJava`

- [ ] **Step 1: Write the failing startup/config test**

Add a focused startup test for one consumer module that asserts:

- context starts with `@EnableCommonMongoPersistence`
- shared repository bean is injectable

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :business:test --tests '...EnableCommonMongoPersistence...Test'`

Expected: FAIL because the module does not yet opt in.

- [ ] **Step 3: Add explicit enablement**

Import `@EnableCommonMongoPersistence` into module startup classes that require direct shared persistence access.

Rules:

- no broad `scanBasePackages` additions as a substitute
- use explicit import/annotation only

- [ ] **Step 4: Remove obsolete local scanning assumptions**

Delete any leftover config or package assumptions that only worked because implementations lived under `business`.

- [ ] **Step 5: Run verification**

Run: `./gradlew :business:compileJava :postoffice:compileJava`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add business/src/main/java/com/cheeseocean/im/business/Business.java postoffice/src/main/java/com/cheeseocean/im/postoffice/PostOffice.java
git commit -m "feat: enable shared mongo persistence in consumer modules"
```

## Task 6: Remove old `business` persistence packages and assert no reverse dependency remains

**Files:**
- Delete: `business/src/main/java/com/cheeseocean/im/business/domain/` moved files
- Delete: `business/src/main/java/com/cheeseocean/im/business/repository/` moved files
- Delete: `business/src/main/java/com/cheeseocean/im/business/infra/mongo/`
- Test: `./gradlew :common-core:compileJava :business:compileJava :postoffice:compileJava`
- Test: `rg -n "com\\.cheeseocean\\.im\\.business\\.(domain|repository|infra\\.mongo)" common-core/src/main/java postoffice/src/main/java business/src/main/java -g '*.java'`

- [ ] **Step 1: Delete moved source files from `business`**

Remove the original copies after confirming imports are already rewritten.

- [ ] **Step 2: Run reverse-dependency scan**

Run:

```bash
rg -n "com\\.cheeseocean\\.im\\.business\\.(domain|repository|infra\\.mongo)" common-core/src/main/java postoffice/src/main/java business/src/main/java -g '*.java'
```

Expected: no remaining references except allowed business service-layer packages.

- [ ] **Step 3: Run compile verification**

Run: `./gradlew :common-core:compileJava :business:compileJava :postoffice:compileJava`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add common-core/src/main/java business/src/main/java postoffice/src/main/java
git commit -m "refactor: remove duplicated business mongo persistence layer"
```

## Task 7: Full regression verification for affected startup modes

**Files:**
- Test only

- [ ] **Step 1: Run shared compile verification**

Run: `./gradlew :common-core:compileJava :business:compileJava :postoffice:compileJava`

Expected: PASS

- [ ] **Step 2: Run focused persistence tests**

Run: `./gradlew :common-core:test --tests 'com.cheeseocean.im.common.core.business.mongo.config.CommonMongoPersistenceConfigurationTest'`

Expected: PASS

- [ ] **Step 3: Run focused module tests**

Run:

```bash
./gradlew :business:test --tests 'com.cheeseocean.im.business.*'
./gradlew :postoffice:test --tests 'com.cheeseocean.im.postoffice.*'
```

If the repository already has unrelated broken tests, document exact blockers and keep the scope focused on compile plus targeted tests.

- [ ] **Step 4: Capture residual risks**

Document:

- any module still not opting in
- any repository implementation that still leaks business-specific dependencies
- any startup mode not fully exercised

- [ ] **Step 5: Final commit if verification fixes were needed**

```bash
git add common-core business postoffice
git commit -m "test: finalize shared mongo persistence migration verification"
```
