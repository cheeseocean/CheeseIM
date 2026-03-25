# IM Message Rebuild Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the CheeseIM message pipeline from synchronous `postoffice -> postman -> postbox/push` calls into an event-driven architecture with stable message IDs, conversation seq, async persistence, async delivery, and receipt convergence.

**Architecture:** Introduce the new flow in layers. First reshape shared contracts in `common` so `postoffice`, `postman`, and `postbox` can represent `ACCEPTED`, `seq`, ingress events, and receipt events without pretending the current synchronous DTOs are sufficient. Then wire Kafka ingress/history/delivery topics, move persistence into `postbox`, move online delivery orchestration into `postman -> postoffice`, and finally land receipt, retry, and DLQ behavior with migration switches and observability.

**Tech Stack:** Java 17, Spring Boot, Dubbo, Kafka, Redis, MongoDB, Gradle, JUnit 5, Mockito

---

## File Structure

### Shared contracts and constants

- Modify: `common/src/main/java/com/cheeseocean/im/common/api/MessageDeliveryService.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/api/MessageStoreService.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/api/group/GroupMembershipQueryDubboService.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/constants/KafkaTopics.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/constants/RedisKeys.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/AcceptedMessage.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/IngressEvent.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/HistoryTask.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryTaskCommand.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/OfflinePushTask.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/ReceiptEvent.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/ConversationReadCursor.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryCommand.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryResult.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryAck.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/MessageProto.java`

### postoffice access layer

- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/MessageProtoMapper.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/GatewayPushServiceImpl.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionManager.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WSMessage.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ReceiptMessageHandler.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/GatewayReceiptPublisher.java`
- Create: `postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/ReceiptMessageHandlerTest.java`
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/service/GatewayPushServiceImplTest.java`
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java`

### postman orchestration

- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImpl.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageIdempotencyService.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/GroupFanoutPlanner.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/DeliveryCompensationService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/ConversationSeqService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/ConsumerDedupService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/IngressEventPublisher.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/IngressEventListener.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/DeliveryTaskListener.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/ReceiptEventListener.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/OfflinePushRetryListener.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/GroupMembershipFacade.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/config/MessageFlowProperties.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/metrics/MessageFlowMetrics.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImplTest.java`
- Create: `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`
- Create: `postman/src/test/java/com/cheeseocean/im/postman/listener/DeliveryTaskListenerTest.java`
- Create: `postman/src/test/java/com/cheeseocean/im/postman/listener/ReceiptEventListenerTest.java`

### postbox persistence

- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/MessageDocument.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/InboxDocument.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/ConversationReadCursorDocument.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/repository/MessageDocumentRepository.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/repository/InboxDocumentRepository.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/repository/ConversationReadCursorRepository.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageStoreServiceImpl.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryQueryService.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationQueryService.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/GroupMemberService.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/impl/GroupMemberServiceImpl.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/GroupMembershipQueryDubboServiceImpl.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryTaskPersistenceService.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/listener/HistoryTaskListener.java`
- Create: `postbox/src/test/java/com/cheeseocean/im/postbox/listener/HistoryTaskListenerTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/MessageStoreServiceImplTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java`

### push offline execution

- Modify: `push/src/main/java/com/cheeseocean/im/push/service/impl/MessagePushServiceImpl.java`
- Create: `push/src/main/java/com/cheeseocean/im/push/listener/OfflinePushTaskListener.java`
- Create: `push/src/test/java/com/cheeseocean/im/push/listener/OfflinePushTaskListenerTest.java`
- Modify: `push/src/test/java/com/cheeseocean/im/push/service/MessagePushServiceImplTest.java`

### Config, metrics, docs

- Modify: `postman/src/main/java/com/cheeseocean/im/postman/config/KafkaConfig.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/config/KafkaConfig.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/config/RedisConfig.java`
- Create: `docs/architecture/im-message-rebuild-migration-checklist.md`

## Current-State Constraints

- Current `postoffice` sends via [`ChatMessageHandler`](../../../../postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java) into `MessageDeliveryService.deliver(DeliveryCommand)`.
- Current `DeliveryCommand` is immutable and built with `builder()`.
- Current `DeliveryResult` does not carry `seq` or accepted-state semantics.
- Current `postman` still synchronously calls `MessageStoreService`, `GatewayPushService`, and `MessagePushService`.
- Current `postbox` owns Redis-backed `GroupMemberService`; `postman` cannot depend on `:postbox` directly, so membership lookup must cross a shared `common` API and Dubbo seam.
- Current ACK model is per-message `DeliveryAck`; moving to read-cursor semantics is a real contract and persistence migration, not a rename.
- Current gateway send response only returns `serverMsgID`, `clientMsgID`, and `sendTime`; exposing accepted `seq` requires a wire-contract update in `WSMessage`.
- Rollout controls must exist before any async behavior becomes active.

## Task 1: Reshape Shared Delivery Contracts

**Files:**
- Modify: `common/src/main/java/com/cheeseocean/im/common/api/MessageDeliveryService.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/api/MessageStoreService.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/api/group/GroupMembershipQueryDubboService.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/constants/KafkaTopics.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/constants/RedisKeys.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/AcceptedMessage.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/IngressEvent.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/HistoryTask.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryTaskCommand.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/OfflinePushTask.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/ReceiptEvent.java`
- Create: `common/src/main/java/com/cheeseocean/im/common/dto/ConversationReadCursor.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryCommand.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryResult.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryAck.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/MessageProto.java`
- Test: `postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImplTest.java`

- [ ] **Step 1: Write the failing contract test for accepted-message semantics**

```java
@Test
void deliverShouldReturnAcceptedResultWithServerMsgIdAndSeq() {
    DeliveryResult result = service.deliver(command);
    assertTrue(result.isSuccess());
    assertEquals("ACCEPTED", result.getStatus());
    assertNotNull(result.getServerMsgId());
    assertEquals(1001L, result.getConversationSeq());
}
```

- [ ] **Step 2: Run the test to verify the current shared contract is insufficient**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.service.MessageDeliveryServiceImplTest"`
Expected: FAIL because `DeliveryResult` has no accepted `seq` field and `deliver()` still models synchronous delivery outcomes.

- [ ] **Step 3: Extend shared DTOs and topic constants with the minimum fields needed for event flow**

```java
public class DeliveryResult implements Serializable {
    private boolean success;
    private String status;
    private boolean receiverOnline;
    private String serverMsgId;
    private Long storedMessageId;
    private Long conversationSeq;
    private DeliveryState state;

    public static DeliveryResult accepted(String serverMsgId, long conversationSeq) {
        DeliveryResult result = new DeliveryResult();
        result.success = true;
        result.status = "ACCEPTED";
        result.serverMsgId = serverMsgId;
        result.conversationSeq = conversationSeq;
        result.state = DeliveryState.PERSISTED;
        return result;
    }
}
```

- [ ] **Step 4: Add event DTOs and keep compatibility fields for staged migration**

```java
public class IngressEvent implements Serializable {
    private String eventId;
    private String traceId;
    private String messageId;
    private String clientMsgId;
    private String conversationId;
    private Long conversationSeq;
    private String senderId;
    private String receiverId;
    private Integer sessionType;
    private List<String> targetUserIds;
}
```

- [ ] **Step 5: Run targeted module tests**

