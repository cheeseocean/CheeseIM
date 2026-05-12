package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.session.ConnectionAuthService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.postoffice.connection.ConnectionBindService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageHandlerContractTest {

    @Test
    void authHandlerShouldConsumeClientEnvelopeBody() {
        ConnectionAuthService authService = mock(ConnectionAuthService.class);
        ConnectionBindService bindService = mock(ConnectionBindService.class);
        when(authService.authenticateWsTicket("ticket-1")).thenReturn(session("user-1"));
        when(bindService.bindAuthenticated(any(UserConnection.class), any(SessionPrincipal.class))).thenReturn(true);

        AuthMessageHandler handler = new AuthMessageHandler();
        ReflectionTestUtils.setField(handler, "connectionAuthService", authService);
        ReflectionTestUtils.setField(handler, "connectionBindService", bindService);
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());

        MessageHandler.HandleResult result = handler.handle(pendingConnection(), envelope(
                CommandType.AUTH,
                "op-auth-1",
                Map.of("ticket", "ticket-1")
        ));

        assertTrue(result.isSuccess());
        assertNotNull(result.getResponseEnvelope());
        assertEquals(CommandType.AUTH, result.getResponseEnvelope().getCommand());
        verify(authService).authenticateWsTicket("ticket-1");
        verify(bindService).bindAuthenticated(any(UserConnection.class), any(SessionPrincipal.class));
    }

    @Test
    void heartbeatHandlerShouldConsumeClientEnvelope() {
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureSessionActive(any(UserConnection.class));

        HeartbeatMessageHandler handler = new HeartbeatMessageHandler();
        ReflectionTestUtils.setField(handler, "connectionSessionGuard", guard);

        MessageHandler.HandleResult result = handler.handle(authenticatedConnection(), envelope(
                CommandType.HEARTBEAT,
                "op-heartbeat-1",
                null
        ));

        assertTrue(result.isSuccess());
        assertNotNull(result.getResponseEnvelope());
        assertEquals(CommandType.HEARTBEAT, result.getResponseEnvelope().getCommand());
    }

    @Test
    void chatHandlerShouldConsumeChatSendEnvelopeBody() {
        MessageSender          messageSender = mock(MessageSender.class);
        ConnectionSessionGuard guard         = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureAuthenticated(any(UserConnection.class));
        when(messageSender.sendMessage(any(SendMessageReq.class))).thenAnswer(invocation -> {
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

        MessageHandler.HandleResult result = handler.handle(authenticatedConnection(), envelope(
                CommandType.CHAT_SEND,
                "op-chat-1",
                chatSendRequest()
        ));

        assertTrue(result.isSuccess());
        ServerEnvelope responseEnvelope = result.getResponseEnvelope();
        assertNotNull(responseEnvelope);
        assertEquals(CommandType.CHAT_SEND, responseEnvelope.getCommand());
        ArgumentCaptor<SendMessageReq> reqCaptor = ArgumentCaptor.forClass(SendMessageReq.class);
        verify(messageSender).sendMessage(reqCaptor.capture());
        assertEquals("op-chat-1", reqCaptor.getValue().getRequestId());
        assertEquals("client-1", reqCaptor.getValue().getClientMsgId());
        assertEquals("receiver-1", reqCaptor.getValue().getRecvId());
    }

    private static ClientEnvelope envelope(CommandType command, String requestId, Object body) {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(command);
        envelope.setRequestId(requestId);
        envelope.setBody(body);
        return envelope;
    }

    private static UserConnection pendingConnection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("conn-1");
        return connection;
    }

    private static UserConnection authenticatedConnection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("conn-1");
        connection.setUserID("user-1");
        connection.setAuthenticated("token");

        ConnectionContext context = new ConnectionContext();
        context.setConnId("conn-1");
        context.setUserId("user-1");
        context.setSessionId("session-1");
        context.setDeviceId("device-1");
        context.setPlatformCode(2);
        context.setState(ConnectionState.AUTHENTICATED);
        connection.setContext(context);
        return connection;
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setSessionId("session-1");
        session.setDeviceId("device-1");
        session.setPlatform("android");
        return session;
    }

    private static Message chatSendRequest() {
        Message request = new Message();
        request.setChatType(1);
        request.setReceiverId("receiver-1");
        request.setClientMsgId("client-1");
        request.setContentType(101);
        request.setContent("hello");
        request.setSendTime(1710000000000L);
        return request;
    }
}
