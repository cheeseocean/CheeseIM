package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.dto.message.ChatSendRequest;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.common.core.enums.ConnectionState;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.service.MessageSendReqMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        MessageSender messageSender = mock(MessageSender.class);
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureValid(any(UserConnection.class));
        when(messageSender.sendMessage(any())).thenAnswer(invocation -> {
            SendMessageResp resp = new SendMessageResp();
            resp.setAccepted(true);
            resp.setServerMsgId("server-1");
            return resp;
        });

        ChatMessageHandler handler = new ChatMessageHandler();
        ReflectionTestUtils.setField(handler, "messageSender", messageSender);
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(handler, "messageSendReqMapper", new MessageSendReqMapper());
        ReflectionTestUtils.setField(handler, "connectionSessionGuard", guard);

        MessageHandler.HandleResult result = handler.handle(authenticatedConnection(), textEnvelope());

        assertTrue(result.isSuccess());
        ServerEnvelope responseEnvelope = result.getResponseEnvelope();
        assertNotNull(responseEnvelope);
        assertEquals(CommandType.CHAT_SEND, responseEnvelope.getCommand());
        verify(messageSender).sendMessage(any());
    }

    @Test
    void invalidSessionShouldReturnErrorEnvelopeWithoutCallingMessageSender() {
        MessageSender messageSender = mock(MessageSender.class);
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureValid(any(UserConnection.class));

        ChatMessageHandler handler = new ChatMessageHandler();
        ReflectionTestUtils.setField(handler, "messageSender", messageSender);
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(handler, "messageSendReqMapper", new MessageSendReqMapper());
        ReflectionTestUtils.setField(handler, "connectionSessionGuard", guard);

        MessageHandler.HandleResult result = handler.handle(authenticatedConnection(), invalidEnvelope());

        assertTrue(!result.isSuccess());
        assertEquals(CommandType.ERROR, result.getResponseEnvelope().getCommand());
        verifyNoInteractions(messageSender);
    }

    private static ClientEnvelope textEnvelope() {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionType(SessionType.SINGLE.getCode());
        request.setRecvId("user-2");
        request.setClientMsgId("client-1");
        request.setContentType(ContentType.TEXT.getCode());
        request.setContent("hello");

        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.CHAT_SEND);
        envelope.setRequestId("op-chat-1");
        envelope.setBody(request);
        return envelope;
    }

    private static ClientEnvelope invalidEnvelope() {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionType(SessionType.SINGLE.getCode());
        request.setClientMsgId("client-1");
        request.setContentType(ContentType.TEXT.getCode());
        request.setContent("hello");

        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.CHAT_SEND);
        envelope.setRequestId("op-invalid-1");
        envelope.setBody(request);
        return envelope;
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
        context.setDeviceId("device-1");
        context.setPlatformId(2);
        context.setState(ConnectionState.AUTHENTICATED);
        connection.setContext(context);
        return connection;
    }
}
