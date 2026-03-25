# IM Chat Read Receipt Unification Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move read receipt handling onto the chat command path with `READ_CURSOR` semantics, remove the dedicated receipt handler pipeline, unify TCP/WebSocket business message models, and replace type-like constants with enums in core paths.

**Architecture:** Introduce transport-agnostic client/server envelopes plus enum-backed command/content/session/receipt types, then route `CHAT_SEND + READ_RECEIPT` into a dedicated receipt application service instead of the normal message ingress path. Keep TCP and WebSocket differences inside codecs/transport adapters only, and delete the legacy receipt handler/topic flow once the unified path is covered by tests.

**Tech Stack:** Java 17, Spring Boot, Netty, Dubbo, Kafka, Redis, MongoDB, Gradle, JUnit 5, Mockito

---

## File Structure

### Shared enums and payload contracts

- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/CommandType.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/ContentType.java`
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/SessionType.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/ReceiptType.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/ErrorCode.java`
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/constants/MessageConstants.java`
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/util/MessagePreviewUtil.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/protocol/ClientEnvelope.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/protocol/ServerEnvelope.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/message/ChatSendRequest.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/message/ReadReceiptPayload.java`
- Test: `common-core/src/test/java/com/cheeseocean/im/common/core/enums/ContentTypeTest.java`
- Test: `common-core/src/test/java/com/cheeseocean/im/common/core/util/MessagePreviewUtilTest.java`

### postoffice command handling and transport model

- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/ClientCommandEnvelope.java`
- Create: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/ServerCommandEnvelope.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/MessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/MessageHandlerFactory.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/AuthMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/HeartbeatMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- Delete: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ReceiptMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/MessageSendReqMapper.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/server/WebSocketServerHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/server/CheeseServerHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WSMessage.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/CheeseMessage.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WSMessageType.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/CheeseMessageType.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandlerTest.java`
- Delete: `postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/ReceiptMessageHandlerTest.java`
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/protocol/CheeseMessageTest.java`

### Receipt application and message path split

- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationReceiptService.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/ReceiptAckRpcImpl.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageSendRpcImpl.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/listener/IngressEventListener.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageStateService.java`
- Test: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationReceiptServiceTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/MessageSendRpcImplTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/MessageStateServiceTest.java`

### Receipt pipeline deletion and cleanup

- Delete: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/GatewayReceiptPublisher.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/listener/ReceiptEventListener.java`
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/constants/TopicNames.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/listener/ReceiptEventListenerTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ReceiptAckRpcImplTest.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/api/OnlineDispatchRpcImpl.java`
- Modify: `push/src/main/java/com/cheeseocean/im/push/listener/DeliveryEventListener.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationQueryService.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/HistoryQueryService.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/HistoryQueryServiceTest.java`

## Current-State Constraints

- `postoffice` currently dispatches by transport-specific message type and shares logic by converting TCP payloads into `WSMessage`.
- `ChatMessageHandler` currently assumes every chat payload becomes a normal `SendMessageReq`.
- `MessageSendRpcImpl` currently assigns default routing behavior to `contentType=2004` as if it were still a message.
- `MessageStateService` currently updates `userReadSeq` for the sender of any message that updates conversation state.
- `ReceiptAckRpcImpl` already contains the essential Redis mutation logic for read state but is hidden behind the old receipt RPC naming.
- Query-side preview tests currently expect `READ_RECEIPT` to render as a readable special type; the plan must preserve display behavior even after receipt messages stop being part of the normal send path.

## Task 1: Introduce Enum Boundaries for Content, Session, Receipt, and Command Types

**Files:**
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/CommandType.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/ContentType.java`
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/SessionType.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/ReceiptType.java`
- Create: `common-core/src/main/java/com/cheeseocean/im/common/core/enums/ErrorCode.java`
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/constants/MessageConstants.java`
- Test: `common-core/src/test/java/com/cheeseocean/im/common/core/enums/ContentTypeTest.java`

- [ ] **Step 1: Write the failing enum lookup tests**

```java
@Test
void fromCodeShouldResolveReadReceiptContentType() {
    assertEquals(ContentType.READ_RECEIPT, ContentType.fromCode(2004));
}

@Test
void fromCodeShouldRejectUnknownContentType() {
    assertThrows(IllegalArgumentException.class, () -> ContentType.fromCode(999999));
}
```

