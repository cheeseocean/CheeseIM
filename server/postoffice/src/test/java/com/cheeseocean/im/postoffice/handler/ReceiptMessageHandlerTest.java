package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.event.ReceiptEvent;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.enums.ConnectionState;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.service.GatewayReceiptPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReceiptMessageHandlerTest {

    @Test
    void handleShouldPublishDeliveredReceiptEvent() {
        GatewayReceiptPublisher receiptPublisher = mock(GatewayReceiptPublisher.class);
        ConnectionSessionGuard sessionGuard = mock(ConnectionSessionGuard.class);
        doNothing().when(sessionGuard).ensureValid(org.mockito.ArgumentMatchers.any(UserConnection.class));

        ReceiptMessageHandler handler = new ReceiptMessageHandler();
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(handler, "gatewayReceiptPublisher", receiptPublisher);
        ReflectionTestUtils.setField(handler, "connectionSessionGuard", sessionGuard);

        MessageHandler.HandleResult result = handler.handle(authenticatedConnection(), deliveredEnvelope());

        ArgumentCaptor<ReceiptEvent> eventCaptor = ArgumentCaptor.forClass(ReceiptEvent.class);
        verify(receiptPublisher).publish(eventCaptor.capture());
        ReceiptEvent event = eventCaptor.getValue();
        assertEquals("DELIVERED", event.getReceiptType());
        assertEquals("userB", event.getUserId());
        assertEquals("single:userA:userB", event.getConversationId());
        assertEquals("msg-1", event.getServerMsgId());
        assertEquals(11L, event.getSeq());
        assertEquals("ios-1", event.getDeviceId());
        assertNotNull(result.getResponseMessage());
        assertFalse(result.isShouldClose());
    }

    @Test
    void handleShouldPublishReadCursorReceiptEvent() {
        GatewayReceiptPublisher receiptPublisher = mock(GatewayReceiptPublisher.class);
        ConnectionSessionGuard sessionGuard = mock(ConnectionSessionGuard.class);
        doNothing().when(sessionGuard).ensureValid(org.mockito.ArgumentMatchers.any(UserConnection.class));

        ReceiptMessageHandler handler = new ReceiptMessageHandler();
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(handler, "gatewayReceiptPublisher", receiptPublisher);
        ReflectionTestUtils.setField(handler, "connectionSessionGuard", sessionGuard);

        handler.handle(authenticatedConnection(), readCursorEnvelope());

        ArgumentCaptor<ReceiptEvent> eventCaptor = ArgumentCaptor.forClass(ReceiptEvent.class);
        verify(receiptPublisher).publish(eventCaptor.capture());
        ReceiptEvent event = eventCaptor.getValue();
        assertEquals("READ_CURSOR", event.getReceiptType());
        assertEquals("userB", event.getUserId());
        assertEquals("single:userA:userB", event.getConversationId());
        assertEquals(19L, event.getSeq());
        assertEquals("ios-1", event.getDeviceId());
    }

    private static UserConnection authenticatedConnection() {
        UserConnection connection = new UserConnection();
        connection.setAuthenticated(true);
        connection.setUserID("userB");

        ConnectionContext context = new ConnectionContext();
        context.setUserId("userB");
        context.setSessionId("session-1");
        context.setDeviceId("ios-1");
        context.setPlatformId(1);
        context.setState(ConnectionState.AUTHENTICATED);
        connection.setContext(context);
        return connection;
    }

    private static ClientEnvelope deliveredEnvelope() {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.READ_RECEIPT);
        envelope.setRequestId("op-delivered");
        envelope.setBody(Map.of(
                "receiptType", "DELIVERED",
                "conversationId", "single:userA:userB",
                "serverMsgId", "msg-1",
                "seq", 11L
        ));
        return envelope;
    }

    private static ClientEnvelope readCursorEnvelope() {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.READ_RECEIPT);
        envelope.setRequestId("op-read");
        envelope.setBody(Map.of(
                "receiptType", "READ_CURSOR",
                "conversationId", "single:userA:userB",
                "seq", 19L
        ));
        return envelope;
    }
}
