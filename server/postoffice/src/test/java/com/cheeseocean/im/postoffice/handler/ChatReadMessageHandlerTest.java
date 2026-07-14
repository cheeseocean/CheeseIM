package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.conversation.ReadStateService;
import com.cheeseocean.im.common.api.dto.conversation.ReadSeqUpdate;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatReadCommand;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatReadMessageHandlerTest {

    @Test
    void shouldAcknowledgeReadSeqAndReturnTypedReadEnvelope() {
        ReadStateService service = mock(ReadStateService.class);
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureAuthenticated(any(UserConnection.class));
        ReadSeqUpdate update = new ReadSeqUpdate();
        update.setConversationId("s:user-1:user-2");
        update.setReaderUserId("user-1");
        update.setReadSeq(8L);
        when(service.acknowledge("user-1", "s:user-1:user-2", 10L)).thenReturn(update);

        MessageHandler.HandleResult result = handler(service, guard).handle(connection(), envelope(10L));

        assertTrue(result.isSuccess());
        assertEquals(CommandType.CHAT_READ, result.getResponseEnvelope().getCommand());
        verify(service).acknowledge("user-1", "s:user-1:user-2", 10L);
    }

    private static ChatReadMessageHandler handler(ReadStateService service, ConnectionSessionGuard guard) {
        ChatReadMessageHandler handler = new ChatReadMessageHandler(guard);
        ReflectionTestUtils.setField(handler, "readStateService", service);
        return handler;
    }

    private static ClientEnvelope envelope(long readSeq) {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.CHAT_READ);
        envelope.setRequestId("read-1");
        envelope.setBody(ProtoChatReadCommand.newBuilder()
                .setConversationId("s:user-1:user-2").setReadSeq(readSeq).build().toByteArray());
        return envelope;
    }

    private static UserConnection connection() {
        UserConnection connection = new UserConnection();
        connection.setConnectionID("conn-1");
        connection.setUserID("user-1");
        connection.setAuthenticated("token");
        ConnectionContext context = new ConnectionContext();
        context.setUserId("user-1");
        context.setState(ConnectionState.AUTHENTICATED);
        connection.setContext(context);
        return connection;
    }
}