Run: `./gradlew :common:compileJava :postman:test --tests "com.cheeseocean.im.postmaster.service.MessageDeliveryServiceImplTest"`
Expected: PASS for compile and updated test expectations.

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/cheeseocean/im/common postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImplTest.java
git commit -m "feat: add shared contracts for accepted message flow"
```

## Task 2: Make `postman` an Accept-Only Ingress Publisher

**Files:**
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImpl.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageIdempotencyService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/ConversationSeqService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/ConsumerDedupService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/IngressEventPublisher.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/config/MessageFlowProperties.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/metrics/MessageFlowMetrics.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/config/KafkaConfig.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WSMessage.java`
- Create: `postman/src/test/java/com/cheeseocean/im/postman/service/ConversationSeqServiceTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImplTest.java`
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java`

- [ ] **Step 1: Write failing tests for idempotent accept and seq allocation**

```java
@Test
void deliverShouldAllocateConversationSeqAndPublishIngressEvent() {
    when(conversationSeqService.nextSeq("single:userA:userB")).thenReturn(1001L);
    DeliveryResult result = service.deliver(command);
    assertEquals(1001L, result.getConversationSeq());
    verify(ingressEventPublisher).publish(any(IngressEvent.class));
}
```

- [ ] **Step 2: Run the postman tests to capture current synchronous coupling**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.service.MessageDeliveryServiceImplTest"`
Expected: FAIL because `deliverFresh()` still persists, pushes online, and schedules offline handling synchronously.

- [ ] **Step 3: Introduce `ConversationSeqService` backed by Redis**

```java
public long nextSeq(String conversationId) {
    Long seq = redisTemplate.opsForValue().increment(RedisKeys.conversationSeq(conversationId));
    if (seq == null) {
        throw new IllegalStateException("Failed to allocate conversation seq");
    }
    return seq;
}
```

- [ ] **Step 4: Refactor `MessageDeliveryServiceImpl.deliver()` to stop after idempotency, auth, seq, and Kafka publish**

```java
private DeliveryResult deliverFresh(DeliveryCommand command) {
    String messageId = messageIdGenerator.nextId();
    long seq = conversationSeqService.nextSeq(command.getConversationId());
    IngressEvent event = IngressEvent.from(command, messageId, seq, TraceId.current());
    ingressEventPublisher.publish(event);
    DeliveryResult accepted = DeliveryResult.accepted(messageId, seq);
    idempotencyService.remember(command.getSenderId(), command.getConversationId(), command.getClientMsgId(), accepted);
    return accepted;
}
```

- [ ] **Step 5: Add feature flags so old synchronous fallback can be toggled off gradually**

```java
@ConfigurationProperties(prefix = "cheeseim.message-flow")
public class MessageFlowProperties {
    private boolean asyncIngressEnabled;
    private boolean asyncHistoryEnabled;
    private boolean asyncDeliveryEnabled;
    private boolean asyncReceiptEnabled;
}

if (!messageFlowProperties.isAsyncIngressEnabled()) {
    return legacyDeliverFresh(command);
}
```

- [ ] **Step 6: Extend gateway send responses to include accepted `seq` while keeping old fields**

```java
WSMessage.sendMsgResp(operationId,
        deliveryResult.getServerMsgId(),
        msgData.getClientMsgID(),
        System.currentTimeMillis(),
        deliveryResult.getConversationSeq());
```

- [ ] **Step 7: Run focused tests and compile**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.service.*" :postman:compileJava`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add postman/src/main/java/com/cheeseocean/im/postman postman/src/test/java/com/cheeseocean/im/postman/service
git commit -m "feat: publish accepted message ingress events from postman"
```

## Task 3: Keep Group Membership Behind a Stable Facade and Add Ingress Consumers

**Files:**
- Create: `common/src/main/java/com/cheeseocean/im/common/api/group/GroupMembershipQueryDubboService.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/service/GroupMembershipFacade.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/IngressEventListener.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/GroupFanoutPlanner.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/GroupMemberService.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/impl/GroupMemberServiceImpl.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/GroupMembershipQueryDubboServiceImpl.java`
- Create: `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`

- [ ] **Step 1: Write the failing listener tests for single-chat and group fanout**

```java
@Test
void ingressListenerShouldPublishHistoryTaskForSingleChat() {
    listener.onMessage(singleIngressEvent);
    verify(kafkaTemplate).send(eq(KafkaTopics.Message.HISTORY), anyString(), any(HistoryTask.class));
}

