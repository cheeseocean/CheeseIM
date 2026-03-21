package com.cheeseocean.im.postoffice;

import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.DeliveryTaskCommand;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.common.dto.ReceiptEvent;
import com.cheeseocean.im.common.dto.RouteSnapshot;
import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postbox.entity.ConversationReadCursorDocument;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.listener.HistoryTaskListener;
import com.cheeseocean.im.postbox.repository.ConversationReadCursorRepository;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import com.cheeseocean.im.postbox.service.HistoryTaskPersistenceService;
import com.cheeseocean.im.postbox.service.MessageStoreServiceImpl;
import com.cheeseocean.im.postman.listener.DeliveryTaskListener;
import com.cheeseocean.im.postman.listener.IngressEventListener;
import com.cheeseocean.im.postman.listener.ReceiptEventListener;
import com.cheeseocean.im.postman.service.DeliveryCompensationService;
import com.cheeseocean.im.postman.service.GroupMembershipFacade;
import com.cheeseocean.im.postman.service.GroupFanoutPlanner;
import com.cheeseocean.im.postman.service.IngressEventPublisher;
import com.cheeseocean.im.postman.service.MessageDeliveryServiceImpl;
import com.cheeseocean.im.postman.service.MessageIdempotencyService;
import com.cheeseocean.im.postman.service.ConversationSeqService;
import com.cheeseocean.im.postman.metrics.MessageFlowMetrics;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.service.GatewayPushServiceImpl;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import com.cheeseocean.im.push.service.PushDecisionService;
import com.cheeseocean.im.push.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImFlowSmokeTest {

    @Test
    void sendMessageResponseShouldKeepLegacyFieldsAndExposeConversationSeq() {
        WSMessage response = WSMessage.sendMsgResp("op-1", "s-1", "c-1", 123L, 1001L);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();

        assertEquals("s-1", data.get("serverMsgID"));
        assertEquals("c-1", data.get("clientMsgID"));
        assertEquals(123L, data.get("sendTime"));
        assertEquals(1001L, data.get("conversationSeq"));

        WSMessage legacyResponse = WSMessage.sendMsgResp("op-1", "s-1", "c-1", 123L);
        @SuppressWarnings("unchecked")
        Map<String, Object> legacyData = (Map<String, Object>) legacyResponse.getData();
        assertNull(legacyData.get("conversationSeq"));
    }

    @Test
    void asyncMessageFlowShouldAcceptPersistDeliverAndConvergeReceipt() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, MessageDocument> messages = new HashMap<>();
        Map<String, InboxDocument> inboxes = new HashMap<>();
        Map<String, ConversationReadCursorDocument> cursors = new HashMap<>();

        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        when(messageRepository.existsById(anyString())).thenAnswer(invocation -> messages.containsKey(invocation.getArgument(0)));
        when(messageRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(messages.get(invocation.getArgument(0))));
        when(messageRepository.findByServerMsgId(anyString())).thenAnswer(invocation -> messages.get(invocation.getArgument(0)));
        when(messageRepository.save(any(MessageDocument.class))).thenAnswer(invocation -> {
            MessageDocument document = invocation.getArgument(0);
            messages.put(document.getServerMsgId(), document);
            return document;
        });

        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        when(inboxRepository.existsById(anyString())).thenAnswer(invocation -> inboxes.containsKey(invocation.getArgument(0)));
        when(inboxRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(inboxes.get(invocation.getArgument(0))));
        when(inboxRepository.save(any(InboxDocument.class))).thenAnswer(invocation -> {
            InboxDocument document = invocation.getArgument(0);
            inboxes.put(document.getId(), document);
            return document;
        });
        when(inboxRepository.findByUserIdAndReadIsFalseOrderBySequenceAsc("userB")).thenAnswer(invocation -> inboxes.values().stream()
                .filter(doc -> "userB".equals(doc.getUserId()) && !doc.isRead())
                .sorted(Comparator.comparing(InboxDocument::getSequence, Comparator.nullsLast(Long::compareTo)))
                .toList());
        when(inboxRepository.findByUserIdAndConversationIdOrderBySequenceDesc("userB", "single:userA:userB"))
                .thenAnswer(invocation -> inboxes.values().stream()
                        .filter(doc -> "userB".equals(doc.getUserId()) && "single:userA:userB".equals(doc.getConversationId()))
                        .sorted(Comparator.comparing(InboxDocument::getSequence, Comparator.nullsLast(Long::compareTo)).reversed())
                        .toList());

        ConversationReadCursorRepository readCursorRepository = mock(ConversationReadCursorRepository.class);
        when(readCursorRepository.findByUserIdAndConversationId("userB", "single:userA:userB"))
                .thenAnswer(invocation -> cursors.get("userB:single:userA:userB"));
        when(readCursorRepository.save(any(ConversationReadCursorDocument.class))).thenAnswer(invocation -> {
            ConversationReadCursorDocument document = invocation.getArgument(0);
            cursors.put(document.getId(), document);
            return document;
        });

        HistoryTaskPersistenceService historyTaskPersistenceService =
                new HistoryTaskPersistenceService(messageRepository, inboxRepository);
        MessageStoreServiceImpl storeService = new MessageStoreServiceImpl(
                messageRepository, inboxRepository, readCursorRepository, historyTaskPersistenceService);

        ConnectionManager connectionManager = new ConnectionManager();
        ReflectionTestUtils.setField(connectionManager, "objectMapper", objectMapper);

        OnlineRouteService routeService = mock(OnlineRouteService.class);
        RouteSnapshot routeSnapshot = new RouteSnapshot();
        routeSnapshot.setUserId("userB");
        routeSnapshot.setDeviceId("ios-b");
        routeSnapshot.setGatewayNode("gateway-a");
        when(routeService.findByUser("userB")).thenReturn(List.of(routeSnapshot));

        EmbeddedChannel channel = new EmbeddedChannel();
        UserConnection activeConnection = new UserConnection("conn-1", "userB", 1, channel);
        activeConnection.setAuthenticated("token");
        @SuppressWarnings("unchecked")
        Map<String, UserConnection> connectionMap =
                (Map<String, UserConnection>) ReflectionTestUtils.getField(connectionManager, "connectionMap");
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> userConnectionMap =
                (Map<String, Set<String>>) ReflectionTestUtils.getField(connectionManager, "userConnectionMap");
        connectionMap.put("conn-1", activeConnection);
        userConnectionMap.put("userB", Set.of("conn-1"));

        GatewayPushServiceImpl gatewayPushService = new GatewayPushServiceImpl(connectionManager, routeService);

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> ingressKafka = mock(KafkaTemplate.class);
        AtomicReference<IngressEvent> ingressRef = new AtomicReference<>();
        when(ingressKafka.send(eq(KafkaTopics.Message.INGRESS), eq("single:userA:userB"), any(IngressEvent.class)))
                .thenAnswer(invocation -> {
                    ingressRef.set(invocation.getArgument(2));
                    return CompletableFuture.completedFuture(null);
                });

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> historyKafka = mock(KafkaTemplate.class);
        AtomicReference<Object> historyRef = new AtomicReference<>();
        when(historyKafka.send(eq(KafkaTopics.Message.HISTORY), eq("single:userA:userB"), any()))
                .thenAnswer(invocation -> {
                    historyRef.set(invocation.getArgument(2));
                    return CompletableFuture.completedFuture(null);
                });

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> historyToDeliveryKafka = mock(KafkaTemplate.class);
        AtomicReference<DeliveryTaskCommand> deliveryRef = new AtomicReference<>();
        when(historyToDeliveryKafka.send(eq(KafkaTopics.Message.DELIVERY), eq("userB"), any(DeliveryTaskCommand.class)))
                .thenAnswer(invocation -> {
                    deliveryRef.set(invocation.getArgument(2));
                    return CompletableFuture.completedFuture(null);
                });

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> offlineKafka = mock(KafkaTemplate.class);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        AtomicLong seq = new AtomicLong(1000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> seq.incrementAndGet());

        MessageDeliveryServiceImpl deliveryService = new MessageDeliveryServiceImpl(
                new MessageIdempotencyService(redisTemplate),
                storeService,
                new MessagePushServiceImpl(mock(com.cheeseocean.im.push.service.OfflinePushService.class), new PushDecisionService()),
                null,
                new ConversationSeqService(redisTemplate),
                new IngressEventPublisher(ingressKafka, new MessageFlowMetrics(new SimpleMeterRegistry())));

        DeliveryResult accepted = deliveryService.deliver(SingleChatFlowClient.command("c-async", "async hello"));

        assertTrue(accepted.isSuccess());
        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals(1001L, accepted.getConversationSeq());
        assertNotNull(ingressRef.get());

        IngressEventListener ingressEventListener = new IngressEventListener(
                objectMapper, historyKafka, mock(GroupMembershipFacade.class), new GroupFanoutPlanner(500));
        ingressEventListener.onMessage(objectMapper.writeValueAsString(ingressRef.get()));

        HistoryTaskListener historyTaskListener = new HistoryTaskListener(historyToDeliveryKafka, historyTaskPersistenceService);
        historyTaskListener.onMessage((com.cheeseocean.im.common.dto.HistoryTask) historyRef.get());

        DeliveryTaskListener deliveryTaskListener = new DeliveryTaskListener(
                objectMapper,
                gatewayPushService,
                offlineKafka,
                new DeliveryCompensationService(mock(KafkaTemplate.class), objectMapper, new SimpleMeterRegistry(), 3, 10L));
        deliveryTaskListener.onMessage(objectMapper.writeValueAsString(deliveryRef.get()));

        TextWebSocketFrame outbound = channel.readOutbound();
        assertNotNull(outbound);
        assertNotNull(messages.get(accepted.getServerMsgId()));
        assertEquals(1, storeService.getOfflineMessages("userB", 10).size());

        ReceiptEventListener receiptEventListener = new ReceiptEventListener(objectMapper, storeService);
        receiptEventListener.onMessage(objectMapper.writeValueAsString(
                ReceiptEvent.readCursor("userB", "single:userA:userB", accepted.getConversationSeq(), "ios-b")));

        assertEquals(0, storeService.getOfflineMessages("userB", 10).size());
        assertEquals(accepted.getConversationSeq(),
                cursors.get("userB:single:userA:userB").getReadSeq());
    }
}
