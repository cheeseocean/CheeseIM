package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.dto.OfflinePushTask;
import com.cheeseocean.im.common.dto.PushResult;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import com.cheeseocean.im.push.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflinePushTaskListenerTest {

    @Test
    void offlinePushListenerShouldSkipVendorPushWhenUserCameBackOnline() throws Exception {
        OnlineRouteService onlineRouteService = mock(OnlineRouteService.class);
        when(onlineRouteService.findByUser("userB")).thenReturn(List.of(new com.cheeseocean.im.common.dto.RouteSnapshot()));

        MessagePushServiceImpl messagePushService = mock(MessagePushServiceImpl.class);
        OfflinePushTaskListener listener = new OfflinePushTaskListener(new ObjectMapper(), messagePushService, onlineRouteService);

        listener.onMessage(new ObjectMapper().writeValueAsString(task()));

        verify(messagePushService, never()).pushOffline(any(OfflinePushTask.class));
    }

    @Test
    void offlinePushListenerShouldTriggerVendorPushWhenUserIsStillOffline() throws Exception {
        OnlineRouteService onlineRouteService = mock(OnlineRouteService.class);
        when(onlineRouteService.findByUser("userB")).thenReturn(List.of());

        MessagePushServiceImpl messagePushService = mock(MessagePushServiceImpl.class);
        when(messagePushService.pushOffline(any(OfflinePushTask.class))).thenReturn(PushResult.success("userB", "offline-push"));

        OfflinePushTaskListener listener = new OfflinePushTaskListener(new ObjectMapper(), messagePushService, onlineRouteService);
        listener.onMessage(new ObjectMapper().writeValueAsString(task()));

        verify(messagePushService).pushOffline(any(OfflinePushTask.class));
    }

    private static OfflinePushTask task() {
        OfflinePushTask task = new OfflinePushTask();
        task.setEventId("evt-1");
        task.setMessageId("msg-1");
        task.setConversationId("single:userA:userB");
        task.setConversationSeq(11L);
        task.setSenderId("userA");
        task.setReceiverId("userB");
        task.setSessionType(1);
        task.setContentType(101);
        task.setContent("hello");
        return task;
    }
}
