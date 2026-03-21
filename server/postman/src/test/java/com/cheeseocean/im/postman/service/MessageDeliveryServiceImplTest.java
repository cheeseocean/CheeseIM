package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.GatewayPushService;
import com.cheeseocean.im.common.api.MessagePushService;
import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.common.dto.PushResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.StoredMessage;
import com.cheeseocean.im.postman.config.MessageFlowProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageDeliveryServiceImplTest {

    @Test
    void deliverShouldAllocateConversationSeqAndPublishIngressEventWhenAsyncIngressEnabled() {
        MessageIdempotencyService idempotencyService = mock(MessageIdempotencyService.class);
        MessageStoreService storeService = mock(MessageStoreService.class);
        GatewayPushService gatewayPushService = mock(GatewayPushService.class);
        MessagePushService messagePushService = mock(MessagePushService.class);
        DeliveryCompensationService compensationService = mock(DeliveryCompensationService.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        IngressEventPublisher ingressEventPublisher = mock(IngressEventPublisher.class);

        when(idempotencyService.findExisting("userA", "single:userA:userB", "c-accept"))
                .thenReturn(Optional.empty());
        when(conversationSeqService.nextSeq("single:userA:userB")).thenReturn(1001L);

        MessageDeliveryServiceImpl service = new MessageDeliveryServiceImpl(
                idempotencyService,
                new DeliveryStateMachine(),
                storeService,
                gatewayPushService,
                messagePushService,
                compensationService,
                new GroupFanoutPlanner(500),
                null,
                conversationSeqService,
                ingressEventPublisher,
                asyncIngressEnabled());

        DeliveryResult result = service.deliver(DeliveryCommand.builder()
                .clientMsgId("c-accept")
                .conversationId("single:userA:userB")
                .senderId("userA")
                .receiverId("userB")
                .deviceId("ios-1")
                .content("hello")
                .contentType(101)
                .sessionType(1)
                .build());

        assertTrue(result.isSuccess());
        assertEquals("ACCEPTED", result.getStatus());
        assertEquals(1001L, result.getConversationSeq());
        verify(ingressEventPublisher).publish(any(IngressEvent.class));
        verify(idempotencyService).remember("userA", "single:userA:userB", "c-accept", result);
        verifyNoInteractions(storeService, gatewayPushService, messagePushService, compensationService);
    }

    @Test
    void acceptedResultFactoryShouldExposeServerMsgIdAndConversationSeq() {
        DeliveryResult result = DeliveryResult.accepted("s-1", 1001L);

        assertTrue(result.isSuccess());
        assertEquals("ACCEPTED", result.getStatus());
        assertEquals("s-1", result.getServerMsgId());
        assertEquals(1001L, result.getConversationSeq());
        assertEquals(DeliveryState.INIT, result.getState());
    }

    @Test
    void duplicateClientMsgIdShouldReturnAcceptedResultWithServerMsgIdAndSeq() {
        MessageIdempotencyService idempotencyService = mock(MessageIdempotencyService.class);
        MessageStoreService storeService = mock(MessageStoreService.class);
        GatewayPushService gatewayPushService = mock(GatewayPushService.class);
        MessagePushService messagePushService = mock(MessagePushService.class);
        DeliveryCompensationService compensationService = mock(DeliveryCompensationService.class);

        DeliveryResult existing = DeliveryResult.accepted("s-1", 1001L);
        when(idempotencyService.findExisting("userA", "single:userA:userB", "c-1"))
                .thenReturn(Optional.of(existing));

        MessageDeliveryServiceImpl service = new MessageDeliveryServiceImpl(
                idempotencyService, new DeliveryStateMachine(), storeService, gatewayPushService, messagePushService, compensationService);

        DeliveryResult result = service.deliver(DeliveryCommand.builder()
                .clientMsgId("c-1")
                .conversationId("single:userA:userB")
                .senderId("userA")
                .receiverId("userB")
                .deviceId("ios-1")
                .content("hello")
                .contentType(101)
                .sessionType(1)
                .build());

        assertTrue(result.isSuccess());
        assertEquals("ACCEPTED", result.getStatus());
        assertEquals("s-1", result.getServerMsgId());
        assertEquals(1001L, result.getConversationSeq());
        verifyNoInteractions(storeService, gatewayPushService, messagePushService, compensationService);
    }

    @Test
    void onlineDeliveryFailureShouldTransitionToInboxedAndPushPending() {
        MessageIdempotencyService idempotencyService = mock(MessageIdempotencyService.class);
        MessageStoreService storeService = mock(MessageStoreService.class);
        GatewayPushService gatewayPushService = mock(GatewayPushService.class);
        MessagePushService messagePushService = mock(MessagePushService.class);
        DeliveryCompensationService compensationService = mock(DeliveryCompensationService.class);

        when(idempotencyService.findExisting("userA", "single:userA:userB", "c-2"))
                .thenReturn(Optional.empty());

        StoredMessage stored = new StoredMessage();
        stored.setServerMsgId("s-2");
        stored.setConversationId("single:userA:userB");
        stored.setSenderId("userA");
        stored.setReceiverId("userB");
        when(storeService.saveMessage(any(StoredMessage.class))).thenReturn(stored);
        when(storeService.saveOfflineMessage(any())).thenReturn(9L);

        GatewayPushResult pushResult = new GatewayPushResult();
        pushResult.setReceiverId("userB");
        pushResult.setRouteFound(true);
        pushResult.setFailedDeviceIds(java.util.List.of("ios-1"));
        when(gatewayPushService.pushToUser(eq("userB"), any())).thenReturn(pushResult);
        when(messagePushService.pushOffline(eq("userB"), any())).thenReturn(PushResult.success("userB", "mock"));

        MessageDeliveryServiceImpl service = new MessageDeliveryServiceImpl(
                idempotencyService,
                new DeliveryStateMachine(),
                storeService,
                gatewayPushService,
                messagePushService,
                compensationService,
                new GroupFanoutPlanner(500),
                null,
                mock(ConversationSeqService.class),
                mock(IngressEventPublisher.class),
                asyncIngressDisabled());

        DeliveryResult result = service.deliver(DeliveryCommand.builder()
                .clientMsgId("c-2")
                .conversationId("single:userA:userB")
                .senderId("userA")
                .receiverId("userB")
                .deviceId("ios-1")
                .content("offline")
                .contentType(101)
                .sessionType(1)
                .build());

        assertTrue(result.isSuccess());
        assertEquals("s-2", result.getServerMsgId());
        assertEquals(DeliveryState.PUSH_TRIGGERED, result.getState());
        assertEquals(9L, result.getStoredMessageId());
        verify(messagePushService).pushOffline(eq("userB"), any());
        verify(compensationService).schedule(any());
    }

    @Test
    void onlineDeliverySuccessShouldNotScheduleCompensation() {
        MessageIdempotencyService idempotencyService = mock(MessageIdempotencyService.class);
        MessageStoreService storeService = mock(MessageStoreService.class);
        GatewayPushService gatewayPushService = mock(GatewayPushService.class);
        MessagePushService messagePushService = mock(MessagePushService.class);
        DeliveryCompensationService compensationService = mock(DeliveryCompensationService.class);

        when(idempotencyService.findExisting("userA", "single:userA:userB", "c-3"))
                .thenReturn(Optional.empty());

        StoredMessage stored = new StoredMessage();
        stored.setServerMsgId("s-3");
        stored.setConversationId("single:userA:userB");
        stored.setSenderId("userA");
        stored.setReceiverId("userB");
        when(storeService.saveMessage(any(StoredMessage.class))).thenReturn(stored);

        GatewayPushResult pushResult = new GatewayPushResult();
        pushResult.setReceiverId("userB");
        pushResult.setRouteFound(true);
        pushResult.setDeliveredDeviceIds(java.util.List.of("ios-1"));
        when(gatewayPushService.pushToUser(eq("userB"), any())).thenReturn(pushResult);

        MessageDeliveryServiceImpl service = new MessageDeliveryServiceImpl(
                idempotencyService,
                new DeliveryStateMachine(),
                storeService,
                gatewayPushService,
                messagePushService,
                compensationService,
                new GroupFanoutPlanner(500),
                null,
                mock(ConversationSeqService.class),
                mock(IngressEventPublisher.class),
                asyncIngressDisabled());

        DeliveryResult result = service.deliver(DeliveryCommand.builder()
                .clientMsgId("c-3")
                .conversationId("single:userA:userB")
                .senderId("userA")
                .receiverId("userB")
                .deviceId("ios-1")
                .content("online")
                .contentType(101)
                .sessionType(1)
                .build());

        assertEquals(DeliveryState.ONLINE_CONFIRMED, result.getState());
        verify(compensationService, never()).schedule(any());
    }

    private static MessageFlowProperties asyncIngressEnabled() {
        MessageFlowProperties properties = new MessageFlowProperties();
        properties.setAsyncIngressEnabled(true);
        return properties;
    }

    private static MessageFlowProperties asyncIngressDisabled() {
        return new MessageFlowProperties();
    }
}