@Test
void ingressListenerShouldSplitGroupMembersIntoBatches() {
    when(groupMembershipFacade.loadTargets("group:crew")).thenReturn(List.of("u1", "u2", "u3"));
    listener.onMessage(groupIngressEvent);
    verify(kafkaTemplate, times(1)).send(eq(KafkaTopics.Message.HISTORY), anyString(), any());
}
```

- [ ] **Step 2: Run the listener tests to verify the new ingress consumer does not exist yet**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.listener.IngressEventListenerTest"`
Expected: FAIL because the listener and membership facade are missing.

- [ ] **Step 3: Add a shared Dubbo query contract for group membership and keep `postman` behind a local facade**

```java
public interface GroupMembershipQueryDubboService {
    List<String> queryMembers(String conversationId);
}
```

- [ ] **Step 4: Implement `IngressEventListener` to route single chats to history and groups to batched fanout tasks**

```java
if (event.isGroupDelivery()) {
    List<List<String>> batches = groupFanoutPlanner.partition(groupMembershipFacade.loadTargets(event.getConversationId()), batchSize);
    for (List<String> batch : batches) {
        ingressEventPublisher.publishHistoryTask(HistoryTask.groupBatch(event, batch));
    }
} else {
    ingressEventPublisher.publishHistoryTask(HistoryTask.single(event));
}
```

- [ ] **Step 5: Run the listener tests and postman test suite**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.listener.*" :postman:test --tests "com.cheeseocean.im.postmaster.service.*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/com/cheeseocean/im/common/api/group postman/src/main/java/com/cheeseocean/im/postman postbox/src/main/java/com/cheeseocean/im/postbox/service postman/src/test/java/com/cheeseocean/im/postman/listener
git commit -m "feat: add ingress consumers and group membership facade"
```

## Task 4: Move History Persistence and Inbox Projection into `postbox`

**Files:**
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/MessageDocument.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/InboxDocument.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/entity/ConversationReadCursorDocument.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/repository/MessageDocumentRepository.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/repository/InboxDocumentRepository.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/repository/ConversationReadCursorRepository.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryTaskPersistenceService.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/listener/HistoryTaskListener.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageStoreServiceImpl.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/MessageStoreServiceImplTest.java`
- Create: `postbox/src/test/java/com/cheeseocean/im/postbox/listener/HistoryTaskListenerTest.java`

- [ ] **Step 1: Write failing tests for idempotent message upsert and inbox bulk projection**

```java
@Test
void persistShouldUpsertMessageOnceAndWriteInboxPerReceiver() {
    persistenceService.persist(historyTask);
    persistenceService.persist(historyTask);
    verify(messageRepository, times(1)).save(any(MessageDocument.class));
    verify(inboxRepository, times(2)).save(any(InboxDocument.class));
}
```

- [ ] **Step 2: Run the postbox tests to capture the current per-call save behavior**

Run: `./gradlew :postbox:test --tests "com.cheeseocean.im.postbox.service.MessageStoreServiceImplTest"`
Expected: FAIL because current store methods assume synchronous direct writes and do not expose event-oriented idempotent persistence.

- [ ] **Step 3: Extend `MessageDocument` and `InboxDocument` with `conversationSeq`, delivery timestamps, and unique IDs derived from message identity**

```java
inbox.setId(receiverId + ":" + messageId);
inbox.setConversationId(conversationId);
inbox.setSequence(conversationSeq);
inbox.setDeliveredAt(null);
inbox.setRead(false);
```

- [ ] **Step 4: Implement `HistoryTaskListener` and `HistoryTaskPersistenceService` to persist message facts and inbox projections before publishing delivery tasks**

```java
public void onMessage(HistoryTask task) {
    if (consumerDedupService.alreadyProcessed("postbox-history", task.getEventId())) {
        return;
    }
    persistenceService.persist(task);
    kafkaTemplate.send(KafkaTopics.Message.DELIVERY, task.deliveryKey(), DeliveryTaskCommand.from(task));
}
```

- [ ] **Step 5: Keep the old `MessageStoreService` methods as compatibility adapters during migration**

```java
@Override
public long saveOfflineMessage(MessageProto message) {
    HistoryTask task = HistoryTask.singleFromProto(message);
    return historyTaskPersistenceService.persist(task).firstStoredSequence();
}
```

