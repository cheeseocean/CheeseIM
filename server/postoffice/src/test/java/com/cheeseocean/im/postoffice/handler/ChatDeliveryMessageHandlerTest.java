package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.conversation.DeliveryStateService;
import com.cheeseocean.im.common.api.dto.conversation.DeliverySeqUpdate;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatDeliveryAckCommand;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatDeliveryMessageHandlerTest {
    @Test
    void shouldAcceptAuthenticatedDeviceHighWatermark() {
        DeliveryStateService service = mock(DeliveryStateService.class);
        DeliverySeqUpdate update = new DeliverySeqUpdate();
        update.setConversationId("s:u1:u2"); update.setRecipientUserId("u2");
        update.setDeviceId("ios-1"); update.setDeliveredSeq(9L);
        when(service.acknowledge("u2", "ios-1", "s:u1:u2", 9L, "op-1")).thenReturn(update);
        ChatDeliveryMessageHandler handler = new ChatDeliveryMessageHandler(mock(ConnectionSessionGuard.class));
        ReflectionTestUtils.setField(handler, "deliveryStateService", service);
        UserConnection connection = new UserConnection(); connection.setUserID("u2"); connection.setDeviceId("ios-1");
        connection.setAuthenticated("token");
        ClientEnvelope envelope = new ClientEnvelope(); envelope.setCommand(CommandType.CHAT_DELIVERY); envelope.setRequestId("op-1");
        envelope.setBody(ProtoChatDeliveryAckCommand.newBuilder().setConversationId("s:u1:u2")
                .setMaxDeliveredSeq(9L).setDeviceId("ios-1").setOpId("op-1").build().toByteArray());

        MessageHandler.HandleResult result = handler.handle(connection, envelope);

        assertTrue(result.isSuccess());
        assertEquals(CommandType.CHAT_DELIVERY, result.getResponseEnvelope().getCommand());
    }
}