- [ ] **Step 2: Run the tests to verify enum types do not exist yet**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.enums.ContentTypeTest"`
Expected: FAIL because the new enum classes and lookup methods do not exist.

- [ ] **Step 3: Add enum classes with explicit code fields and lookup helpers**

```java
public enum ContentType {
    TEXT(101),
    READ_RECEIPT(2004);

    private final int code;

    public static ContentType fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ContentType code: " + code));
    }
}
```

- [ ] **Step 4: Trim `MessageConstants` down to non-enum constants only**

```java
public final class MessageConstants {
    public static final String REDIS_KEY_USER_TOKEN = "cheese_im:user:token:";
    public static final String REDIS_KEY_USER_ONLINE = "cheese_im:user:online:";
}
```

- [ ] **Step 5: Run the targeted common-core tests**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.enums.ContentTypeTest"`
Expected: PASS with enum code lookups and failure behavior covered.

- [ ] **Step 6: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core common-core/src/test/java/com/cheeseocean/im/common/core/enums/ContentTypeTest.java
git commit -m "refactor: add enum types for message domains"
```

## Task 2: Convert Preview and Query Utilities to Use `ContentType`

**Files:**
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/util/MessagePreviewUtil.java`
- Modify: `common-core/src/test/java/com/cheeseocean/im/common/core/util/MessagePreviewUtilTest.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessagePreviewResolver.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/HistoryQueryServiceTest.java`

- [ ] **Step 1: Write the failing preview utility test with enum-backed resolution**

```java
@Test
void resolvePreviewShouldSupportReadReceiptViaEnumCode() {
    assertEquals("[已读回执]", MessagePreviewUtil.resolvePreview(ContentType.READ_RECEIPT.getCode(), "raw", Map.of()));
}
```

- [ ] **Step 2: Run the utility and query-side tests**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.util.MessagePreviewUtilTest" :postbox:test --tests "com.cheeseocean.im.postbox.service.ConversationQueryServiceTest" --tests "com.cheeseocean.im.postbox.service.HistoryQueryServiceTest"`
Expected: FAIL until preview resolution stops depending on removed `MessageConstants` content-type values.

- [ ] **Step 3: Update preview utilities and resolvers to convert codes through `ContentType`**

```java
ContentType type = ContentType.fromCode(contentType);
switch (type) {
    case READ_RECEIPT -> "[已读回执]";
    case REVOKE_NOTIFY -> "你撤回了一条消息";
    default -> content;
}
```

- [ ] **Step 4: Update query tests to keep readable receipt/revoke/system previews**

```java
slot.setContentType(ContentType.READ_RECEIPT.getCode());
assertEquals(MessagePreviewType.READ_RECEIPT, conversations.get(0).getLastMessagePreviewType());
```

- [ ] **Step 5: Run the targeted tests**

Run: `./gradlew :common-core:test --tests "com.cheeseocean.im.common.core.util.MessagePreviewUtilTest" :postbox:test --tests "com.cheeseocean.im.postbox.service.ConversationQueryServiceTest" --tests "com.cheeseocean.im.postbox.service.HistoryQueryServiceTest"`
Expected: PASS with receipt preview behavior preserved.

- [ ] **Step 6: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/util/MessagePreviewUtil.java common-core/src/test/java/com/cheeseocean/im/common/core/util/MessagePreviewUtilTest.java postbox/src/main/java/com/cheeseocean/im/postbox/service/MessagePreviewResolver.java postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java postbox/src/test/java/com/cheeseocean/im/postbox/service/HistoryQueryServiceTest.java
git commit -m "refactor: route preview resolution through content type enum"
```

## Task 3: Add Unified Envelope and Chat Payload Contracts

**Files:**
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/protocol/ClientEnvelope.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/protocol/ServerEnvelope.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/message/ChatSendRequest.java`
- Create: `common-api/src/main/java/com/cheeseocean/im/common/api/dto/message/ReadReceiptPayload.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/protocol/CheeseMessageTest.java`

- [ ] **Step 1: Write the failing transport contract test for a unified envelope**

