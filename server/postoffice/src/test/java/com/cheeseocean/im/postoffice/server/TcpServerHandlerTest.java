package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TcpServerHandlerTest {

    @Test
    void authenticatedEnvelopeShouldDispatchToCommandHandler() throws Exception {
        ConnectionManager manager = mock(ConnectionManager.class);
        MessageHandlerFactory factory = mock(MessageHandlerFactory.class);
        MessageHandler handler = mock(MessageHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5148));
        when(manager.getConnectionByChannel(channel)).thenReturn(connection());
        when(factory.getHandler(CommandType.HEARTBEAT)).thenReturn(handler);
        when(handler.handle(any(UserConnection.class), any(ClientEnvelope.class))).thenReturn(MessageHandler.HandleResult.success());

        BusinessMessageExecutor executor = immediateExecutor();
        TcpServerHandler serverHandler = new TcpServerHandler(manager, factory, executor);
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.HEARTBEAT);
        envelope.setRequestId("tcp-1");
        envelope.setBody(new byte[0]);

        serverHandler.channelRead0(ctx, envelope);

        verify(factory).getHandler(CommandType.HEARTBEAT);
        verify(handler).handle(any(UserConnection.class), any(ClientEnvelope.class));
        assertTrue(connection().isAuthenticated());
    }

    private static UserConnection connection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("tcp-conn");
        connection.setUserID("u100");
        connection.setAuthenticated("token");
        ConnectionContext context = new ConnectionContext();
        context.setConnId("tcp-conn");
        context.setUserId("u100");
        context.setState(ConnectionState.AUTHENTICATED);
        connection.setContext(context);
        return connection;
    }

    private static BusinessMessageExecutor immediateExecutor() {
        BusinessMessageExecutor executor = mock(BusinessMessageExecutor.class);
        when(executor.submit(any(Channel.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        return executor;
    }
}
