package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.CheeseMessage;
import com.fasterxml.jackson.core.type.TypeReference;
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

class OnlineDispatchRpcImplTest {

    @Test
    void dispatchShouldFanoutToActiveUserConnectionsWhenTargetsAreOmitted() throws Exception {
        ConnectionManager connectionManager = new ConnectionManager();
        ReflectionTestUtils.setField(connectionManager, "objectMapper", new ObjectMapper());

        EmbeddedChannel activeChannel = new EmbeddedChannel();
        UserConnection activeConnection = new UserConnection("conn-1", "userB", 1, activeChannel);
        activeConnection.setAuthenticated("token");
        activeConnection.setProtocol("WebSocket");

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
        Map<String, Object> envelope = new ObjectMapper().readValue(
                outbound.text(),
                new TypeReference<Map<String, Object>>() {}
        );
        assertEquals(CommandType.CHAT_RECV.getCode(), envelope.get("command"));
        assertEquals("srv-1", envelope.get("requestId"));
    }

    @Test
    void dispatchShouldWriteTcpFramesForTcpConnections() {
        ConnectionManager connectionManager = new ConnectionManager();
        ReflectionTestUtils.setField(connectionManager, "objectMapper", new ObjectMapper());

        EmbeddedChannel activeChannel = new EmbeddedChannel();
        UserConnection activeConnection = new UserConnection("conn-2", "userB", 2, activeChannel);
        activeConnection.setAuthenticated("token");
        activeConnection.setProtocol("TCP");

        @SuppressWarnings("unchecked")
        Map<String, UserConnection> connectionMap =
                (Map<String, UserConnection>) ReflectionTestUtils.getField(connectionManager, "connectionMap");
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> userConnectionMap =
                (Map<String, Set<String>>) ReflectionTestUtils.getField(connectionManager, "userConnectionMap");
        connectionMap.put("conn-2", activeConnection);
        userConnectionMap.put("userB", Set.of("conn-2"));

        OnlineDispatchRpcImpl service = new OnlineDispatchRpcImpl(connectionManager);

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        req.setPayload(payload("srv-3", "hello-tcp"));

        var resp = service.dispatchMessage(req);

        assertEquals(1, resp.getResults().size());
        assertEquals(true, resp.getResults().get(0).isSuccess());

        Object outbound = activeChannel.readOutbound();
        assertNotNull(outbound);
        assertEquals(CheeseMessage.class, outbound.getClass());
        ServerEnvelope envelope = ((CheeseMessage) outbound).toServerEnvelope();
        assertEquals(CommandType.CHAT_RECV, envelope.getCommand());
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

    @Test
    void dispatchShouldUseFriendNotifyTypeForRelationshipNotifications() throws Exception {
        ConnectionManager connectionManager = new ConnectionManager();
        ObjectMapper objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(connectionManager, "objectMapper", objectMapper);

        EmbeddedChannel activeChannel = new EmbeddedChannel();
        UserConnection activeConnection = new UserConnection("conn-3", "userB", 1, activeChannel);
        activeConnection.setAuthenticated("token");
        activeConnection.setProtocol("WebSocket");

        @SuppressWarnings("unchecked")
        Map<String, UserConnection> connectionMap =
                (Map<String, UserConnection>) ReflectionTestUtils.getField(connectionManager, "connectionMap");
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> userConnectionMap =
                (Map<String, Set<String>>) ReflectionTestUtils.getField(connectionManager, "userConnectionMap");
        connectionMap.put("conn-3", activeConnection);
        userConnectionMap.put("userB", Set.of("conn-3"));

        OnlineDispatchRpcImpl service = new OnlineDispatchRpcImpl(connectionManager);

        DispatchPayload payload = payload("friend-evt-1", "refresh");
        payload.getExt().put("notificationType", "friend_request_created");

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        req.setPayload(payload);

        var resp = service.dispatchMessage(req);

        assertEquals(1, resp.getResults().size());
        assertTrue(resp.getResults().get(0).isSuccess());

        TextWebSocketFrame outbound = activeChannel.readOutbound();
        assertNotNull(outbound);
        @SuppressWarnings("unchecked")
        Map<String, Object> frame = objectMapper.readValue(outbound.text(), Map.class);
        assertEquals(CommandType.CHAT_RECV.getCode(), frame.get("command"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) frame.get("body");
        assertEquals("friend_request_created", ((Map<?, ?>) body.get("ext")).get("notificationType"));
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