```java
@Test
void tcpReadReceiptShouldDecodeIntoChatSendEnvelope() {
    ClientEnvelope envelope = decodedTcpMessage.toClientEnvelope();
    assertEquals(CommandType.CHAT_SEND, envelope.getCommand());
}
```

- [ ] **Step 2: Run the protocol test**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.protocol.CheeseMessageTest"`
Expected: FAIL because the transport messages still convert into `WSMessage` only.

- [ ] **Step 3: Add shared envelope and payload DTOs**

```java
public final class ReadReceiptPayload {
    private ReceiptType receiptType;
    private String conversationId;
    private Long seq;
}
```

- [ ] **Step 4: Add conversion hooks from transport messages into the unified envelope model**

```java
public ClientEnvelope toClientEnvelope() {
    ClientEnvelope envelope = new ClientEnvelope();
    envelope.setCommand(CommandType.CHAT_SEND);
    envelope.setBody(parsedBody);
    return envelope;
}
```

- [ ] **Step 5: Run the targeted protocol test**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.protocol.CheeseMessageTest"`
Expected: PASS with shared command-envelope semantics for TCP.

- [ ] **Step 6: Commit**

```bash
git add common-api/src/main/java/com/cheeseocean/im/common/api/protocol common-api/src/main/java/com/cheeseocean/im/common/api/dto/message postoffice/src/test/java/com/cheeseocean/im/postoffice/protocol/CheeseMessageTest.java
git commit -m "refactor: add unified client server envelope contracts"
```

## Task 4: Make Handler Dispatch Transport-Agnostic

**Files:**
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/MessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/MessageHandlerFactory.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/AuthMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/HeartbeatMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/server/WebSocketServerHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/server/CheeseServerHandler.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/MessageHandlerFactoryTest.java`

- [ ] **Step 1: Write the failing handler-factory test for `CommandType` dispatch**

```java
@Test
void factoryShouldResolveChatHandlerByCommandType() {
    assertSame(chatHandler, factory.getHandler(CommandType.CHAT_SEND));
}
```

- [ ] **Step 2: Run the handler test**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.handler.MessageHandlerFactoryTest"`
Expected: FAIL because handlers still register by transport-specific numeric message type.

- [ ] **Step 3: Change the handler contract to support `CommandType`**

```java
public interface MessageHandler {
    CommandType getSupportedCommand();
    HandleResult handle(UserConnection connection, ClientEnvelope envelope);
}
```

- [ ] **Step 4: Update TCP and WebSocket server handlers to decode transport frames then share the same dispatch path**

```java
ClientEnvelope envelope = inbound.toClientEnvelope();
MessageHandler handler = messageHandlerFactory.getHandler(envelope.getCommand());
```

- [ ] **Step 5: Run the targeted postoffice tests**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.handler.MessageHandlerFactoryTest" --tests "com.cheeseocean.im.postoffice.protocol.CheeseMessageTest"`
Expected: PASS with factory dispatch no longer tied to `WSMessageType`.

- [ ] **Step 6: Commit**

```bash
git add postoffice/src/main/java/com/cheeseocean/im/postoffice/handler postoffice/src/main/java/com/cheeseocean/im/postoffice/server postoffice/src/test/java/com/cheeseocean/im/postoffice
git commit -m "refactor: dispatch gateway handlers by command type"
```

## Task 5: Add a Dedicated Receipt Application Service

**Files:**
- Create: `postbox/src/main/java/com/cheeseocean/im/postbox/service/ConversationReceiptService.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/ReceiptAckRpcImpl.java`
- Test: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationReceiptServiceTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ReceiptAckRpcImplTest.java`

- [ ] **Step 1: Write the failing receipt-service tests**

```java
@Test
void applyReadCursorShouldWriteUserReadSeq() {
    service.applyReadCursor("userB", "c1:userA:userB", 19L);
    verify(valueOperations).set(eq(RedisKeys.userReadSeq("userB", "c1:userA:userB")), eq("19"));
}