- [ ] **Step 6: Run postbox tests**

Run: `./gradlew :postbox:test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add postbox/src/main/java/com/cheeseocean/im/postbox postbox/src/test/java/com/cheeseocean/im/postbox
git commit -m "feat: persist history tasks and inbox projections asynchronously"
```

## Task 5: Route Online Delivery Back Through `postoffice`

**Files:**
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/DeliveryTaskListener.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/DeliveryCompensationService.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/GatewayPushServiceImpl.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/connection/ConnectionManager.java`
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/service/GatewayPushServiceImplTest.java`
- Create: `postman/src/test/java/com/cheeseocean/im/postman/listener/DeliveryTaskListenerTest.java`

- [ ] **Step 1: Write failing tests for online delivery callback and offline fallback**

```java
@Test
void deliveryListenerShouldCallGatewayPushServiceAndQueueOfflinePushWhenNoRoutesExist() {
    when(gatewayPushService.pushToUser("userB", messageProto)).thenReturn(resultWithoutRoutes());
    listener.onMessage(deliveryTask);
    verify(kafkaTemplate).send(eq(KafkaTopics.Message.OFFLINE_PUSH), anyString(), any(OfflinePushTask.class));
}
```

- [ ] **Step 2: Run delivery-listener tests to show the listener path does not exist**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.listener.DeliveryTaskListenerTest"`
Expected: FAIL because online delivery is still embedded in `deliverFresh()`.

- [ ] **Step 3: Implement `DeliveryTaskListener` in `postman` and keep `GatewayPushServiceImpl` as the only long-connection writer**

```java
GatewayPushResult pushResult = gatewayPushService.pushToUser(task.getReceiverId(), toMessageProto(task));
if (!pushResult.isRouteFound()) {
    kafkaTemplate.send(KafkaTopics.Message.OFFLINE_PUSH, task.getReceiverId(), OfflinePushTask.from(task));
}
deliveryCompensationService.recordAttempt(task, pushResult);
```

- [ ] **Step 4: Add delivery-level dedup in `GatewayPushServiceImpl` before writing to a connection**

```java
if (deliveryDedupService.alreadyPushed(message.getServerMsgId(), receiverId, route.getDeviceId())) {
    continue;
}
```

- [ ] **Step 5: Run targeted postman and postoffice tests**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.listener.DeliveryTaskListenerTest" :postoffice:test --tests "com.cheeseocean.im.postoffice.service.GatewayPushServiceImplTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add postman/src/main/java/com/cheeseocean/im/postman postoffice/src/main/java/com/cheeseocean/im/postoffice postman/src/test/java/com/cheeseocean/im/postman/listener postoffice/src/test/java/com/cheeseocean/im/postoffice/service
git commit -m "feat: dispatch online delivery through postoffice gateway push"
```

## Task 6: Introduce Receipt Events and Conversation Read Cursors

**Files:**
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ReceiptMessageHandler.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/GatewayReceiptPublisher.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/ReceiptEventListener.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageStoreServiceImpl.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryQueryService.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationQueryService.java`
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/repository/ConversationReadCursorRepository.java`
- Create: `postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/ReceiptMessageHandlerTest.java`
- Create: `postman/src/test/java/com/cheeseocean/im/postman/listener/ReceiptEventListenerTest.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/api/MessageDeliveryService.java`
- Modify: `common/src/main/java/com/cheeseocean/im/common/dto/DeliveryAck.java`

- [ ] **Step 1: Write failing tests for delivered receipt and read cursor advancement**

```java
@Test
void receiptListenerShouldAdvanceConversationReadCursorMonotonically() {
    listener.onMessage(readReceiptAtSeq(9));
    listener.onMessage(readReceiptAtSeq(7));
    assertEquals(9L, repository.findByUserIdAndConversationId("userB", "single:userA:userB").getReadSeq());
}
```

- [ ] **Step 2: Run receipt tests to verify the current code only supports per-message ack**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.listener.ReceiptEventListenerTest" :postoffice:test --tests "com.cheeseocean.im.postoffice.handler.ReceiptMessageHandlerTest"`
Expected: FAIL because there is no receipt event path and no conversation read cursor persistence.

