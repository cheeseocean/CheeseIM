package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
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

class OnlineDispatchRpcImplTest {

    @Test
    void dispatchShouldFanoutToActiveUserConnectionsWhenTargetsAreOmitted() {
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

        OnlineDispatchRpcImpl service = new OnlineDispatchRpcImpl(connectionManager);

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        req.setPayload(payload("srv-1", "hello"));

        var resp = service.dispatchMessage(req);

        assertEquals(1, resp.getResults().size());
        assertEquals("conn-1", resp.getResults().get(0).getConnectionId());
        assertEquals(true, resp.getResults().get(0).isSuccess());

        TextWebSocketFrame outbound = activeChannel.readOutbound();
        assertNotNull(outbound);
        assertFalse(outbound.text().isBlank());
    }

    @Test
    void dispatchShouldReturnFailureWhenRequestedConnectionIsMissing() {
        ConnectionManager connectionManager = new ConnectionManager();
        ReflectionTestUtils.setField(connectionManager, "objectMapper", new ObjectMapper());

        OnlineDispatchRpcImpl service = new OnlineDispatchRpcImpl(connectionManager);

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        req.setConnectionIds(List.of("missing-conn"));
        req.setPayload(payload("srv-2", "hello"));

        var resp = service.dispatchMessage(req);

        assertEquals(1, resp.getResults().size());
        assertEquals("missing-conn", resp.getResults().get(0).getConnectionId());
        assertEquals(false, resp.getResults().get(0).isSuccess());
        assertEquals("CONNECTION_NOT_FOUND", resp.getResults().get(0).getCode());
    }

    private static DispatchPayload payload(String serverMsgId, String content) {
        DispatchPayload payload = new DispatchPayload();
        payload.setServerMsgId(serverMsgId);
        payload.setConversationId("single:userA:userB");
        payload.setSeq(1L);
        payload.setContentType(101);
        payload.setContent(content);
        payload.setSendTime(System.currentTimeMillis());
        return payload;
    }
}
