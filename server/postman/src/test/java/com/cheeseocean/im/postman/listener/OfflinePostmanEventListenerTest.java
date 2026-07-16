package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.push.PushResult;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.protocol.ProtoOfflinePushEventMapper;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.postman.service.impl.MessagePushServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflinePostmanEventListenerTest {

    @Test
    void offlinePushListenerShouldPropagateMalformedPayloadToQueueErrorHandler() {
        OnlineRouteQueryService onlineRouteQueryService = mock(OnlineRouteQueryService.class);
        MessagePushServiceImpl messagePushService = mock(MessagePushServiceImpl.class);
        OfflinePushEventListener listener = listener(messagePushService, onlineRouteQueryService);

        assertThrows(IllegalArgumentException.class, () -> listener.onMessage(new byte[]{(byte) 0x80}));

        verify(onlineRouteQueryService, never()).findByUser(any());
        verify(messagePushService, never()).pushOffline(any(OfflinePushEvent.class));
    }

    @Test
    void offlinePushListenerShouldPropagateRetryableRouteFailure() throws Exception {
        OnlineRouteQueryService onlineRouteQueryService = mock(OnlineRouteQueryService.class);
        when(onlineRouteQueryService.findByUser("userB"))
                .thenThrow(new IllegalStateException("route unavailable"));
        MessagePushServiceImpl messagePushService = mock(MessagePushServiceImpl.class);
        OfflinePushEventListener listener = listener(messagePushService, onlineRouteQueryService);
        byte[] payload = ProtoOfflinePushEventMapper.toProto(event()).toByteArray();

        assertThrows(IllegalStateException.class, () -> listener.onMessage(payload));
        verify(messagePushService, never()).pushOffline(any(OfflinePushEvent.class));
    }

    @Test
    void offlinePushListenerShouldSkipVendorPushWhenUserCameBackOnline() throws Exception {
        OnlineRouteQueryService onlineRouteQueryService = mock(OnlineRouteQueryService.class);
        when(onlineRouteQueryService.findByUser("userB")).thenReturn(List.of(new RouteSnapshot()));

        MessagePushServiceImpl messagePushService = mock(MessagePushServiceImpl.class);
        OfflinePushEventListener listener = listener(messagePushService, onlineRouteQueryService);

        listener.onMessage(ProtoOfflinePushEventMapper.toProto(event()).toByteArray());

        verify(messagePushService, never()).pushOffline(any(OfflinePushEvent.class));
    }

    @Test
    void offlinePushListenerShouldTriggerVendorPushWhenUserIsStillOffline() throws Exception {
        OnlineRouteQueryService onlineRouteQueryService = mock(OnlineRouteQueryService.class);
        when(onlineRouteQueryService.findByUser("userB")).thenReturn(List.of());

        MessagePushServiceImpl messagePushService = mock(MessagePushServiceImpl.class);
        when(messagePushService.pushOffline(any(OfflinePushEvent.class))).thenReturn(PushResult.success("userB", "offline-push"));

        OfflinePushEventListener listener = listener(messagePushService, onlineRouteQueryService);
        listener.onMessage(ProtoOfflinePushEventMapper.toProto(event()).toByteArray());

        verify(messagePushService).pushOffline(any(OfflinePushEvent.class));
    }

    private static OfflinePushEvent event() {
        OfflinePushEvent event = new OfflinePushEvent();
        event.setUserId("userB");
        event.setConversationId("s:userA:userB");
        event.setSeq(11L);
        event.setServerMsgId("msg-1");
        event.setContent("hello");
        return event;
    }

    private static OfflinePushEventListener listener(MessagePushServiceImpl messagePushService,
                                                      OnlineRouteQueryService onlineRouteQueryService) {
        OfflinePushEventListener listener = new OfflinePushEventListener(messagePushService);
        ReflectionTestUtils.setField(listener, "onlineRouteQueryService", onlineRouteQueryService);
        return listener;
    }
}