@Test
void applyReadCursorShouldRejectMissingSeq() {
    assertThrows(IllegalArgumentException.class, () -> service.applyReadCursor("userB", "c1:userA:userB", null));
}
```

- [ ] **Step 2: Run the postbox receipt tests**

Run: `./gradlew :postbox:test --tests "com.cheeseocean.im.postbox.service.ConversationReceiptServiceTest" --tests "com.cheeseocean.im.postbox.service.ReceiptAckRpcImplTest"`
Expected: FAIL because the dedicated receipt domain service does not exist yet.

- [ ] **Step 3: Extract read-state mutation from `ReceiptAckRpcImpl` into `ConversationReceiptService`**

```java
public void applyReadCursor(String userId, String conversationId, Long seq) {
    if (userId == null || conversationId == null || seq == null) {
        throw new IllegalArgumentException("read cursor requires userId, conversationId, and seq");
    }
    redisTemplate.opsForValue().set(RedisKeys.userReadSeq(userId, conversationId), String.valueOf(seq));
}
```

- [ ] **Step 4: Keep `ReceiptAckRpcImpl` as a thin adapter or remove it from chat-side usage**

```java
if ("READ".equals(req.getAckType())) {
    conversationReceiptService.applyReadCursor(req.getUserId(), req.getConversationId(), req.getSeq());
}
```

- [ ] **Step 5: Run the targeted postbox tests**

Run: `./gradlew :postbox:test --tests "com.cheeseocean.im.postbox.service.ConversationReceiptServiceTest" --tests "com.cheeseocean.im.postbox.service.ReceiptAckRpcImplTest"`
Expected: PASS with receipt state mutation covered directly.

- [ ] **Step 6: Commit**

```bash
git add postbox/src/main/java/com/cheeseocean/im/postbox/service postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationReceiptServiceTest.java postbox/src/test/java/com/cheeseocean/im/postbox/service/ReceiptAckRpcImplTest.java
git commit -m "refactor: extract conversation receipt service"
```

## Task 6: Route `CHAT_SEND + READ_RECEIPT` Away from Normal Message Send

**Files:**
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/MessageSendReqMapper.java`
- Modify: `postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageSendRpcImpl.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandlerTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/MessageSendRpcImplTest.java`

- [ ] **Step 1: Write the failing chat-handler test for read-receipt branching**

```java
@Test
void readReceiptChatSendShouldCallConversationReceiptServiceInsteadOfMessageSendRpc() {
    handler.handle(connection, readReceiptEnvelope());
    verify(conversationReceiptService).applyReadCursor("userB", "c1:userA:userB", 19L);
    verifyNoInteractions(messageSendRpc);
}
```

- [ ] **Step 2: Run the chat-handler and send-rpc tests**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.handler.ChatMessageHandlerTest" :postbox:test --tests "com.cheeseocean.im.postbox.service.MessageSendRpcImplTest"`
Expected: FAIL because `ChatMessageHandler` still maps every chat payload into `SendMessageReq`.

- [ ] **Step 3: Branch `ChatMessageHandler` by `ContentType` and route receipts to `ConversationReceiptService`**

```java
if (chatRequest.getContentType() == ContentType.READ_RECEIPT) {
    ReadReceiptPayload payload = parseReceiptPayload(chatRequest.getContent());
    conversationReceiptService.applyReadCursor(context.getUserId(), payload.getConversationId(), payload.getSeq());
    return HandleResult.success(ServerCommandEnvelope.ok(requestId));
}
```

- [ ] **Step 4: Remove read-receipt pseudo-message defaults from `MessageSendRpcImpl`**

```java
private static boolean defaultNeedHistory(ContentType contentType) {
    return contentType != ContentType.TYPING && contentType != ContentType.FORCE_LOGOUT;
}
```

- [ ] **Step 5: Run the targeted tests**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.handler.ChatMessageHandlerTest" :postbox:test --tests "com.cheeseocean.im.postbox.service.MessageSendRpcImplTest"`
Expected: PASS with receipts no longer treated as normal sends.

- [ ] **Step 6: Commit**

```bash
git add postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java postoffice/src/main/java/com/cheeseocean/im/postoffice/service/MessageSendReqMapper.java postbox/src/main/java/com/cheeseocean/im/postbox/service/MessageSendRpcImpl.java postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandlerTest.java postbox/src/test/java/com/cheeseocean/im/postbox/service/MessageSendRpcImplTest.java
git commit -m "feat: handle read receipts through chat command path"
```

## Task 7: Guard the Message Pipeline Against Receipt Side Effects

