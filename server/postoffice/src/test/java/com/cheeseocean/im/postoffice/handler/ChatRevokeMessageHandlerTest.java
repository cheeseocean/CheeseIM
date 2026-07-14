package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.dto.message.MessageMutationResult;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ConnectionState;
import com.cheeseocean.im.common.api.message.MessageMutationService;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatRevokeCommand;
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

class ChatRevokeMessageHandlerTest {

    @Test
    void shouldRevokeThroughSharedServiceAndReturnTypedEnvelope() {
        MessageMutationService service = mock(MessageMutationService.class);
        ConnectionSessionGuard guard = mock(ConnectionSessionGuard.class);
        doNothing().when(guard).ensureAuthenticated(any(UserConnection.class));
        MessageMutationResult mutation = new MessageMutationResult();
        mutation.setSuccess(true);
        mutation.setConversationId("s:user-1:user-2");
        mutation.setServerMsgId("server-1");
        mutation.setOperatorUserId("user-1");
        mutation.setTargetSenderId("user-1");
        when(service.revoke("user-1", "s:user-1:user-2", "server-1", "误发"))
                .thenReturn(mutation);

        MessageHandler.HandleResult result = handler(service, guard).handle(connection(), envelope());

        assertTrue(result.isSuccess());
        assertEquals(CommandType.CHAT_REVOKE, result.getResponseEnvelope().getCommand());
        verify(service).revoke("user-1", "s:user-1:user-2", "server-1", "误发");
    }

    private static ChatRevokeMessageHandler handler(MessageMutationService service, ConnectionSessionGuard guard) {
        ChatRevokeMessageHandler handler = new ChatRevokeMessageHandler(guard);
        ReflectionTestUtils.setField(handler, "messageMutationService", service);
        return handler;
    }

    private static ClientEnvelope envelope() {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.CHAT_REVOKE);
        envelope.setRequestId("revoke-1");
        envelope.setBody(ProtoChatRevokeCommand.newBuilder()
                .setConversationId("s:user-1:user-2").setServerMsgId("server-1").setReason("误发")
                .build().toByteArray());
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
