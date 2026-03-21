package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.ReceiptEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptEventListenerTest {

    @Test
    void onMessageShouldTranslateDeliveredReceiptIntoLegacyAck() throws Exception {
        MessageStoreService messageStoreService = mock(MessageStoreService.class);
        when(messageStoreService.applyAck(any(DeliveryAck.class))).thenReturn(new DeliveryResult());
        ReceiptEventListener listener = new ReceiptEventListener(new ObjectMapper(), messageStoreService);

        listener.onMessage(new ObjectMapper().writeValueAsString(
                ReceiptEvent.delivered("userB", "single:userA:userB", "msg-1", 11L, "ios-1")));

        ArgumentCaptor<DeliveryAck> ackCaptor = ArgumentCaptor.forClass(DeliveryAck.class);
        verify(messageStoreService).applyAck(ackCaptor.capture());
        DeliveryAck ack = ackCaptor.getValue();
        assertEquals("RECEIVED", ack.getAckType());
        assertEquals("msg-1", ack.getServerMsgId());
        assertEquals("single:userA:userB", ack.getConversationId());
        assertEquals("userB", ack.getUserId());
        assertEquals("ios-1", ack.getDeviceId());
        assertEquals(11L, ack.getSeq());
    }

    @Test
    void onMessageShouldTranslateReadCursorReceiptIntoLegacyReadAck() throws Exception {
        MessageStoreService messageStoreService = mock(MessageStoreService.class);
        when(messageStoreService.applyAck(any(DeliveryAck.class))).thenReturn(new DeliveryResult());
        ReceiptEventListener listener = new ReceiptEventListener(new ObjectMapper(), messageStoreService);

        listener.onMessage(new ObjectMapper().writeValueAsString(
                ReceiptEvent.readCursor("userB", "single:userA:userB", 19L, "ios-1")));

        ArgumentCaptor<DeliveryAck> ackCaptor = ArgumentCaptor.forClass(DeliveryAck.class);
        verify(messageStoreService).applyAck(ackCaptor.capture());
        DeliveryAck ack = ackCaptor.getValue();
        assertEquals("READ", ack.getAckType());
        assertEquals("single:userA:userB", ack.getConversationId());
        assertEquals("userB", ack.getUserId());
        assertEquals("ios-1", ack.getDeviceId());
        assertEquals(19L, ack.getSeq());
    }
}