**Files:**
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/listener/IngressEventListener.java`
- Modify: `postman/src/main/java/com/cheeseocean/im/postman/service/MessageStateService.java`
- Test: `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/service/MessageStateServiceTest.java`

- [ ] **Step 1: Write the failing ingress/message-state regression tests**

```java
@Test
void readReceiptShouldNotPublishHistoryOrDeliveryEvents() {
    listener.handle(readReceiptIngressEvent());
    verify(kafkaTemplate, never()).send(eq(TopicNames.HISTORY), any(), any());
    verify(kafkaTemplate, never()).send(eq(TopicNames.DELIVERY), any(), any());
}
```

- [ ] **Step 2: Run the postman tests**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.listener.IngressEventListenerTest" --tests "com.cheeseocean.im.postmaster.service.MessageStateServiceTest"`
Expected: FAIL until receipt content cannot enter the normal ingress path.

- [ ] **Step 3: Add explicit guards so receipt payloads cannot mutate message-pipeline state**

```java
if (message.getContentType() == ContentType.READ_RECEIPT.getCode()) {
    throw new IllegalStateException("READ_RECEIPT must not reach ingress pipeline");
}
```

- [ ] **Step 4: Run the targeted postman tests**

Run: `./gradlew :postman:test --tests "com.cheeseocean.im.postmaster.listener.IngressEventListenerTest" --tests "com.cheeseocean.im.postmaster.service.MessageStateServiceTest"`
Expected: PASS with guardrails against accidental receipt regression.

- [ ] **Step 5: Commit**

```bash
git add postman/src/main/java/com/cheeseocean/im/postman/listener/IngressEventListener.java postman/src/main/java/com/cheeseocean/im/postman/service/MessageStateService.java postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java postman/src/test/java/com/cheeseocean/im/postman/service/MessageStateServiceTest.java
git commit -m "test: guard message pipeline from receipt side effects"
```

## Task 8: Remove the Legacy Receipt Handler and Topic Pipeline

**Files:**
- Delete: `postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ReceiptMessageHandler.java`
- Delete: `postoffice/src/main/java/com/cheeseocean/im/postoffice/service/GatewayReceiptPublisher.java`
- Delete: `postman/src/main/java/com/cheeseocean/im/postman/listener/ReceiptEventListener.java`
- Modify: `common-core/src/main/java/com/cheeseocean/im/common/core/constants/TopicNames.java`
- Delete: `postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/ReceiptMessageHandlerTest.java`
- Delete or Modify: `postman/src/test/java/com/cheeseocean/im/postman/listener/ReceiptEventListenerTest.java`

- [ ] **Step 1: Write the failing cleanup assertion**

```java
@Test
void topicNamesShouldNotExposeReceiptTopic() {
    assertFalse(Arrays.asList(TopicNames.ALL).contains("receipt"));
}
```

- [ ] **Step 2: Run the affected tests**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.handler.ChatMessageHandlerTest" :postman:test --tests "com.cheeseocean.im.postmaster.listener.ReceiptEventListenerTest"`
Expected: FAIL because the old receipt classes and tests still exist.

- [ ] **Step 3: Delete the dedicated receipt path and remove topic references**

```java
public final class TopicNames {
    public static final String INGRESS = "ingress";
    public static final String HISTORY = "history";
    public static final String DELIVERY = "delivery";
}
```

- [ ] **Step 4: Replace the deleted tests with assertions on the new chat receipt path**

```java
assertThrows(UnsupportedOperationException.class, () -> legacyDecoder.decodeOldReceiptType(...));
```

- [ ] **Step 5: Run the targeted tests**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.handler.ChatMessageHandlerTest" :postman:test --tests "com.cheeseocean.im.postmaster.listener.IngressEventListenerTest" :postbox:test --tests "com.cheeseocean.im.postbox.service.ConversationReceiptServiceTest"`
Expected: PASS with no receipt-topic dependencies remaining.

- [ ] **Step 6: Commit**

```bash
git add common-core/src/main/java/com/cheeseocean/im/common/core/constants/TopicNames.java postoffice/src/main/java/com/cheeseocean/im/postoffice postman/src/main/java/com/cheeseocean/im/postman postoffice/src/test/java/com/cheeseocean/im/postoffice postman/src/test/java/com/cheeseocean/im/postman
git commit -m "refactor: remove legacy receipt handler pipeline"
```

