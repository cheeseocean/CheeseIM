package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import com.cheeseocean.im.postoffice.protocol.CheeseMessage;
import com.cheeseocean.im.postoffice.client.ProtocolContractFixtures;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.cheeseocean.im.common.core.enums.ConnectionState;

import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheeseServerHandlerTest {

    @Test
    void channelActiveShouldRegisterPendingConnection() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory messageHandlerFactory = mock(MessageHandlerFactory.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(connectionManager.registerPendingConnection(org.mockito.ArgumentMatchers.any(UserConnection.class))).thenReturn(true);

        CheeseServerHandler handler = new CheeseServerHandler();
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
        assertTrue(CheeseServerHandler.class.isAnnotationPresent(ChannelHandler.Sharable.class));
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
                .thenReturn(MessageHandler.HandleResult.success());

        CheeseServerHandler handlerUnderTest = new CheeseServerHandler();
        ReflectionTestUtils.setField(handlerUnderTest, "connectionManager", connectionManager);
        ReflectionTestUtils.setField(handlerUnderTest, "messageHandlerFactory", messageHandlerFactory);

        CheeseMessage message = ProtocolContractFixtures.tcpSendRequest();
        handlerUnderTest.channelRead0(ctx, message);

        verify(messageHandlerFactory).getHandler(CommandType.CHAT_SEND);
        verify(handler).handle(any(UserConnection.class), any(ClientEnvelope.class));
    }

    @Test
    void channelReadShouldDispatchReceiptByCommandTypeAfterDecoding() throws Exception {
        ConnectionManager connectionManager = mock(ConnectionManager.class);
        MessageHandlerFactory messageHandlerFactory = mock(MessageHandlerFactory.class);
        MessageHandler handler = mock(MessageHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(connectionManager.getConnectionByChannel(channel)).thenReturn(authenticatedConnection());
        when(messageHandlerFactory.getHandler(CommandType.READ_RECEIPT)).thenReturn(handler);
        when(handler.handle(any(UserConnection.class), any(ClientEnvelope.class)))
                .thenReturn(MessageHandler.HandleResult.success());

        CheeseServerHandler handlerUnderTest = new CheeseServerHandler();
        ReflectionTestUtils.setField(handlerUnderTest, "connectionManager", connectionManager);
        ReflectionTestUtils.setField(handlerUnderTest, "messageHandlerFactory", messageHandlerFactory);

        CheeseMessage message = new CheeseMessage(
                com.cheeseocean.im.postoffice.protocol.CheeseMessageType.TCP_MSG_READ_RECEIPT,
                "op-read-1",
                "{\"receiptType\":\"READ_CURSOR\",\"conversationId\":\"single:userA:userB\",\"seq\":19}"
        );
        handlerUnderTest.channelRead0(ctx, message);

        verify(messageHandlerFactory).getHandler(CommandType.READ_RECEIPT);
        verify(handler).handle(any(UserConnection.class), any(ClientEnvelope.class));
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
