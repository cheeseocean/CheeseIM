# Cheese IM Full Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing IM architecture in one branch with the target `postoffice -> postbox -> postman -> push` flow, split `common` into `common-core` and `common-api`, rebuild history around Mongo block storage, and remove legacy contracts and redundant implementations.

**Architecture:** The refactor keeps the four business modules but rewrites their boundaries. `common-core` owns pure primitives, `common-api` owns all RPC/Kafka contracts, `postbox` owns ingress and history persistence/query, `postman` owns orchestration and Redis hot state, `postoffice` owns access and online session truth, and `push` owns online dispatch orchestration plus offline push.

**Tech Stack:** Java 17, Spring Boot 3, Dubbo, Kafka, Redis, MongoDB, Gradle, JUnit 5, Mockito, Spring Kafka Test.

---

### Task 1: Split Shared Modules And Rewire Build Graph

**Files:**
- Modify: `settings.gradle`
- Modify: `build.gradle`
- Modify: `postoffice/build.gradle`
- Modify: `postbox/build.gradle`
- Modify: `postman/build.gradle`
- Modify: `push/build.gradle`
- Create: `common-core/build.gradle`
- Create: `common-api/build.gradle`
- Test: `./gradlew projects`

- [ ] **Step 1: Write the failing structural check**

Record the expected module graph:

```text
common-core
common-api -> common-core
postoffice -> common-core, common-api, config
postbox -> common-core, common-api, config
postman -> common-core, common-api, config
push -> common-core, common-api, config
```

- [ ] **Step 2: Run Gradle project listing to verify it fails the new expectation**

Run: `./gradlew projects`
Expected: output still shows `common` and does not show `common-core` / `common-api`

- [ ] **Step 3: Modify Gradle settings and module build files**

Apply these structural changes:

```groovy
include 'common-core'
include 'common-api'
// remove include 'common'
```

```groovy
dependencies {
    implementation project(':common-core')
    implementation project(':common-api')
}
```

- [ ] **Step 4: Re-run project listing**

Run: `./gradlew projects`
Expected: `common-core` and `common-api` are present, `common` is gone, dependency resolution succeeds

- [ ] **Step 5: Commit**

```bash
git add settings.gradle build.gradle common-core/build.gradle common-api/build.gradle postoffice/build.gradle postbox/build.gradle postman/build.gradle push/build.gradle
git commit -m "refactor: split shared modules into core and api"
```

### Task 2: Rebuild Shared Primitives And Cross-Service Contracts

**Files:**
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/constants/TopicNames.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/constants/RedisKeys.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/constants/ErrorCodes.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/SessionType.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/MessageStatus.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/util/ConversationIdUtil.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/util/BlockIndexUtil.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/rpc/MessageSendRpc.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/rpc/MessageQueryRpc.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/rpc/OnlineDispatchRpc.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/rpc/OnlineRouteQueryRpc.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/model/BaseResponse.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/model/PageQuery.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/model/PageResult.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/message/MessageOptions.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/message/SendMessageReq.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/message/SendMessageResp.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/message/SequencedMessage.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/dispatch/DispatchPayload.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/dispatch/DispatchMessageReq.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/dispatch/DispatchMessageResp.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/dispatch/DispatchResult.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/route/OnlineRouteSnapshot.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/route/OnlineRouteQuery.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/query/PullMessagesRequest.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/query/PullMessagesResponse.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/query/ConversationStateRequest.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/query/ConversationStateResponse.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/event/IngressEvent.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/event/HistoryEvent.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/event/DeliveryEvent.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/event/OfflinePushEvent.java`
- Modify: `common-core/build.gradle`
- Modify: `common-api/build.gradle`
- Test: `common-core/src/test/java/com/cheeseocean/im/common/core/util/ConversationIdUtilTest.java`
- Test: `common-core/src/test/java/com/cheeseocean/im/common/core/util/BlockIndexUtilTest.java`

- [ ] **Step 1: Write failing shared primitive tests**

```java
@Test
void singleConversationIdIsOrderIndependent() {
    assertEquals("c1:u100:u200", ConversationIdUtil.single("u200", "u100"));
}

