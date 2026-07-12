package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatMessageHandlerTest {

    @Test
    void normalChatSendShouldReturnChatSendEnvelope() {
        MessageSender sender = mock(MessageSender.class);
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureAuthenticated(any(UserConnection.class));
        SendMessageResp response = new SendMessageResp();
        response.setAccepted(true);
        response.setServerMsgId("server-1");
        when(sender.sendMessage(any())).thenReturn(response);

        MessageHandler.HandleResult result = handler(sender, guard).handle(connection(), envelope(message("user-2")));

        assertTrue(result.isSuccess());
        assertEquals(CommandType.CHAT_SEND_ACK, result.getResponseEnvelope().getCommand());
        verify(sender).sendMessage(any());
    }

    @Test
    void invalidPrivateMessageShouldReturnErrorWithoutSending() {
        MessageSender sender = mock(MessageSender.class);
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureAuthenticated(any(UserConnection.class));

        MessageHandler.HandleResult result = handler(sender, guard).handle(connection(), envelope(message("")));

        assertFalse(result.isSuccess());
        assertEquals(CommandType.ERROR, result.getResponseEnvelope().getCommand());
        verifyNoInteractions(sender);
    }

    private static ChatMessageHandler handler(MessageSender sender, ConnectionSessionGuard guard) {
        ChatMessageHandler handler = new ChatMessageHandler();
        ReflectionTestUtils.setField(handler, "messageSender", sender);
        ReflectionTestUtils.setField(handler, "connectionSessionGuard", guard);
        return handler;
    }

    private static ClientEnvelope envelope(Message message) {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.CHAT_SEND);
        envelope.setRequestId("chat-1");
        envelope.setBody(ProtoMessageMapper.toProto(message).toByteArray());
        return envelope;
    }

    private static Message message(String receiverId) {
        Message message = new Message();
        message.setClientMsgId("client-1");
        message.setReceiverId(receiverId);
        message.setContent("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        message.setContentType(ContentType.TEXT);
        message.setChatType(ChatType.PRIVATE);
        return message;
    }

    private static UserConnection connection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("conn-1");
        connection.setUserID("user-1");
        connection.setAuthenticated("token");
        ConnectionContext context = new ConnectionContext();
        context.setConnId("conn-1");
        context.setUserId("user-1");
        context.setSessionId("session-1");
        context.setPlatformCode(PlatformType.IOS);
        context.setState(ConnectionState.AUTHENTICATED);
        connection.setContext(context);
        return connection;
    }
}
