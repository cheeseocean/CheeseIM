package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoServerEnvelope;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.config.NodeIdentityProvider;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.dedup.DeliveryDedupStore;
import com.cheeseocean.im.postoffice.delivery.DeliveryWriteFinalizer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.timeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class OnlineDispatcherImplTest {

    @Test
    void dispatchShouldFanoutToActiveUserConnectionsWhenTargetsAreOmitted() throws Exception {
        ConnectionManager connectionManager = connectionManager();

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

        OnlineDispatcherImpl service = dispatcher(connectionManager);

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        req.setPayload(payload("srv-1", "hello"));

        var resp = service.dispatchMessage(req);

        assertEquals(1, resp.getResults().size());
        assertEquals("conn-1", resp.getResults().get(0).getConnectionId());
        assertFalse(resp.getResults().get(0).isSuccess());
        assertEquals("WRITE_PENDING", resp.getResults().get(0).getCode());

        BinaryWebSocketFrame outbound = activeChannel.readOutbound();
        assertNotNull(outbound);
        ProtoServerEnvelope envelope = ProtoServerEnvelope.parseFrom(ByteBufUtil.getBytes(outbound.content()));
        assertEquals(CommandType.CHAT_RECV.getCode(), envelope.getCommand());
        assertEquals("srv-1", envelope.getRequestId());
    }

    @Test
    void dispatchShouldWriteTcpFramesForTcpConnections() {
        ConnectionManager connectionManager = connectionManager();

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

        OnlineDispatcherImpl service = dispatcher(connectionManager);

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        req.setPayload(payload("srv-3", "hello-tcp"));

        var resp = service.dispatchMessage(req);

        assertEquals(1, resp.getResults().size());
        assertFalse(resp.getResults().get(0).isSuccess());
        assertEquals("WRITE_PENDING", resp.getResults().get(0).getCode());

        Object outbound = activeChannel.readOutbound();
        assertNotNull(outbound);
        assertEquals(ServerEnvelope.class, outbound.getClass());
        ServerEnvelope envelope = (ServerEnvelope) outbound;
        assertEquals(CommandType.CHAT_RECV, envelope.getCommand());
    }

    @Test
    void dispatchShouldReturnFailureWhenRequestedConnectionIsMissing() {
        ConnectionManager connectionManager = connectionManager();

        OnlineDispatcherImpl service = dispatcher(connectionManager);

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
        ConnectionManager connectionManager = connectionManager();

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

        OnlineDispatcherImpl service = dispatcher(connectionManager);

        DispatchPayload payload = payload("friend-evt-1", "refresh");
        payload.getMsg().getAttributes().put("notificationType", "friend_request_created");

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        req.setPayload(payload);

        var resp = service.dispatchMessage(req);

        assertEquals(1, resp.getResults().size());
        assertFalse(resp.getResults().get(0).isSuccess());
        assertEquals("WRITE_PENDING", resp.getResults().get(0).getCode());

        BinaryWebSocketFrame outbound = activeChannel.readOutbound();
        assertNotNull(outbound);
        ProtoServerEnvelope frame = ProtoServerEnvelope.parseFrom(ByteBufUtil.getBytes(outbound.content()));
        assertEquals(CommandType.CHAT_RECV.getCode(), frame.getCommand());
        assertEquals("friend_request_created", frame.getChatMessage().getAttributesMap().get("notificationType"));
    }

    @Test
    void failedSendShouldAbortClaimAndAllowRetry() {
        ConnectionManager manager = mock(ConnectionManager.class);
        EmbeddedChannel channel = new EmbeddedChannel();
        UserConnection connection = new UserConnection("conn-1", "userB", 1, channel);
        when(manager.getConnection("conn-1")).thenReturn(connection);
        DeliveryDedupStore.Claim first = DeliveryDedupStore.Claim.acquired("key", "token-1");
        DeliveryDedupStore.Claim retry = DeliveryDedupStore.Claim.acquired("key", "token-2");
        when(manager.claimDelivery("srv-1", "userB", "conn-1")).thenReturn(first, retry);
        when(manager.writeMessageToConnection(eq(connection), any(ServerEnvelope.class)))
                .thenReturn(
                        channel.newFailedFuture(new IllegalStateException("write failed")),
                        channel.newSucceededFuture());
        when(manager.commitDelivery(retry)).thenReturn(true);
        OnlineDispatcherImpl dispatcher = dispatcher(manager);
        DispatchMessageReq request = new DispatchMessageReq();
        request.setUserId("userB");
        request.setConnectionIds(List.of("conn-1"));
        request.setPayload(payload("srv-1", "hello"));

        var failed = dispatcher.dispatchMessage(request);
        var retried = dispatcher.dispatchMessage(request);

        assertEquals("WRITE_PENDING", failed.getResults().get(0).getCode());
        assertEquals("WRITE_PENDING", retried.getResults().get(0).getCode());
        verify(manager, timeout(1_000)).abortDelivery(first);
        verify(manager, timeout(1_000)).commitDelivery(retry);
    }

    private static ConnectionManager connectionManager() {
        return new ConnectionManager(emptyProvider(), emptyProvider(), new NodeIdentityProvider("test-node"),
                new com.cheeseocean.im.postoffice.config.ServerProperties(), emptyProvider(), emptyProvider());
    }

    private static OnlineDispatcherImpl dispatcher(ConnectionManager connectionManager) {
        ServerProperties properties = new ServerProperties();
        return new OnlineDispatcherImpl(
                connectionManager,
                new DeliveryWriteFinalizer(connectionManager, properties),
                properties);
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return null;
            }

            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }
        };
    }

    private static DispatchPayload payload(String serverMsgId, String content) {
        DispatchPayload payload = new DispatchPayload();
        com.cheeseocean.im.common.api.dto.message.Message message = new com.cheeseocean.im.common.api.dto.message.Message();
        message.setServerMsgId(serverMsgId);
        message.setContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        message.setContentType(com.cheeseocean.im.common.api.enums.ContentType.TEXT);
        message.setChatType(com.cheeseocean.im.common.api.enums.ChatType.PRIVATE);
        message.setSeq(1L);
        message.setSendTime(System.currentTimeMillis());
        message.setAttributes(new java.util.HashMap<>());
        payload.setMsg(message);
        return payload;
    }
}