@Test
void blockIndexMapsSeqToBlockAndSlot() {
    assertEquals(1L, BlockIndexUtil.blockNo(101));
    assertEquals(0, BlockIndexUtil.index(101));
}
```

- [ ] **Step 2: Run the primitive tests and watch them fail**

Run: `./gradlew :common-core:test --tests '*ConversationIdUtilTest' --tests '*BlockIndexUtilTest'`
Expected: FAIL because module/classes do not exist yet

- [ ] **Step 3: Implement the new shared module contents**

Use the spec model directly. For example:

```java
public record MessageOptions(
        boolean needHistory,
        boolean needConversation,
        boolean needUnreadCount,
        boolean needOnlinePush,
        boolean needOfflinePush,
        boolean senderSync,
        boolean notification,
        boolean needLastMessage) {
}
```

- [ ] **Step 4: Remove legacy shared contracts**

Delete the old `common/src/main/java/com/cheeseocean/im/common/{api,dto,entity,model,utils,...}` tree once all usages are migrated to `common-core` and `common-api`.

- [ ] **Step 5: Run shared-module tests**

Run: `./gradlew :common-core:test :common-api:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common-core common-api
git commit -m "refactor: rebuild shared IM contracts"
```

### Task 3: Rebuild Postbox Around Ingress RPC And Mongo Block History

**Files:**
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageSendRpcImpl.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/IngressEventPublisher.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageQueryRpcImpl.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/history/BlockHistoryPersistenceService.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/history/HistoryQueryService.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/history/MessageBlockDoc.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/history/MessageSlot.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/history/MessageIdMappingDoc.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/history/MessageBlockRepository.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/history/MessageIdMappingRepository.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/listener/HistoryEventListener.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageStoreServiceImpl.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/repository/InboxDocumentRepository.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/InboxDocument.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/MessageDocument.java`
- Delete: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/ConversationReadCursorDocument.java`
- Test: `postbox/src/test/java/com/cheeseocean/im/postbox/history/BlockHistoryPersistenceServiceTest.java`
- Test: `postbox/src/test/java/com/cheeseocean/im/postbox/service/MessageSendRpcImplTest.java`

- [ ] **Step 1: Write failing ingress and history persistence tests**

```java
@Test
void sendMessagePublishesIngressEventWithConversationIdAndServerMsgId() { }

@Test
void historyEventPersistsMessagesIntoFixedBlockSlotsAndMapping() { }
```

- [ ] **Step 2: Run postbox tests to verify red state**

Run: `./gradlew :postbox:test --tests '*MessageSendRpcImplTest' --tests '*BlockHistoryPersistenceServiceTest'`
Expected: FAIL because the new RPC/provider and block history model do not exist

- [ ] **Step 3: Implement ingress RPC and idempotent event publication**

Core behavior:

```java
String conversationId = conversationIdResolver.resolve(req);
String serverMsgId = serverMsgIdGenerator.nextId();
MessageOptions options = messageOptionsResolver.fillDefaults(req.options());
IngressEvent event = ingressEventFactory.create(req, conversationId, serverMsgId, options);
ingressEventPublisher.publish(event, conversationId);
```

- [ ] **Step 4: Implement Mongo block history persistence and query flow**

Core behavior:

```java
long blockNo = BlockIndexUtil.blockNo(seq);
int index = BlockIndexUtil.index(seq);
update.set("messages." + index, slot);
mongoTemplate.upsert(query, update, MessageBlockDoc.class);
mappingRepository.save(mapping);
```

- [ ] **Step 5: Implement Redis-first query flow against the canonical key model**

Expose query RPCs that read Redis hot cache and conversation state first, then fill gaps from Mongo block history. Use the canonical `RedisKeys` contract from `common-core` so `postbox` and `postman` are pinned to the same hot-state shape during implementation.

- [ ] **Step 6: Run postbox test suite**

Run: `./gradlew :postbox:test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add postbox
git commit -m "refactor: rebuild postbox ingress and history"
```

### Task 4: Rebuild Postman As The Only Orchestrator

**Files:**
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/IngressEventListener.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/MessagePolicyEngine.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/PostmanIdempotencyService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/ConversationSerialExecutor.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/ConversationSequencer.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/RedisConversationStateService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/HistoryEventPublisher.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/DeliveryEventPublisher.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/TargetUserResolver.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImpl.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/service/IngressEventPublisher.java`
- Test: `postman/src/test/java/com/cheeseocean/im/postman/service/MessagePolicyEngineTest.java`
- Test: `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`

- [ ] **Step 1: Write failing orchestration tests**

