package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import com.cheeseocean.im.postoffice.client.ProtocolContractFixtures;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.cheeseocean.im.common.core.enums.ConnectionState;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TcpServerHandlerTest {

    @Test
    void channelActiveShouldRegisterPendingConnection() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory messageHandlerFactory = mock(MessageHandlerFactory.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(connectionManager.registerPendingConnection(org.mockito.ArgumentMatchers.any(UserConnection.class))).thenReturn(true);

        TcpServerHandler handler = new TcpServerHandler();
        ReflectionTestUtils.setField(handler, "connectionManager", connectionManager);
        ReflectionTestUtils.setField(handler, "messageHandlerFactory", messageHandlerFactory);

        handler.channelActive(ctx);

        ArgumentCaptor<UserConnection> captor = ArgumentCaptor.forClass(UserConnection.class);
        verify(connectionManager).registerPendingConnection(captor.capture());
        UserConnection connection = captor.getValue();
        assertNotNull(connection.getConnectionID());
        assertTrue(channel == connection.getChannel());
        assertTrue("TCP".equals(connection.getProtocol()));
    }

    @Test
    void cheeseServerHandlerShouldBeSharable() {
        assertTrue(TcpServerHandler.class.isAnnotationPresent(ChannelHandler.Sharable.class));
    }

    @Test
    void channelReadShouldDispatchByCommandTypeAfterDecoding() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory messageHandlerFactory = mock(MessageHandlerFactory.class);
        MessageHandler handler = mock(MessageHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(connectionManager.getConnectionByChannel(channel)).thenReturn(authenticatedConnection());
        when(messageHandlerFactory.getHandler(CommandType.CHAT_SEND)).thenReturn(handler);
        when(handler.handle(any(UserConnection.class), any(ClientEnvelope.class)))
                .thenReturn(MessageHandler.HandleResult.success(chatSendAck()));

        TcpServerHandler handlerUnderTest = new TcpServerHandler();
        ReflectionTestUtils.setField(handlerUnderTest, "connectionManager", connectionManager);
        ReflectionTestUtils.setField(handlerUnderTest, "messageHandlerFactory", messageHandlerFactory);

        handlerUnderTest.channelRead0(ctx, ProtocolContractFixtures.clientEnvelope(
                CommandType.CHAT_SEND,
                "op-send-00000001",
                Map.of(
                        "clientMsgID", "client-123",
                        "recvID", "receiver123",
                        "content", "Hello World!",
                        "contentType", 101,
                        "sessionType", 1
                )));

        verify(messageHandlerFactory).getHandler(CommandType.CHAT_SEND);
        verify(handler).handle(any(UserConnection.class), any(ClientEnvelope.class));
        verify(ctx).writeAndFlush(argThat(outbound ->
                outbound instanceof ServerEnvelope
                        && ((ServerEnvelope) outbound).getCommand() == CommandType.CHAT_SEND));
    }

    @Test
    void channelReadShouldRejectLegacyReceiptByCommandTypeAfterDecoding() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory messageHandlerFactory = mock(MessageHandlerFactory.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(connectionManager.getConnectionByChannel(channel)).thenReturn(authenticatedConnection());
        when(messageHandlerFactory.getHandler(null)).thenReturn(null);

        TcpServerHandler handlerUnderTest = new TcpServerHandler();
        ReflectionTestUtils.setField(handlerUnderTest, "connectionManager", connectionManager);
        ReflectionTestUtils.setField(handlerUnderTest, "messageHandlerFactory", messageHandlerFactory);

        handlerUnderTest.channelRead0(ctx, ProtocolContractFixtures.clientEnvelope(
                null,
                "op-read-1",
                "{\"receiptType\":\"READ_CURSOR\",\"conversationId\":\"single:userA:userB\",\"seq\":19}"
        ));

        verify(messageHandlerFactory).getHandler(null);
        verify(messageHandlerFactory, never()).getHandler(CommandType.CHAT_SEND);
        verify(ctx).writeAndFlush(any());
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

    private static ServerEnvelope chatSendAck() {
        ServerEnvelope envelope = new ServerEnvelope();
        envelope.setCommand(CommandType.CHAT_SEND);
        envelope.setRequestId("op-send-1");
        envelope.setBody(Map.of("serverMsgID", "server-1"));
        return envelope;
    }
}
