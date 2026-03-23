package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.event.FriendRelationEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryRpc;
import com.cheeseocean.im.common.api.rpc.OnlineDispatchRpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendRelationEventListenerTest {

    @Test
    void listenerShouldDispatchFriendNotificationWhenRecipientIsOnline() {
        OnlineRouteQueryRpc routeQueryRpc = mock(OnlineRouteQueryRpc.class);
        OnlineDispatchRpc onlineDispatchRpc = mock(OnlineDispatchRpc.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of(route("userB", "ios-1")));
        DispatchMessageResp dispatchResp = new DispatchMessageResp();
        dispatchResp.setResults(List.of(new DispatchResult("conn-1", true, "OK", "delivered")));
        when(onlineDispatchRpc.dispatchMessage(any())).thenReturn(dispatchResp);

        FriendRelationEventListener listener = new FriendRelationEventListener(new ObjectMapper(), routeQueryRpc, onlineDispatchRpc);
        listener.handle(event());

        var captor = org.mockito.ArgumentCaptor.forClass(com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq.class);
        verify(onlineDispatchRpc).dispatchMessage(captor.capture());
        assertEquals("userB", captor.getValue().getUserId());
        assertEquals("friend_request_created", captor.getValue().getPayload().getExt().get("notificationType"));
    }

    @Test
    void listenerShouldSkipDispatchWhenRecipientIsOffline() {
        OnlineRouteQueryRpc routeQueryRpc = mock(OnlineRouteQueryRpc.class);
        OnlineDispatchRpc onlineDispatchRpc = mock(OnlineDispatchRpc.class);

        when(routeQueryRpc.findByUser("userB")).thenReturn(List.of());

        FriendRelationEventListener listener = new FriendRelationEventListener(new ObjectMapper(), routeQueryRpc, onlineDispatchRpc);
        listener.handle(event());

        verify(onlineDispatchRpc, never()).dispatchMessage(any());
    }

    private static FriendRelationEvent event() {
        FriendRelationEvent event = new FriendRelationEvent();
        event.setRecipientUserId("userB");
        event.setActorUserId("userA");
        event.setPeerUserId("userA");
        event.setEventType("friend_request_created");
        event.setOccurredAt(System.currentTimeMillis());
        return event;
    }

    private static RouteSnapshot route(String userId, String deviceId) {
        RouteSnapshot route = new RouteSnapshot();
        route.setUserId(userId);
        route.setDeviceId(deviceId);
        return route;
    }
}