```java
@Test
void policyDisablesHistoryFanoutWhenNeedHistoryIsFalse() { }

@Test
void ingressListenerAssignsSeqUpdatesRedisAndPublishesHistoryAndDelivery() { }

@Test
void ingressListenerSkipsOnlineFanoutWhenNeedOnlinePushIsFalse() { }

@Test
void ingressListenerSkipsUnreadMutationWhenNeedUnreadCountIsFalse() { }

@Test
void ingressListenerIncludesSenderWhenSenderSyncIsTrue() { }

@Test
void ingressListenerSupportsNotificationConversationPolicy() { }
```

- [ ] **Step 2: Run postman tests to verify red state**

Run: `./gradlew :postman:test --tests '*MessagePolicyEngineTest' --tests '*IngressEventListenerTest'`
Expected: FAIL because orchestration classes still reflect the old delivery-entry design

- [ ] **Step 3: Implement the policy engine and sequencing path**

Core orchestration:

```java
if (postmanIdempotencyService.isDuplicate(event)) {
    return;
}
conversationSerialExecutor.execute(event.conversationId(), () -> {
    PolicyDecision decision = policyEngine.evaluate(event);
    SequencedMessage message = sequencer.sequence(event, decision);
    TargetUsers targets = targetUserResolver.resolve(message, decision);
    redisConversationStateService.apply(message, decision, targets);
    if (decision.needHistory()) historyEventPublisher.publish(message, targets, decision);
    if (decision.needDelivery()) deliveryEventPublisher.publish(message, targets, decision);
});
```

- [ ] **Step 4: Remove entrypoint and storage responsibilities from postman**

Delete old RPC-entry classes and any direct persistence/push coupling that conflicts with the new boundary.

- [ ] **Step 5: Run postman test suite**

Run: `./gradlew :postman:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add postman
git commit -m "refactor: rebuild postman orchestration flow"
```

### Task 5: Rebuild Postoffice As Access Layer And Online Dispatch Provider

**Files:**
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/api/DispatchMessageRpcImpl.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/api/OnlineRouteQueryRpcImpl.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/ConnectionDispatchService.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/RedisOnlineRouteService.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionBindService.java`
- Delete: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/GatewayPushServiceImpl.java`
- Delete: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/MessageProtoMapper.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/service/ConnectionDispatchServiceTest.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandlerTest.java`

- [ ] **Step 1: Write failing access and dispatch tests**

```java
@Test
void chatHandlerConvertsClientPayloadIntoSendMessageReq() { }

@Test
void dispatchRpcReturnsPerConnectionResultForRequestedConnections() { }

@Test
void onlineRouteQueryRpcReturnsStableRouteSnapshotsForUserIds() { }
```

- [ ] **Step 2: Run postoffice tests to verify red state**

Run: `./gradlew :postoffice:test --tests '*ChatMessageHandlerTest' --tests '*ConnectionDispatchServiceTest'`
Expected: FAIL because current handlers still target old delivery contracts

- [ ] **Step 3: Implement access-to-postbox send flow**

Core behavior:

```java
SendMessageReq req = sendMessageReqMapper.fromWsCommand(command, principal);
SendMessageResp resp = messageSender.sendMessage(req);
```

- [ ] **Step 4: Implement online dispatch RPC**

Core behavior:

```java
for (String connectionId : req.connectionIds()) {
    boolean success = connectionDispatchService.dispatch(connectionId, req.payload());
    results.add(new DispatchResult(connectionId, success, code, message));
}
```

- [ ] **Step 5: Implement route-query RPC for push**

Expose route snapshots through `OnlineRouteQueryRpc` so `push` can query online connections without depending on `postoffice` implementation classes.

- [ ] **Step 6: Run postoffice test suite**

Run: `./gradlew :postoffice:test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add postoffice
git commit -m "refactor: rebuild postoffice access and dispatch"
```

### Task 6: Rebuild Push Around Delivery And Offline Push Events

**Files:**
- Create: `push/src/main/java/com/cheeseocean/im/push/listener/DeliveryEventListener.java`
- Create: `push/src/main/java/com/cheeseocean/im/push/service/OnlineDispatchOrchestrator.java`
- Create: `push/src/main/java/com/cheeseocean/im/push/service/OfflinePushEventPublisher.java`
- Create: `push/src/main/java/com/cheeseocean/im/push/listener/OfflinePushEventListener.java`
- Delete: `push/src/main/java/com/cheeseocean/im/push/listener/OfflinePushTaskListener.java`
- Delete: old push services that depend on `postoffice` implementation classes directly
- Modify: `push/build.gradle`
- Test: `push/src/test/java/com/cheeseocean/im/push/service/OnlineDispatchOrchestratorTest.java`
- Test: `push/src/test/java/com/cheeseocean/im/push/listener/DeliveryEventListenerTest.java`

- [ ] **Step 1: Write failing delivery/orchestrator tests**

```java
@Test
void deliveryListenerCallsDispatchRpcAndBuildsOfflinePushForOfflineUser() { }

