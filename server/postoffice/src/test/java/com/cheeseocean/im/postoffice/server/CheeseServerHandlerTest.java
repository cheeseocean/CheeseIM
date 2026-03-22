package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
