package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.push.OfflinePushReq;
import com.cheeseocean.im.common.api.dto.push.PushResult;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.core.enums.DeliveryState;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.postman.entity.OfflinePushResult;
import com.cheeseocean.im.postman.entity.PushAttempt;
import com.cheeseocean.im.postman.service.impl.MessagePushServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessagePostmanServiceImplTest {

    @Test
    void shouldNotPushWhenAnotherDeviceAlreadyConfirmedReceipt() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        PushDecisionService decisionService = new PushDecisionService();
        MessagePushServiceImpl service = new MessagePushServiceImpl(offlinePushService, decisionService);

        OfflinePushReq message = new OfflinePushReq();
        message.setServerMsgId("s-1");
        message.setUserId("userB");
        service.recordDeliveryState("s-1", "userB", DeliveryState.ONLINE_CONFIRMED);

        PushResult result = service.pushOffline(message);

        assertFalse(result.isSuccess());
        verifyNoInteractions(offlinePushService);
    }

    @Test
    void sameServerMsgIdShouldNotCreateDuplicatePushAttempt() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), eq("userB"))).thenReturn(OfflinePushResult.success(java.util.List.of("userB")));

        MessagePushServiceImpl service = new MessagePushServiceImpl(offlinePushService, new PushDecisionService());

        OfflinePushReq message = new OfflinePushReq();
        message.setServerMsgId("s-2");
        message.setUserId("userB");

        PushResult first = service.pushOffline(message);
        PushResult second = service.pushOffline(message);

        assertTrue(first.isSuccess());
        assertFalse(second.isSuccess());
        verify(offlinePushService).pushMessageToUser(any(), eq("userB"));
    }

    @Test
    void cancelPendingShouldMarkAttemptCancelled() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), eq("userB"))).thenReturn(OfflinePushResult.success(java.util.List.of("userB")));

        MessagePushServiceImpl service = new MessagePushServiceImpl(offlinePushService, new PushDecisionService());

        OfflinePushReq message = new OfflinePushReq();
        message.setServerMsgId("s-3");
        message.setUserId("userB");
        service.pushOffline(message);

        service.cancelPending("s-3", "userB");

        PushAttempt attempt = service.findAttempt("s-3", "userB").orElseThrow();
        assertTrue(attempt.isCancelled());
    }

    @Test
    void offlinePushEventShouldMapIntoExistingPushFlow() {
        OfflinePushService offlinePushService = mock(OfflinePushService.class);
        when(offlinePushService.pushMessageToUser(any(), eq("userB"))).thenReturn(OfflinePushResult.success(java.util.List.of("userB")));

        MessagePushServiceImpl service = new MessagePushServiceImpl(offlinePushService, new PushDecisionService());

        OfflinePushEvent task = new OfflinePushEvent();
        task.setServerMsgId("s-4");
        task.setConversationId("single:userA:userB");
        task.setSeq(17L);
        task.setUserId("userB");
        task.setSenderId("system");
        task.setSessionType(SessionType.NOTIFICATION.getCode());
        task.setContentType(ContentType.SYSTEM_NOTIFY.getCode());
        task.setNotification(true);
        task.setContent("ping");

        PushResult result = service.pushOffline(task);

        assertTrue(result.isSuccess());
        var messageCaptor = forClass(com.cheeseocean.im.common.api.dto.message.Message.class);
        verify(offlinePushService).pushMessageToUser(messageCaptor.capture(), eq("userB"));
        assertTrue(Boolean.TRUE.equals(messageCaptor.getValue().getOptions().get("notification")));
    }
}
