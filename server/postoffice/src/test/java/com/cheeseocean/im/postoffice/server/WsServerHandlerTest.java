package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.protocol.proto.ProtoClientEnvelope;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsServerHandlerTest {

    @Test
    void binaryProtobufEnvelopeShouldDispatchToCommandHandler() throws Exception {
        ConnectionManager manager = mock(ConnectionManager.class);
        MessageHandlerFactory factory = mock(MessageHandlerFactory.class);
        MessageHandler handler = mock(MessageHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 5147));
        when(manager.getConnectionByChannel(channel)).thenReturn(connection());
        when(factory.getHandler(CommandType.CHAT_SEND)).thenReturn(handler);
        when(handler.handle(any(UserConnection.class), any(ClientEnvelope.class))).thenReturn(MessageHandler.HandleResult.success());

        BusinessMessageExecutor executor = immediateExecutor();
        WsServerHandler serverHandler = new WsServerHandler(manager, factory, executor);
        byte[] payload = ProtoClientEnvelope.newBuilder()
                .setCommand(CommandType.CHAT_SEND.getCode())
                .setRequestId("ws-1")
                .setChatMessage(ProtoMessageMapper.toProto(new com.cheeseocean.im.common.api.dto.message.Message()))
                .build().toByteArray();

        serverHandler.channelRead0(ctx, new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)));

        verify(factory).getHandler(CommandType.CHAT_SEND);
        verify(handler).handle(any(UserConnection.class), any(ClientEnvelope.class));
    }

    private static UserConnection connection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("ws-conn");
        connection.setUserID("u100");
        connection.setAuthenticated("token");
        ConnectionContext context = new ConnectionContext();
        context.setConnId("ws-conn");
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