## Task 9: Finish TCP/WS Envelope Unification for Outbound Delivery

**Files:**
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/api/OnlineDispatchRpcImpl.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WSMessage.java`
- Modify: `postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/CheeseMessage.java`
- Modify: `push/src/main/java/com/cheeseocean/im/push/listener/DeliveryEventListener.java`
- Test: `postoffice/src/test/java/com/cheeseocean/im/postoffice/api/OnlineDispatchRpcImplTest.java`

- [ ] **Step 1: Write the failing outbound-envelope test**

```java
@Test
void dispatchShouldSendServerEnvelopeBackedRecvCommand() {
    DispatchResult result = service.dispatchMessage(req).getResults().get(0);
    assertTrue(result.isSuccess());
    verify(connectionManager).sendMessageToConnection(eq(connection), argThat(message -> message.toServerEnvelope().getCommand() == CommandType.CHAT_RECV));
}
```

- [ ] **Step 2: Run the targeted dispatch test**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.api.OnlineDispatchRpcImplTest"`
Expected: FAIL because outbound dispatch is still building transport-specific messages directly.

- [ ] **Step 3: Introduce shared outbound envelope creation before transport encoding**

```java
ServerEnvelope envelope = ServerEnvelope.chatRecv(payload.getServerMsgId(), payload);
connectionManager.sendMessageToConnection(connection, transportEncoder.encode(envelope, connection));
```

- [ ] **Step 4: Run the targeted postoffice and push tests**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.api.OnlineDispatchRpcImplTest" :push:test --tests "com.cheeseocean.im.postman.listener.DeliveryEventListenerTest"`
Expected: PASS with outbound delivery still working through the unified envelope boundary.

- [ ] **Step 5: Commit**

```bash
git add postoffice/src/main/java/com/cheeseocean/im/postoffice/api/OnlineDispatchRpcImpl.java postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/WSMessage.java postoffice/src/main/java/com/cheeseocean/im/postoffice/protocol/CheeseMessage.java push/src/main/java/com/cheeseocean/im/push/listener/DeliveryEventListener.java postoffice/src/test/java/com/cheeseocean/im/postoffice/api/OnlineDispatchRpcImplTest.java
git commit -m "refactor: unify outbound delivery envelopes"
```

## Task 10: Full Verification and Cleanup

**Files:**
- Modify: `postoffice/src/test/java/com/cheeseocean/im/postoffice/ImFlowSmokeTest.java`
- Modify: `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/ConversationQueryServiceTest.java`
- Modify: `postbox/src/test/java/com/cheeseocean/im/postbox/service/HistoryQueryServiceTest.java`

- [ ] **Step 1: Add the failing end-to-end smoke test for chat read receipt**

```java
@Test
void chatReadReceiptShouldUpdateReadSeqWithoutCreatingHistoryMessage() {
    sendReadReceiptThroughChat();
    assertEquals("19", redis.get(RedisKeys.userReadSeq("userB", "c1:userA:userB")));
    assertNull(findHistorySlotByContentType(ContentType.READ_RECEIPT.getCode()));
}
```

- [ ] **Step 2: Run the smoke and regression suite**

Run: `./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.ImFlowSmokeTest" :postman:test --tests "com.cheeseocean.im.postmaster.listener.IngressEventListenerTest" :postbox:test --tests "com.cheeseocean.im.postbox.service.ConversationQueryServiceTest" --tests "com.cheeseocean.im.postbox.service.HistoryQueryServiceTest"`
Expected: FAIL until the new path is fully wired and old receipt persistence side effects are gone.

- [ ] **Step 3: Fill any remaining gaps in transport mapping, enum serialization, or read-state application**

```java
objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
```

- [ ] **Step 4: Run the focused module suites**

Run: `./gradlew :common-core:test :common-api:test :postoffice:test :postman:test :postbox:test :push:test`
Expected: PASS in all touched modules.

- [ ] **Step 5: Run the top-level verification commands used for this refactor**

Run: `./gradlew test`
Expected: PASS with no remaining references to the dedicated receipt handler pipeline.

- [ ] **Step 6: Commit**

```bash
git add common-core common-api postoffice postman postbox push
git commit -m "feat: unify chat read receipts and transport message model"
```

