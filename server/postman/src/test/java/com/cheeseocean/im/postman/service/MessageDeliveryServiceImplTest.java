package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.MessagePushService;
import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.common.entity.DeliveryState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageDeliveryServiceImplTest {

    private MessageIdempotencyService idempotencyService;
    private MessageStoreService storeService;
    private MessagePushService messagePushService;
    private ConversationSeqService conversationSeqService;
    private IngressEventPublisher ingressEventPublisher;
    private MessageDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        idempotencyService = mock(MessageIdempotencyService.class);
        storeService = mock(MessageStoreService.class);
        messagePushService = mock(MessagePushService.class);
        conversationSeqService = mock(ConversationSeqService.class);
        ingressEventPublisher = mock(IngressEventPublisher.class);
        service = new MessageDeliveryServiceImpl(
                idempotencyService,
                storeService,
                messagePushService,
                null,
                conversationSeqService,
                ingressEventPublisher
        );
    }

    @Test
    void deliverShouldAllocateConversationSeqAndPublishIngressEvent() {
        when(idempotencyService.findExisting("userA", "single:userA:userB", "c-accept"))
                .thenReturn(Optional.empty());
        when(conversationSeqService.nextSeq("single:userA:userB")).thenReturn(1001L);

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
    }

    @Test
    void duplicateClientMsgIdShouldReturnAcceptedResultWithServerMsgIdAndSeq() {
        DeliveryResult existing = DeliveryResult.accepted("s-1", 1001L);
        when(idempotencyService.findExisting("userA", "single:userA:userB", "c-1"))
                .thenReturn(Optional.of(existing));

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
        verifyNoInteractions(ingressEventPublisher, conversationSeqService);
    }

    @Test
    void ackShouldApplyToStoreAndCancelPendingWhenRead() {
        DeliveryAck ack = new DeliveryAck();
        ack.setUserId("userB");
        ack.setServerMsgId("s-1");
        ack.setAckType("READ");
        
        DeliveryResult expectedResult = new DeliveryResult();
        expectedResult.setSuccess(true);
        when(storeService.applyAck(ack)).thenReturn(expectedResult);

        DeliveryResult result = service.ack(ack);

        assertTrue(result.isSuccess());
        verify(storeService).applyAck(ack);
        verify(messagePushService).cancelPending("s-1", "userB");
    }

    @Test
    void acceptedResultFactoryShouldExposeServerMsgIdAndConversationSeq() {
        DeliveryResult result = DeliveryResult.accepted("s-1", 1001L);

        assertTrue(result.isSuccess());
        assertEquals("ACCEPTED", result.getStatus());
        assertEquals("s-1", result.getServerMsgId());
        assertEquals(1001L, result.getConversationSeq());
        assertEquals(DeliveryState.PERSISTED, result.getState());
    }
}
