package com.cheeseocean.im.apiserver.facade;

import com.cheeseocean.im.apiserver.model.request.ListConversationsRequest;
import com.cheeseocean.im.apiserver.model.response.ConversationIncrementalSyncResponse;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.conversation.ConversationSyncService;
import com.cheeseocean.im.common.api.conversation.ReadStateService;
import com.cheeseocean.im.common.api.dto.conversation.ConversationIncrementalSyncResult;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.message.MessageHistoryQueryService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.permission.ConversationPermissionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
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
                mock(ReadStateService.class),
                mock(ConversationPermissionService.class),
                mock(MessageHistoryQueryService.class)
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
                mock(ReadStateService.class),
                mock(ConversationPermissionService.class),
                mock(MessageHistoryQueryService.class)
        );

        List<?> responses = facade.getAllConversations(session("u100"));
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void syncConversationsShouldMapIncrementalResult() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationIncrementalSyncResult syncResult = new ConversationIncrementalSyncResult();
        syncResult.setVersionId("v1");
        syncResult.setVersion(3);
        syncResult.setIdHash(99);
        syncResult.setFull(false);
        syncResult.setUpdate(List.of(conversation("c1", "u2")));
        syncResult.setDelete(List.of("c0"));
        when(conversationService.syncConversations("u100", "v1", 2, 88)).thenReturn(syncResult);

        ConversationFacade facade = new ConversationFacade(
                conversationService,
                mock(ConversationSyncService.class),
                mock(ReadStateService.class),
                mock(ConversationPermissionService.class),
                mock(MessageHistoryQueryService.class)
        );

        ConversationIncrementalSyncResponse response = facade.syncConversations(session("u100"), "v1", 2L, 88L);

        assertEquals("v1", response.getVersionId());
        assertEquals(3, response.getVersion());
        assertEquals(99, response.getIdHash());
        assertEquals("c1", response.getUpdate().get(0).getConversationId());
        assertEquals(List.of("c0"), response.getDelete());
        verify(conversationService).syncConversations("u100", "v1", 2, 88);
    }

    @Test
    void deleteConversationShouldDeleteOnlyCurrentUsersConversation() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationFacade facade = new ConversationFacade(
                conversationService,
                mock(ConversationSyncService.class),
                mock(ReadStateService.class),
                mock(ConversationPermissionService.class),
                mock(MessageHistoryQueryService.class)
        );

        facade.deleteConversation(session("u100"), "s:u100:u200");

        verify(conversationService).deleteConversation("u100", "s:u100:u200");
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    private static UserConversation conversation(String conversationId, String targetId) {
        UserConversation conversation = new UserConversation();
        conversation.setOwnerUserId("u100");
        conversation.setConversationId(conversationId);
        conversation.setChatType(1);
        conversation.setTargetId(targetId);
        return conversation;
    }
}
