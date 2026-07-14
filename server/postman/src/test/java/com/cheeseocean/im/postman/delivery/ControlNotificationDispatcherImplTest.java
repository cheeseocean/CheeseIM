package com.cheeseocean.im.postman.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.NodeDeliveryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControlNotificationDispatcherImplTest {

    @Test
    void shouldQueueControlEnvelopeToRouteGatewayNode() {
        OnlineRouteQueryService routeQuery = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        NodeDeliveryService nodeDelivery = mock(NodeDeliveryService.class);
        when(routeQuery.findByUser("user-1")).thenReturn(List.of(route("node-a", "conn-a")));
        when(nodeDelivery.deliver(eq("node-a"), any(DispatchMessageReq.class))).thenReturn(true);

        boolean accepted = dispatcher(routeQuery, onlineDispatcher, nodeDelivery).dispatch(request());

        assertTrue(accepted);
        verify(nodeDelivery).deliver(eq("node-a"), any(DispatchMessageReq.class));
        verify(onlineDispatcher, never()).dispatchMessage(any());
    }

    @Test
    void shouldUseDirectDispatcherForLegacyRouteWithoutGatewayNode() {
        OnlineRouteQueryService routeQuery = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        when(routeQuery.findByUser("user-1")).thenReturn(List.of(route(null, "conn-a")));
        DispatchMessageResp response = new DispatchMessageResp();
        response.setResults(List.of(new DispatchResult("conn-a", true, "OK", "accepted")));
        when(onlineDispatcher.dispatchMessage(any())).thenReturn(response);

        boolean accepted = dispatcher(routeQuery, onlineDispatcher, null).dispatch(request());

        assertTrue(accepted);
        verify(onlineDispatcher).dispatchMessage(any(DispatchMessageReq.class));
    }

    @Test
    void shouldNotAttemptOfflineFallbackWhenUserHasNoRoute() {
        OnlineRouteQueryService routeQuery = mock(OnlineRouteQueryService.class);
        OnlineDispatcher onlineDispatcher = mock(OnlineDispatcher.class);
        when(routeQuery.findByUser("user-1")).thenReturn(List.of());

        assertFalse(dispatcher(routeQuery, onlineDispatcher, null).dispatch(request()));

        verify(onlineDispatcher, never()).dispatchMessage(any());
    }

    private static ControlNotificationDispatcherImpl dispatcher(OnlineRouteQueryService routeQuery,
                                                                 OnlineDispatcher onlineDispatcher,
                                                                 NodeDeliveryService nodeDelivery) {
        ControlNotificationDispatcherImpl dispatcher = new ControlNotificationDispatcherImpl();
        ReflectionTestUtils.setField(dispatcher, "onlineRouteQueryService", routeQuery);
        ReflectionTestUtils.setField(dispatcher, "onlineDispatcher", onlineDispatcher);
        ReflectionTestUtils.setField(dispatcher, "nodeDeliveryService", nodeDelivery);
        return dispatcher;
    }

    private static ControlNotificationReq request() {
        ControlNotificationReq request = new ControlNotificationReq();
        request.setUserId("user-1");
        request.setDeliveryId("read:s:user-1:user-2:user-1:10");
        request.setEnvelope(ServerEnvelope.error("request-1", 400, "ignored"));
        return request;
    }

    private static RouteSnapshot route(String gatewayNode, String connectionId) {
        RouteSnapshot route = new RouteSnapshot();
        route.setUserId("user-1");
        route.setGatewayNode(gatewayNode);
        route.setConnectionId(connectionId);
        return route;
    }
}
