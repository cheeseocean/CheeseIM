package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.dto.push.PushResult;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.postman.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflinePostmanEventListenerTest {

    @Test
    void offlinePushListenerShouldSkipVendorPushWhenUserCameBackOnline() throws Exception {
        OnlineRouteQueryService onlineRouteQueryService = mock(OnlineRouteQueryService.class);
        when(onlineRouteQueryService.findByUser("userB")).thenReturn(List.of(new RouteSnapshot()));

        MessagePushServiceImpl messagePushService = mock(MessagePushServiceImpl.class);
        OfflinePushEventListener listener = new OfflinePushEventListener(new ObjectMapper(), messagePushService, onlineRouteQueryService);

        listener.onMessage(new ObjectMapper().writeValueAsString(event()));

        verify(messagePushService, never()).pushOffline(any(OfflinePushEvent.class));
    }

    @Test
    void offlinePushListenerShouldTriggerVendorPushWhenUserIsStillOffline() throws Exception {
        OnlineRouteQueryService onlineRouteQueryService = mock(OnlineRouteQueryService.class);
        when(onlineRouteQueryService.findByUser("userB")).thenReturn(List.of());

        MessagePushServiceImpl messagePushService = mock(MessagePushServiceImpl.class);
        when(messagePushService.pushOffline(any(OfflinePushEvent.class))).thenReturn(PushResult.success("userB", "offline-push"));

        OfflinePushEventListener listener = new OfflinePushEventListener(new ObjectMapper(), messagePushService, onlineRouteQueryService);
        listener.onMessage(new ObjectMapper().writeValueAsString(event()));

        verify(messagePushService).pushOffline(any(OfflinePushEvent.class));
    }

    private static OfflinePushEvent event() {
        OfflinePushEvent event = new OfflinePushEvent();
        event.setUserId("userB");
        event.setConversationId("single:userA:userB");
        event.setSeq(11L);
        event.setServerMsgId("msg-1");
        event.setContent("hello");
        return event;
    }
}
