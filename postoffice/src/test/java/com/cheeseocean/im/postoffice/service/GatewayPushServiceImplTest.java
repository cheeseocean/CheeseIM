package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.dto.RouteSnapshot;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayPushServiceImplTest {

    @Test
    void pushToUserShouldReturnPerDeviceResults() {
        OnlineRouteService onlineRouteService = mock(OnlineRouteService.class);

        RouteSnapshot onlineRoute = new RouteSnapshot();
        onlineRoute.setUserId("userB");
        onlineRoute.setDeviceId("ios-1");
        onlineRoute.setGatewayNode("gateway-a");

        RouteSnapshot staleRoute = new RouteSnapshot();
        staleRoute.setUserId("userB");
        staleRoute.setDeviceId("android-1");
        staleRoute.setGatewayNode("gateway-a");

        when(onlineRouteService.findByUser("userB")).thenReturn(List.of(onlineRoute, staleRoute));

        ConnectionManager connectionManager = new ConnectionManager();
        ReflectionTestUtils.setField(connectionManager, "objectMapper", new ObjectMapper());

        EmbeddedChannel activeChannel = new EmbeddedChannel();
        UserConnection activeConnection = new UserConnection("conn-1", "userB", 1, activeChannel);
        activeConnection.setAuthenticated("token");

        @SuppressWarnings("unchecked")
        Map<String, UserConnection> connectionMap =
                (Map<String, UserConnection>) ReflectionTestUtils.getField(connectionManager, "connectionMap");
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> userConnectionMap =
                (Map<String, Set<String>>) ReflectionTestUtils.getField(connectionManager, "userConnectionMap");
        connectionMap.put("conn-1", activeConnection);
        userConnectionMap.put("userB", Set.of("conn-1"));

        GatewayPushServiceImpl service = new GatewayPushServiceImpl(connectionManager, onlineRouteService);

        MessageProto message = new MessageProto();
        message.setServerMsgId("srv-1");
        message.setSenderId("userA");
        message.setReceiverId("userB");
        message.setContent("hello");

        GatewayPushResult result = service.pushToUser("userB", message);

        assertTrue(result.isRouteFound());
        assertEquals(List.of("ios-1"), result.getDeliveredDeviceIds());
        assertEquals(List.of("android-1"), result.getFailedDeviceIds());

        TextWebSocketFrame outbound = activeChannel.readOutbound();
        assertNotNull(outbound);
        assertFalse(outbound.text().isBlank());
    }
}