- [ ] **Step 3: Add `ReceiptMessageHandler` in `postoffice` that translates client receipt payloads into `ReceiptEvent`**

```java
ReceiptEvent event = ReceiptEvent.delivered(userId, conversationId, serverMsgId, seq, deviceId);
gatewayReceiptPublisher.publish(event);
```

- [ ] **Step 4: Update `postbox` persistence to treat delivered timestamp and read cursor separately**

```java
if (receiptEvent.isDelivered()) {
    inboxRepository.markDelivered(receiptEvent.getUserId(), receiptEvent.getServerMsgId(), receiptEvent.getReceiptTime());
}
if (receiptEvent.isReadCursor()) {
    readCursorRepository.advance(receiptEvent.getUserId(), receiptEvent.getConversationId(), receiptEvent.getSeq(), receiptEvent.getReceiptTime());
}
```

- [ ] **Step 5: Keep compatibility with old `DeliveryAck` values while new clients roll out**

```java
if ("READ".equals(ack.getAckType())) {
    return ReceiptEvent.readCursorFromLegacyAck(ack);
}
```

- [ ] **Step 6: Keep `MessageDeliveryService.ack()` authoritative until receipt parity is proven, then guard the new async path behind `asyncReceiptEnabled`**

```java
if (!messageFlowProperties.isAsyncReceiptEnabled()) {
    return messageStoreService.applyAck(ack);
}
gatewayReceiptPublisher.publish(ReceiptEvent.fromLegacyAck(ack));
return DeliveryResult.acceptedAck(ack.getServerMsgId());
```

- [ ] **Step 7: Run receipt and query tests**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.handler.ReceiptMessageHandlerTest" :postman:test --tests "com.cheeseocean.im.postmaster.listener.ReceiptEventListenerTest" :postbox:test --tests "com.cheeseocean.im.postbox.service.ConversationQueryServiceTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add postoffice/src/main/java/com/cheeseocean/im/postoffice postman/src/main/java/com/cheeseocean/im/postman postbox/src/main/java/com/cheeseocean/im/postbox postoffice/src/test/java/com/cheeseocean/im/postoffice/handler postman/src/test/java/com/cheeseocean/im/postman/listener
git commit -m "feat: add receipt events and conversation read cursors"
```

## Task 7: Wire Offline Push, Retry, DLQ, and Rollout Verification

**Files:**
- Create: `push/src/main/java/com/cheeseocean/im/push/listener/OfflinePushTaskListener.java`
- Modify: `push/src/main/java/com/cheeseocean/im/push/service/impl/MessagePushServiceImpl.java`
- Create: `postman/src/main/java/com/cheeseocean/im/postman/listener/OfflinePushRetryListener.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/DeliveryCompensationService.java`
- Create: `docs/architecture/im-message-rebuild-migration-checklist.md`
- Create: `push/src/test/java/com/cheeseocean/im/push/listener/OfflinePushTaskListenerTest.java`
- Modify: `push/src/test/java/com/cheeseocean/im/push/service/MessagePushServiceImplTest.java`

- [ ] **Step 1: Write failing tests for offline push topic handling and stale-route skipping**

```java
@Test
void offlinePushListenerShouldSkipVendorPushWhenUserCameBackOnline() {
    when(onlineRouteService.isOnline("userB")).thenReturn(true);
    listener.onMessage(offlinePushTask);
    verifyNoInteractions(pushProvider);
}
```

- [ ] **Step 2: Run push tests to confirm topic-driven offline execution does not exist yet**

Run: `./gradlew :push:test --tests "com.cheeseocean.im.postman.listener.OfflinePushTaskListenerTest"`
Expected: FAIL because offline push is still directly triggered by `postman`.

- [ ] **Step 3: Split retry orchestration from push execution**

```java
// postman
kafkaTemplate.send(KafkaTopics.Message.OFFLINE_PUSH, task.getReceiverId(), OfflinePushTask.from(task));

