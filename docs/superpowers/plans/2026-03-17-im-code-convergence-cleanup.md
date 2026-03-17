# IM Code Convergence Cleanup Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove obsolete IM code paths and duplicate contracts so the repository matches the validated delivery architecture without regressing the passing test suite.

**Architecture:** Converge the codebase in small verified batches. Each batch deletes one obsolete slice, rewires any remaining adapters in the same pass, and updates docs/tests so the surviving code describes the current design only.

**Tech Stack:** Java 17, Gradle multi-module build, Spring Boot, Dubbo, Kafka, Redis, MongoDB, JUnit 5

---

### Task 1: Document and lock the cleanup boundary

**Files:**
- Create: `docs/superpowers/specs/2026-03-17-im-code-convergence-cleanup-design.md`
- Create: `docs/superpowers/plans/2026-03-17-im-code-convergence-cleanup.md`

- [ ] **Step 1: Save the approved cleanup design**

Write the cleanup policy, target batches, and verification strategy into the spec file.

- [ ] **Step 2: Save the execution plan**

Write this plan file with exact cleanup batches and test commands.

- [ ] **Step 3: Sanity-check the plan boundary**

Run: `rg -n "MessageTransferService|MessageRouterService|OnlineUserService|MessageStatisticsService|MessageService|MessageMongo|ConversationUtils|common\\.constant" common postbox postman postoffice push -S`

Expected: references cluster around the targeted legacy slices, confirming the plan is scoped.

### Task 2: Remove duplicate common contracts

**Files:**
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/MessageProto.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/MessageProtoMapper.java`
- Modify: `push/src/main/java/com/cheeseocean/im/push/service/impl/MessagePushServiceImpl.java`
- Delete: `common/src/main/java/com/cheeseocean/im/common/constant/MessageConstants.java`
- Delete: `common/src/main/java/com/cheeseocean/im/common/constant/OptionConstants.java`
- Delete: `common/src/main/java/com/cheeseocean/im/common/service/ConversationService.java`
- Test: `common/src/test/java/com/cheeseocean/im/common/dto/DeliveryContractTest.java`

- [ ] **Step 1: Write or extend the failing tests**

Add a test proving the surviving delivery contracts still serialize and validate without relying on the deleted duplicate constants/service namespace.

- [ ] **Step 2: Run the focused test to verify the current gap**

Run: `./gradlew :common:test --tests "com.cheeseocean.im.common.dto.DeliveryContractTest"`

Expected: fail if additional rewiring is still needed.

- [ ] **Step 3: Remove duplicate contract classes and rewire consumers**

Delete the duplicate constants/service files and update remaining consumers to use the surviving contract path or inline behavior.

- [ ] **Step 4: Re-run the focused test**

Run: `./gradlew :common:test --tests "com.cheeseocean.im.common.dto.DeliveryContractTest"`

Expected: PASS

- [ ] **Step 5: Run shared-module regression**

Run: `./gradlew :common:test :postoffice:test :push:test`

Expected: PASS

### Task 3: Delete the legacy postbox delivery pipeline

**Files:**
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/controller/PostmanController.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/listener/MessageTransferListener.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageTransferService.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageRouterService.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageStatisticsService.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/OnlineUserService.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/impl/MessageTransferServiceImpl.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/impl/MessageRouterServiceImpl.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/impl/MessageStatisticsServiceImpl.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/impl/OnlineUserServiceImpl.java`
- Modify: `postbox/README.md`
- Test: `postbox/src/test/java/com/cheeseocean/im/postbox/service/MessageStoreServiceImplTest.java`

- [ ] **Step 1: Add one test assertion guarding the surviving storage boundary**

Extend `MessageStoreServiceImplTest` if needed so the module's intended responsibility is explicit and still covered after deletions.

- [ ] **Step 2: Run the storage test first**

Run: `./gradlew :postbox:test --tests "com.cheeseocean.im.postbox.service.MessageStoreServiceImplTest"`

Expected: PASS before deletion baseline is confirmed.

- [ ] **Step 3: Delete the legacy routing/transfer/statistics pipeline**

Remove the obsolete interfaces, implementations, listener, and controller. Rewrite the README to describe storage-only responsibility.

- [ ] **Step 4: Re-run postbox tests**

Run: `./gradlew :postbox:test`

Expected: PASS

### Task 4: Delete the legacy postman message API slice

**Files:**
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/api/MessageService.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/entity/ConversationSeq.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/entity/MessageMongo.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageStorageService.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/service/impl/MessageServiceImpl.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/utils/ConversationUtils.java`
- Modify: `postman/README.md`
- Test: `postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImplTest.java`
- Test: `postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryAckFlowTest.java`

- [ ] **Step 1: Run delivery-core tests as the baseline**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postman.service.MessageDeliveryServiceImplTest" --tests "com.cheeseocean.im.postman.service.MessageDeliveryAckFlowTest"`

Expected: PASS

- [ ] **Step 2: Delete the competing legacy API and persistence shell**

Remove the old API/service/entity/util classes that are outside the current design.

- [ ] **Step 3: Rewrite the module README**

Describe only the delivery core, compensation, idempotency, and fanout planner responsibilities.

- [ ] **Step 4: Re-run the full postman test suite**

Run: `./gradlew :postman:test`

Expected: PASS

### Task 5: Prune outdated push and transport leftovers

**Files:**
- Modify: `push/src/main/java/com/cheeseocean/im/push/listener/PushMessageListener.java`
- Modify: `push/src/main/java/com/cheeseocean/im/push/listener/OfflinePushListener.java`
- Modify: `push/docs/PUSH_ARCHITECTURE.md`
- Modify: `postoffice/README.md`
- Modify: `postbox/README.md`
- Test: `push/src/test/java/com/cheeseocean/im/push/service/MessagePushServiceImplTest.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java`

- [ ] **Step 1: Run push and smoke baselines**

Run: `./gradlew :push:test --tests "com.cheeseocean.im.push.service.MessagePushServiceImplTest" :postoffice:test --tests "com.cheeseocean.im.postoffice.ImFlowSmokeTest"`

Expected: PASS

- [ ] **Step 2: Remove or rewrite old-flow listeners and docs**

If the legacy Kafka push listeners are not part of the current validated path, delete or narrow them. Rewrite docs so they no longer describe the removed flow.

- [ ] **Step 3: Re-run push and smoke tests**

Run: `./gradlew :push:test :postoffice:test`

Expected: PASS

### Task 6: Full regression and cleanup summary

**Files:**
- Modify: `docs/superpowers/runbooks/im-local-smoke-test.md`

- [ ] **Step 1: Run full repository regression**

Run: `./gradlew test`

Expected: PASS

- [ ] **Step 2: Update the smoke runbook if commands or assumptions changed**

Adjust the runbook to reflect the cleaned architecture only.

- [ ] **Step 3: Summarize deleted slices and retained compatibility**

Prepare a concise summary of what was deleted, what remains intentionally, and what residual cleanup is still optional.
