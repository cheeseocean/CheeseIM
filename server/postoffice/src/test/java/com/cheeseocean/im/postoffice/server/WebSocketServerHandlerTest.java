package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.common.core.enums.ConnectionState;
import com.cheeseocean.im.common.api.dto.message.ReadReceiptPayload;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketServerHandlerTest {

    @Test
    void channelReadShouldDispatchByCommandTypeAfterDecoding() throws Exception {
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

        WebSocketServerHandler serverHandler = new WebSocketServerHandler();
        ReflectionTestUtils.setField(serverHandler, "connectionManager", connectionManager);
        ReflectionTestUtils.setField(serverHandler, "messageHandlerFactory", factory);
        ReflectionTestUtils.setField(serverHandler, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());

        WSMessage wsMessage = new WSMessage(WSMessageType.WS_SEND_MSG_REQ, "op-send-1",
                java.util.Map.of("clientMsgID", "client-1", "recvID", "receiver-1", "content", "hello", "contentType", 101, "sessionType", 1));
        serverHandler.channelRead0(ctx, new TextWebSocketFrame(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(wsMessage)));

        verify(factory).getHandler(CommandType.CHAT_SEND);
        verify(handler).handle(any(UserConnection.class), any(ClientEnvelope.class));
    }

    @Test
    void channelReadShouldDispatchReceiptAndPreserveLegacyReceiptFields() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory factory = mock(MessageHandlerFactory.class);
        MessageHandler handler = mock(MessageHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(connectionManager.getConnectionByChannel(channel)).thenReturn(authenticatedConnection());
        when(factory.getHandler(CommandType.READ_RECEIPT)).thenReturn(handler);
        when(handler.handle(any(UserConnection.class), any(ClientEnvelope.class))).thenReturn(MessageHandler.HandleResult.success());

        WebSocketServerHandler serverHandler = new WebSocketServerHandler();
        ReflectionTestUtils.setField(serverHandler, "connectionManager", connectionManager);
        ReflectionTestUtils.setField(serverHandler, "messageHandlerFactory", factory);
        ReflectionTestUtils.setField(serverHandler, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());

        WSMessage wsMessage = new WSMessage(WSMessageType.WS_MSG_READ_NOTIFY, "op-read-1",
                java.util.Map.of(
                        "receiptType", "DELIVERED",
                        "conversationId", "single:userA:userB",
                        "serverMsgId", "msg-77",
                        "receiptTime", 1710000000003L,
                        "seq", 19L
                ));
        serverHandler.channelRead0(ctx, new TextWebSocketFrame(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(wsMessage)));

        verify(factory).getHandler(CommandType.READ_RECEIPT);
        ArgumentCaptor<ClientEnvelope> envelopeCaptor = ArgumentCaptor.forClass(ClientEnvelope.class);
        verify(handler).handle(any(UserConnection.class), envelopeCaptor.capture());
        ReadReceiptPayload payload = assertInstanceOf(ReadReceiptPayload.class, envelopeCaptor.getValue().getBody());
        assertEquals("msg-77", payload.getServerMsgId());
        assertEquals(1710000000003L, payload.getReceiptTime());
        assertEquals("single:userA:userB", payload.getConversationId());
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
}