// push
if (onlineRouteService.isOnline(task.getReceiverId())) {
    return;
}
messagePushService.pushOffline(task.getReceiverId(), task.toMessageProto());
```

- [ ] **Step 4: Add migration flags and observability counters**

```java
@ConfigurationProperties(prefix = "cheeseim.message-flow")
public class MessageFlowProperties {
    private boolean asyncIngressEnabled;
    private boolean asyncHistoryEnabled;
    private boolean asyncDeliveryEnabled;
    private boolean asyncReceiptEnabled;
}
```

- [ ] **Step 5: Write the rollout checklist**

```markdown
1. Enable `asyncIngressEnabled` in one staging env.
2. Compare accepted message counts between legacy and Kafka ingress.
3. Enable `asyncHistoryEnabled` while keeping legacy store fallback on.
4. Enable `asyncDeliveryEnabled` after gateway push success-rate parity.
5. Enable `asyncReceiptEnabled` after read cursor parity checks.
```

- [ ] **Step 6: Run full module tests**

Run: `./gradlew :postman:test :postbox:test :postoffice:test :push:test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add postman/src/main/java/com/cheeseocean/im/postman push/src/main/java/com/cheeseocean/im/push docs/architecture/im-message-rebuild-migration-checklist.md push/src/test/java/com/cheeseocean/im/push
git commit -m "feat: add offline push listeners and message flow rollout controls"
```

## Task 8: Update End-to-End Smoke Coverage and Verify Migration Path

**Files:**
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImplTest.java`
- Modify: `push/src/test/java/com/cheeseocean/im/push/service/MessagePushServiceImplTest.java`

- [ ] **Step 1: Write a failing smoke test for accepted-send -> history persist -> online delivery -> delivered receipt**

```java
@Test
void imFlowShouldAcceptPersistDeliverAndConvergeReceipt() {
    DeliveryResult accepted = messageDeliveryService.deliver(command);
    assertEquals("ACCEPTED", accepted.getStatus());
    assertNotNull(messageRepository.findByServerMsgId(accepted.getServerMsgId()));
    assertTrue(receiverConnection.receivedMessage(accepted.getServerMsgId()));
    assertEquals(accepted.getConversationSeq(), readCursorRepository.findByUserIdAndConversationId("userB", command.getConversationId()).getReadSeq());
}
```

- [ ] **Step 2: Run the smoke test to verify the full async path is not complete yet**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.ImFlowSmokeTest"`
Expected: FAIL until all async listeners and receipt handling are wired together.

- [ ] **Step 3: Update the smoke fixtures to exercise the new Kafka listeners directly or through embedded-topic adapters**

```java
ingressEventListener.onMessage(capturedIngressEvent);
historyTaskListener.onMessage(capturedHistoryTask);
deliveryTaskListener.onMessage(capturedDeliveryTask);
receiptEventListener.onMessage(deliveredReceipt);
```

- [ ] **Step 4: Run the smoke test and full verification suite**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.ImFlowSmokeTest" :postman:test :postbox:test :push:test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryServiceImplTest.java postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java push/src/test/java/com/cheeseocean/im/push/service/MessagePushServiceImplTest.java
git commit -m "test: cover rebuilt message flow end to end"
```

## Rollout Notes

- Do not treat this plan as a pure rename. It changes shared contracts, receipt semantics, and persistence models.
- Keep old `DeliveryAck` compatibility adapters until all clients support `ReceiptEvent`.
- Keep group membership lookup behind `GroupMembershipFacade` so `postman` does not absorb `postbox` storage internals.
- Use `KafkaTopics.Message.OFFLINE_PUSH` for offline push execution and reserve retry/DLQ topics for `postman` orchestration only.
- Keep legacy synchronous fallback behind feature flags until message counts, push success, and read-cursor parity are verified.

## Recommended Execution Order

1. Task 1
2. Task 2
3. Task 3
4. Task 4
5. Task 5
6. Task 6
7. Task 7
8. Task 8