@Test
void orchestratorSkipsOfflinePushWhenPolicyDisablesIt() { }

@Test
void orchestratorUsesOnlineRouteQueryRpcRatherThanPostofficeInternals() { }

@Test
void orchestratorSkipsOnlineDispatchWhenNeedOnlinePushIsFalse() { }
```

- [ ] **Step 2: Run push tests to verify red state**

Run: `./gradlew :push:test --tests '*OnlineDispatchOrchestratorTest' --tests '*DeliveryEventListenerTest'`
Expected: FAIL because push still consumes old task contracts and reaches into postoffice internals

- [ ] **Step 3: Implement delivery consumer and dispatch RPC orchestration**

Core behavior:

```java
if (!event.message().options().needOnlinePush()) {
    return;
}
Map<String, List<String>> connectionsByUser = onlineRouteQueryService.groupConnections(event.targetUserIds());
DispatchMessageResp resp = onlineDispatcher.dispatchMessage(req);
if (shouldOfflinePush(event, userId, resp)) offlinePushEventPublisher.publish(...);
```

- [ ] **Step 4: Implement offline push event consumption**

Translate `OfflinePushEvent` into provider-specific push requests without re-checking architecture decisions already made upstream.

- [ ] **Step 5: Run push test suite**

Run: `./gradlew :push:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add push
git commit -m "refactor: rebuild push delivery and offline flow"
```

### Task 7: Remove Legacy Code Paths, Align Config, And Restore Integration Coverage

**Files:**
- Modify: `config/src/main/resources/module-postoffice.yml`
- Modify: `config/src/main/resources/module-postbox.yml`
- Modify: `config/src/main/resources/module-postman.yml`
- Modify: `config/src/main/resources/module-push.yml`
- Modify: `config/src/main/resources/common.yml`
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java`
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/service/GatewayPushServiceImplTest.java` or replace with dispatch tests
- Delete: all remaining references to old `common` package imports
- Delete: obsolete docs under `docs/architecture` that contradict the new design, or rewrite them to point to the new spec
- Test: `./gradlew test`

- [ ] **Step 1: Write or update failing end-to-end smoke coverage**

Target behaviors:

```text
single chat send -> ingress -> seq -> history -> delivery -> dispatch
history lookup reads Mongo truth and Redis hot-state enrichment where available
ingress retry returns existing mapping and does not duplicate history
offline push is generated only when policy allows it
senderSync affects target expansion
needLastMessage and notification policies mutate projections correctly
```

- [ ] **Step 2: Run full test suite and capture failures**

Run: `./gradlew test`
Expected: FAIL until old imports, old configs, and old tests are aligned

- [ ] **Step 3: Remove remaining legacy and redundant code**

Delete:

- leftover `common` module sources
- old sync-delivery abstractions
- old inbox history code
- stale tests that assert removed behavior

- [ ] **Step 4: Align runtime configuration and smoke tests**

Ensure Kafka topics, Dubbo references/providers, Redis keys, Mongo collections, and test fixtures match the new contracts.

- [ ] **Step 5: Run full verification**

Run: `./gradlew clean test`
Expected: PASS

Run: `./gradlew :postoffice:test :postbox:test :postman:test :push:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add config docs postoffice postbox postman push common-core common-api settings.gradle build.gradle
git commit -m "refactor: replace IM architecture with final design"
```

## Execution Notes

- Keep TDD discipline per task: test first, verify red, implement minimal code, verify green, then refactor.
- Prefer deleting incompatible legacy code rather than adapting it.
- Do not preserve compatibility shims, alias topics, or duplicate DTOs.
- When in doubt, the source of truth is the spec at `docs/superpowers/specs/2026-03-22-im-full-refactor-design.md`.
