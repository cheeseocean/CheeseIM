package com.cheeseocean.im.push.service;

import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.dto.PushResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.push.entity.OfflinePushResult;
import com.cheeseocean.im.push.entity.PushAttempt;
import com.cheeseocean.im.push.service.impl.MessagePushServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessagePushServiceImplTest {

    @Test
    void shouldNotPushWhenAnotherDeviceAlreadyConfirmedReceipt() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        PushDecisionService decisionService = new PushDecisionService();
        MessagePushServiceImpl service = new MessagePushServiceImpl(offlinePushService, decisionService);

        MessageProto message = new MessageProto();
        message.setServerMsgId("s-1");
        message.setReceiverId("userB");
        service.recordDeliveryState("s-1", "userB", DeliveryState.ONLINE_CONFIRMED);

        PushResult result = service.pushOffline("userB", message);

        assertFalse(result.isSuccess());
        verifyNoInteractions(offlinePushService);
    }

    @Test
    void sameServerMsgIdShouldNotCreateDuplicatePushAttempt() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), eq("userB"))).thenReturn(OfflinePushResult.success(java.util.List.of("userB")));

        MessagePushServiceImpl service = new MessagePushServiceImpl(offlinePushService, new PushDecisionService());

        MessageProto message = new MessageProto();
        message.setServerMsgId("s-2");
        message.setReceiverId("userB");

        PushResult first = service.pushOffline("userB", message);
        PushResult second = service.pushOffline("userB", message);

        assertTrue(first.isSuccess());
        assertFalse(second.isSuccess());
        verify(offlinePushService).pushMessageToUser(any(), eq("userB"));
    }

    @Test
    void cancelPendingShouldMarkAttemptCancelled() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), eq("userB"))).thenReturn(OfflinePushResult.success(java.util.List.of("userB")));

        MessagePushServiceImpl service = new MessagePushServiceImpl(offlinePushService, new PushDecisionService());

        MessageProto message = new MessageProto();
        message.setServerMsgId("s-3");
        message.setReceiverId("userB");
        service.pushOffline("userB", message);

        service.cancelPending("s-3", "userB");

        PushAttempt attempt = service.findAttempt("s-3", "userB").orElseThrow();
        assertTrue(attempt.isCancelled());
    }
}
