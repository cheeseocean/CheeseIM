package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.common.core.enums.ConnectionState;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebSocketServerHandlerTest {

    @Test
    void handshakeShouldRegisterPendingConnectionWithoutSendingUncorrelatedFrame() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5147));
        when(connectionManager.registerPendingConnection(any(UserConnection.class))).thenReturn(true);

        WebSocketServerHandler serverHandler = newHandler(connectionManager, mock(MessageHandlerFactory.class));

        serverHandler.userEventTriggered(
                ctx,
                new WebSocketServerProtocolHandler.HandshakeComplete("/ws", new DefaultHttpHeaders(), null)
        );

        verify(connectionManager).registerPendingConnection(any(UserConnection.class));
        verify(ctx, never()).writeAndFlush(any());
    }

    @Test
    void connectEnvelopeShouldReturnCorrelatedConnectSuccessEnvelope() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory factory = mock(MessageHandlerFactory.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        UserConnection pendingConnection = pendingConnection();

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(connectionManager.getConnectionByChannel(channel)).thenReturn(pendingConnection);

        WebSocketServerHandler serverHandler = newHandler(connectionManager, factory);

        serverHandler.channelRead0(ctx, new TextWebSocketFrame(writeClientEnvelope(
                CommandType.CONNECT,
                "op-connect-1",
                Map.of()
        )));

        org.mockito.ArgumentCaptor<TextWebSocketFrame> frameCaptor =
                org.mockito.ArgumentCaptor.forClass(TextWebSocketFrame.class);
        verify(ctx).writeAndFlush(frameCaptor.capture());
        Map<String, Object> envelope = new ObjectMapper().readValue(
                frameCaptor.getValue().text(),
                new TypeReference<Map<String, Object>>() {}
        );
        assertEquals(CommandType.CONNECT.getCode(), envelope.get("command"));
        assertEquals("op-connect-1", envelope.get("requestId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) envelope.get("body");
        assertNotNull(body.get("connId"));
        verifyNoInteractions(factory);
    }

    @Test
    void channelReadShouldDispatchByCommandTypeAfterDecodingEnvelopeJson() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory factory = mock(MessageHandlerFactory.class);
        MessageHandler handler = mock(MessageHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(connectionManager.getConnectionByChannel(channel)).thenReturn(authenticatedConnection());
        when(factory.getHandler(CommandType.CHAT_SEND)).thenReturn(handler);
        when(handler.handle(any(UserConnection.class), any(ClientEnvelope.class))).thenReturn(MessageHandler.HandleResult.success());

        WebSocketServerHandler serverHandler = newHandler(connectionManager, factory);

        serverHandler.channelRead0(ctx, new TextWebSocketFrame(writeClientEnvelope(
                CommandType.CHAT_SEND,
                "op-send-1",
                Map.of(
                        "clientMsgID", "client-1",
                        "recvID", "receiver-1",
                        "content", "hello",
                        "contentType", 101,
                        "sessionType", 1
                )
        )));

        verify(factory).getHandler(CommandType.CHAT_SEND);
        verify(handler).handle(any(UserConnection.class), any(ClientEnvelope.class));
    }

    @Test
    void channelReadShouldRejectLegacyWsMessageJsonWithEnvelopeErrorResponse() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory factory = mock(MessageHandlerFactory.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));

        WebSocketServerHandler serverHandler = newHandler(connectionManager, factory);

        serverHandler.channelRead0(ctx, new TextWebSocketFrame(new ObjectMapper().writeValueAsString(Map.of(
                "msgType", 6007,
                "operationID", "op-read-1",
                "data", Map.of(
                        "receiptType", "DELIVERED",
                        "conversationId", "single:userA:userB",
                        "serverMsgId", "msg-77",
                        "receiptTime", 1710000000003L,
                        "seq", 19L
                )
        ))));

        org.mockito.ArgumentCaptor<TextWebSocketFrame> frameCaptor =
                org.mockito.ArgumentCaptor.forClass(TextWebSocketFrame.class);
        verify(ctx).writeAndFlush(frameCaptor.capture());
        Map<String, Object> envelope = new ObjectMapper().readValue(
                frameCaptor.getValue().text(),
                new TypeReference<Map<String, Object>>() {}
        );
        assertEquals(CommandType.ERROR.getCode(), envelope.get("command"));
        assertEquals("system", envelope.get("requestId"));
        verifyNoInteractions(factory);
    }

    private static UserConnection authenticatedConnection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("conn-1");
        connection.setUserID("user-1");
        connection.setAuthenticated(true);
        ConnectionContext context = new ConnectionContext();
        context.setConnId("conn-1");
        context.setUserId("user-1");
        context.setSessionId("session-1");
        context.setState(ConnectionState.AUTHENTICATED);
        connection.setContext(context);
        return connection;
    }

    private static UserConnection pendingConnection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("conn-pending-1");
        connection.setAuthenticated(false);
        ConnectionContext context = new ConnectionContext();
        context.setConnId("conn-pending-1");
        context.setState(ConnectionState.PENDING);
        connection.setContext(context);
        return connection;
    }

    private static WebSocketServerHandler newHandler(ConnectionManager connectionManager,
                                                     MessageHandlerFactory factory) {
        WebSocketServerHandler serverHandler = new WebSocketServerHandler();
        ReflectionTestUtils.setField(serverHandler, "connectionManager", connectionManager);
        ReflectionTestUtils.setField(serverHandler, "messageHandlerFactory", factory);
        ReflectionTestUtils.setField(serverHandler, "objectMapper", new ObjectMapper());
        return serverHandler;
    }

    private static String writeClientEnvelope(CommandType command,
                                              String requestId,
                                              Map<String, Object> body) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "command", command.getCode(),
                "requestId", requestId,
                "body", body
        ));
    }
}
