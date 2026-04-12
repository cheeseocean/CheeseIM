package com.cheeseocean.im.apiserver.facade;

import com.cheeseocean.im.apiserver.model.request.ListConversationsRequest;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.conversation.ConversationSyncService;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.postbox.service.ConversationPermissionService;
import com.cheeseocean.im.postbox.service.HistoryQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationFacadeTest {

    @Test
    void listConversationsShouldTreatNullServiceResultAsEmptyList() {
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.getAllConversations(anyString())).thenReturn(null);
        ConversationFacade facade = new ConversationFacade(
                conversationService,
                mock(ConversationSyncService.class),
                mock(ConversationPermissionService.class),
                mock(HistoryQueryService.class)
        );

        ListConversationsRequest request = new ListConversationsRequest();
        request.setLimit(20);

        List<?> responses = facade.listConversations(session("u100"), request);
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void getAllConversationsShouldTreatNullServiceResultAsEmptyList() {
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.getAllConversations(anyString())).thenReturn(null);
        ConversationFacade facade = new ConversationFacade(
                conversationService,
                mock(ConversationSyncService.class),
                mock(ConversationPermissionService.class),
                mock(HistoryQueryService.class)
        );

        List<?> responses = facade.getAllConversations(session("u100"));
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }
}
