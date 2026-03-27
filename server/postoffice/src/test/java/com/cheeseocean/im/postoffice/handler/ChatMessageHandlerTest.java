package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.dto.message.ChatSendRequest;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.dto.receipt.ReceiptAckReq;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.api.rpc.ReceiptAckRpc;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.common.core.enums.ConnectionState;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.ReceiptType;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.service.MessageSendReqMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

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
    void readReceiptChatSendShouldCallReceiptAckRpcInsteadOfMessageSendRpc() {
        MessageSender messageSender = mock(MessageSender.class);
        ReceiptAckRpc receiptAckRpc = mock(ReceiptAckRpc.class);
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureValid(any(UserConnection.class));

        ChatMessageHandler handler = new ChatMessageHandler();
        ReflectionTestUtils.setField(handler, "messageSendRpc", messageSender);
        ReflectionTestUtils.setField(handler, "receiptAckRpc", receiptAckRpc);
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(handler, "messageSendReqMapper", new MessageSendReqMapper());
        ReflectionTestUtils.setField(handler, "connectionSessionGuard", guard);

        MessageHandler.HandleResult result = handler.handle(authenticatedConnection(), readReceiptEnvelope());

        assertTrue(result.isSuccess());
        assertNotNull(result.getResponseMessage());
        ArgumentCaptor<ReceiptAckReq> reqCaptor = ArgumentCaptor.forClass(ReceiptAckReq.class);
        verify(receiptAckRpc).apply(reqCaptor.capture());
        verifyNoInteractions(messageSender);
        ReceiptAckReq req = reqCaptor.getValue();
        assertTrue(req.getAckType() == ReceiptType.READ_CURSOR);
        assertTrue(req.getUserId().equals("user-1"));
        assertTrue(req.getConversationId().equals("c1:user-1:user-2"));
        assertTrue(req.getSeq().equals(19L));
    }

    @Test
    void normalChatSendShouldStillCallMessageSendRpc() {
        MessageSender messageSender = mock(MessageSender.class);
        ReceiptAckRpc receiptAckRpc = mock(ReceiptAckRpc.class);
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureValid(any(UserConnection.class));
        when(messageSender.sendMessage(any())).thenAnswer(invocation -> {
            SendMessageResp resp = new SendMessageResp();
            resp.setAccepted(true);
            resp.setServerMsgId("server-1");
            return resp;
        });

        ChatMessageHandler handler = new ChatMessageHandler();
        ReflectionTestUtils.setField(handler, "messageSendRpc", messageSender);
        ReflectionTestUtils.setField(handler, "receiptAckRpc", receiptAckRpc);
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(handler, "messageSendReqMapper", new MessageSendReqMapper());
        ReflectionTestUtils.setField(handler, "connectionSessionGuard", guard);

        MessageHandler.HandleResult result = handler.handle(authenticatedConnection(), textEnvelope());

        assertTrue(result.isSuccess());
        verify(messageSender).sendMessage(any());
        verifyNoInteractions(receiptAckRpc);
    }

    private static ClientEnvelope readReceiptEnvelope() {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionType(SessionType.SINGLE.getCode());
        request.setRecvId("user-2");
        request.setClientMsgId("client-receipt-1");
        request.setContentType(ContentType.READ_RECEIPT.getCode());
        request.setContent("{\"receiptType\":\"READ_CURSOR\",\"conversationId\":\"c1:user-1:user-2\",\"seq\":19}");

        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.CHAT_SEND);
        envelope.setRequestId("op-read-1");
        envelope.setBody(request);
        return envelope;
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
