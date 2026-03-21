package com.cheeseocean.im.postoffice;

import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.RouteSnapshot;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import com.cheeseocean.im.postbox.service.MessageStoreServiceImpl;
import com.cheeseocean.im.postman.service.DeliveryCompensationService;
import com.cheeseocean.im.postman.service.DeliveryStateMachine;
import com.cheeseocean.im.postman.service.GroupFanoutPlanner;
import com.cheeseocean.im.postman.service.MessageDeliveryServiceImpl;
import com.cheeseocean.im.postman.service.MessageIdempotencyService;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.service.GatewayPushServiceImpl;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import com.cheeseocean.im.push.entity.OfflinePushResult;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
    void singleChatShouldRecoverAcrossOfflineAndReconnect() {
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);

        Map<String, MessageDocument> messages = new HashMap<>();
        Map<String, InboxDocument> inboxes = new HashMap<>();
        when(messageRepository.save(any(MessageDocument.class))).thenAnswer(invocation -> {
            MessageDocument document = invocation.getArgument(0);
            messages.put(document.getServerMsgId(), document);
            return document;
        });
        when(messageRepository.findByServerMsgId(any())).thenAnswer(invocation -> messages.get(invocation.getArgument(0)));
        when(inboxRepository.save(any(InboxDocument.class))).thenAnswer(invocation -> {
            InboxDocument document = invocation.getArgument(0);
            inboxes.put(document.getId(), document);
            return document;
        });
        when(inboxRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(inboxes.get(invocation.getArgument(0))));
        when(inboxRepository.findByUserIdAndReadIsFalseOrderBySequenceAsc("userB")).thenAnswer(invocation -> inboxes.values().stream()
                .filter(doc -> "userB".equals(doc.getUserId()) && !doc.isRead())
                .sorted(Comparator.comparing(InboxDocument::getSequence, Comparator.nullsLast(Long::compareTo)))
                .toList());

        MessageStoreServiceImpl storeService = new MessageStoreServiceImpl(messageRepository, inboxRepository);

        ConnectionManager connectionManager = new ConnectionManager();
        ReflectionTestUtils.setField(connectionManager, "objectMapper", new ObjectMapper());

        AtomicReference<List<RouteSnapshot>> routes = new AtomicReference<>(List.of());
        OnlineRouteService routeService = mock(OnlineRouteService.class);
        when(routeService.findByUser("userB")).thenAnswer(invocation -> routes.get());
        GatewayPushServiceImpl gatewayPushService = new GatewayPushServiceImpl(connectionManager, routeService);

        com.cheeseocean.im.push.service.OfflinePushService offlinePushService =
                mock(com.cheeseocean.im.push.service.OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), any())).thenReturn(OfflinePushResult.success(List.of("userB")));
        MessagePushServiceImpl pushService = new MessagePushServiceImpl(offlinePushService, new PushDecisionService());

        DeliveryCompensationService compensationService = new DeliveryCompensationService(
                mock(KafkaTemplate.class), new ObjectMapper(), new SimpleMeterRegistry(), 3, 10L);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MessageDeliveryServiceImpl deliveryService = new MessageDeliveryServiceImpl(
                new MessageIdempotencyService(redisTemplate),
                new DeliveryStateMachine(),
                storeService,
                gatewayPushService,
                pushService,
                compensationService,
                new GroupFanoutPlanner(500));

        DeliveryResult offline = deliveryService.deliver(SingleChatFlowClient.command("c-offline", "offline hello"));

        assertEquals(DeliveryState.PUSH_TRIGGERED, offline.getState());
        assertEquals(1, storeService.getOfflineMessages("userB", 10).size());

        RouteSnapshot routeSnapshot = new RouteSnapshot();
        routeSnapshot.setUserId("userB");
        routeSnapshot.setDeviceId("ios-b");
        routeSnapshot.setGatewayNode("gateway-a");
        routes.set(List.of(routeSnapshot));

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

        DeliveryResult online = deliveryService.deliver(SingleChatFlowClient.command("c-online", "online hello"));

        assertEquals(DeliveryState.ONLINE_CONFIRMED, online.getState());
        TextWebSocketFrame outbound = channel.readOutbound();
        assertNotNull(outbound);
        assertFalse(outbound.text().isBlank());

        deliveryService.ack(SingleChatFlowClient.read(offline.getServerMsgId()));

        assertEquals(0, storeService.getOfflineMessages("userB", 10).size());
        assertTrue(pushService.findAttempt(offline.getServerMsgId(), "userB").orElseThrow().isCancelled());
    }
}
