package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.GatewayPushService;
import com.cheeseocean.im.common.api.MessagePushService;
import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDeliveryAckFlowTest {

    @Test
    void laterReadEventShouldWinOverEarlierReceiveAck() {
        MessageStoreService storeService = mock(MessageStoreService.class);
        MessagePushService pushService = mock(MessagePushService.class);

        DeliveryResult delivered = new DeliveryResult();
        delivered.setSuccess(true);
        delivered.setServerMsgId("s-1");
        delivered.setStatus(DeliveryState.ONLINE_CONFIRMED.name());
        delivered.setState(DeliveryState.ONLINE_CONFIRMED);

        DeliveryResult read = new DeliveryResult();
        read.setSuccess(true);
        read.setServerMsgId("s-1");
        read.setStatus(DeliveryState.READ.name());
        read.setState(DeliveryState.READ);

        DeliveryAck receiveAck = DeliveryAck.receive("s-1", "single:userA:userB", "userB", "ios-1", 1L);
        DeliveryAck readAck = DeliveryAck.read("s-1", "single:userA:userB", "userB", "ios-1", 2L);
        when(storeService.applyAck(receiveAck)).thenReturn(delivered);
        when(storeService.applyAck(readAck)).thenReturn(read);

        MessageDeliveryServiceImpl service = new MessageDeliveryServiceImpl(
                mock(MessageIdempotencyService.class),
                new DeliveryStateMachine(),
                storeService,
                mock(GatewayPushService.class),
                pushService,
                mock(DeliveryCompensationService.class));

        service.ack(receiveAck);
        DeliveryResult result = service.ack(readAck);

        assertEquals(DeliveryState.READ, result.getState());
        verify(pushService).cancelPending("s-1", "userB");
    }

    @Test
    void recallShouldBeRejectedAfterReadWhenPolicyDisallowsIt() {
        MessageStoreService storeService = mock(MessageStoreService.class);
        MessagePushService pushService = mock(MessagePushService.class);

        DeliveryResult rejected = new DeliveryResult();
        rejected.setSuccess(false);
        rejected.setServerMsgId("s-2");
        rejected.setStatus("RECALL_REJECTED_AFTER_READ");
        rejected.setState(DeliveryState.FAILED_FINAL);

        DeliveryAck recallAck = DeliveryAck.recall("s-2", "single:userA:userB", "userA", "ios-1", 3L);
        when(storeService.applyAck(recallAck)).thenReturn(rejected);

        MessageDeliveryServiceImpl service = new MessageDeliveryServiceImpl(
                mock(MessageIdempotencyService.class),
                new DeliveryStateMachine(),
                storeService,
                mock(GatewayPushService.class),
                pushService,
                mock(DeliveryCompensationService.class));

        DeliveryResult result = service.ack(recallAck);

        assertFalse(result.isSuccess());
        assertEquals(DeliveryState.FAILED_FINAL, result.getState());
        assertEquals("RECALL_REJECTED_AFTER_READ", result.getStatus());
        verify(pushService).cancelPending("s-2", "userA");
    }
}
