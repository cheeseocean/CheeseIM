package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.receipt.ReceiptAckReq;
import com.cheeseocean.im.common.api.rpc.ReceiptAckRpc;
import com.cheeseocean.im.common.dto.ReceiptEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReceiptEventListenerTest {

    @Test
    void onMessageShouldTranslateDeliveredReceiptIntoReceiptAckReq() throws Exception {
        ReceiptAckRpc receiptAckRpc = mock(ReceiptAckRpc.class);
        ReceiptEventListener listener = new ReceiptEventListener(new ObjectMapper(), receiptAckRpc);

        listener.onMessage(new ObjectMapper().writeValueAsString(
                ReceiptEvent.delivered("userB", "single:userA:userB", "msg-1", 11L, "ios-1")));

        ArgumentCaptor<ReceiptAckReq> ackCaptor = ArgumentCaptor.forClass(ReceiptAckReq.class);
        verify(receiptAckRpc).apply(ackCaptor.capture());
        ReceiptAckReq ack = ackCaptor.getValue();
        assertEquals("RECEIVED", ack.getAckType());
        assertEquals("msg-1", ack.getServerMsgId());
        assertEquals("single:userA:userB", ack.getConversationId());
        assertEquals("userB", ack.getUserId());
        assertEquals("ios-1", ack.getDeviceId());
        assertEquals(11L, ack.getSeq());
    }

    @Test
    void onMessageShouldTranslateReadCursorReceiptIntoReadAckReq() throws Exception {
        ReceiptAckRpc receiptAckRpc = mock(ReceiptAckRpc.class);
        ReceiptEventListener listener = new ReceiptEventListener(new ObjectMapper(), receiptAckRpc);

        listener.onMessage(new ObjectMapper().writeValueAsString(
                ReceiptEvent.readCursor("userB", "single:userA:userB", 19L, "ios-1")));

        ArgumentCaptor<ReceiptAckReq> ackCaptor = ArgumentCaptor.forClass(ReceiptAckReq.class);
        verify(receiptAckRpc).apply(ackCaptor.capture());
        ReceiptAckReq ack = ackCaptor.getValue();
        assertEquals("READ", ack.getAckType());
        assertEquals("single:userA:userB", ack.getConversationId());
        assertEquals("userB", ack.getUserId());
        assertEquals("ios-1", ack.getDeviceId());
        assertEquals(19L, ack.getSeq());
    }
}
