package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.apiserver.auth.CurrentPrincipalArgumentResolver;
import com.cheeseocean.im.apiserver.facade.ConversationFacade;
import com.cheeseocean.im.apiserver.model.request.GetConversationRequest;
import com.cheeseocean.im.apiserver.model.request.ListConversationMessagesRequest;
import com.cheeseocean.im.apiserver.model.request.ListConversationsRequest;
import com.cheeseocean.im.apiserver.model.request.SetConversationsRequest;
import com.cheeseocean.im.apiserver.model.response.ConversationIdsHashResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationIdsResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationIncrementalSyncResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationResponse;
import com.cheeseocean.im.apiserver.model.response.HistoryMessageResponse;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.api.enums.ConversationKind;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationControllerTest {

    @Test
    void listShouldReturnConversationCards() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.listConversations(any(SessionPrincipal.class), any(ListConversationsRequest.class)))
                .thenReturn(List.of(conversationResponse("c1:userA:userB", 1, "userA", "Need the final mockups.")));

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value("c1:userA:userB"))
                .andExpect(jsonPath("$[0].title").value("userA"))
                .andExpect(jsonPath("$[0].lastMessagePreview").value("Need the final mockups."));

        verify(conversationFacade).listConversations(any(SessionPrincipal.class), any(ListConversationsRequest.class));
    }

    @Test
    void messagesShouldReturnRecentMessages() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getConversationMessages(any(SessionPrincipal.class), any(ListConversationMessagesRequest.class)))
                .thenReturn(List.of(history(102L, "s-102", "userC", "Casey", "Stand-up moved to 11:30.")));

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations/g:crew/messages")
                        .param("limit", "30")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sequence").value(102))
                .andExpect(jsonPath("$[0].senderName").value("Casey"))
                .andExpect(jsonPath("$[0].content").value("Stand-up moved to 11:30."));

        verify(conversationFacade).getConversationMessages(any(SessionPrincipal.class), any(ListConversationMessagesRequest.class));
    }

    @Test
    void allShouldReturnUserConversations() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getAllConversations(any(SessionPrincipal.class)))
                .thenReturn(List.of(conversationResponse("g:crew", 2, "crew", null)));

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations/all").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value("g:crew"))
                .andExpect(jsonPath("$[0].targetId").value("crew"));

        verify(conversationFacade).getAllConversations(any(SessionPrincipal.class));
    }

    @Test
    void getConversationShouldReturnSingleConversation() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getConversation(any(SessionPrincipal.class), any(GetConversationRequest.class)))
                .thenReturn(conversationResponse("g:crew", 2, "crew", null));

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations/g:crew").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("g:crew"));

        verify(conversationFacade).getConversation(any(SessionPrincipal.class), any(GetConversationRequest.class));
    }

    @Test
    void idsShouldReturnConversationIds() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(List.of("c1", "c2"));
        when(conversationFacade.getConversationIds(any(SessionPrincipal.class))).thenReturn(response);

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations/ids").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationIds[0]").value("c1"))
                .andExpect(jsonPath("$.conversationIds[1]").value("c2"));

        verify(conversationFacade).getConversationIds(any(SessionPrincipal.class));
    }

    @Test
    void hashShouldReturnConversationIdsHash() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        ConversationIdsHashResponse response = new ConversationIdsHashResponse();
        response.setHash(42L);
        when(conversationFacade.getConversationIdsHash(any(SessionPrincipal.class))).thenReturn(response);

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations/ids/hash").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hash").value(42));

        verify(conversationFacade).getConversationIdsHash(any(SessionPrincipal.class));
    }

    @Test
    void notNotifyShouldReturnConversationIds() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(List.of("c1"));
        when(conversationFacade.getNotNotifyConversationIds(any(SessionPrincipal.class))).thenReturn(response);

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations/not-notify").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationIds[0]").value("c1"));

        verify(conversationFacade).getNotNotifyConversationIds(any(SessionPrincipal.class));
    }

    @Test
    void pinnedShouldReturnConversationIds() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(List.of("c2"));
        when(conversationFacade.getPinnedConversationIds(any(SessionPrincipal.class))).thenReturn(response);

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations/pinned").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationIds[0]").value("c2"));

        verify(conversationFacade).getPinnedConversationIds(any(SessionPrincipal.class));
    }

    @Test
    void setConversationsShouldDelegateToFacade() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        SetConversationRequest request = new SetConversationRequest();
        request.setConversationId("g:crew");
        request.setConversationType(2);
        request.setTargetId("crew");
        request.setPinned(true);

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(put("/api/im/conversations")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(conversationFacade).setConversations(any(SessionPrincipal.class), any(SetConversationsRequest.class));
    }

    @Test
    void deleteConversationShouldDelegateToFacade() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(delete("/api/im/conversations/s:user-1:user-2")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(conversationFacade).deleteConversation(any(SessionPrincipal.class), any(String.class));
    }

    @Test
    void incrementalSyncShouldReturnConversationChanges() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        ConversationIncrementalSyncResponse response = new ConversationIncrementalSyncResponse();
        response.setVersionId("v1");
        response.setVersion(3);
        response.setIdHash(99);
        response.setUpdate(List.of(conversationResponse("c1:userA:userB", 1, "userA", null)));
        response.setDelete(List.of("c0"));
        when(conversationFacade.syncConversations(any(SessionPrincipal.class), any(), any(Long.class), any(Long.class)))
                .thenReturn(response);

        MockMvc mockMvc = mockMvc(conversationFacade);

        mockMvc.perform(get("/api/im/conversations/sync/incremental")
                        .param("versionId", "v1")
                        .param("version", "2")
                        .param("idHash", "88")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value("v1"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.idHash").value(99))
                .andExpect(jsonPath("$.update[0].conversationId").value("c1:userA:userB"))
                .andExpect(jsonPath("$.delete[0]").value("c0"));

        verify(conversationFacade).syncConversations(any(SessionPrincipal.class), any(), any(Long.class), any(Long.class));
    }

    private static MockMvc mockMvc(ConversationFacade conversationFacade) {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        return MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade))
                .setCustomArgumentResolvers(new CurrentPrincipalArgumentResolver(resolver))
                .build();
    }

    private static HistoryMessageResponse history(long sequence,
                                                  String serverMsgId,
                                                  String senderId,
                                                  String senderName,
                                                  String content) {
        HistoryMessageResponse response = new HistoryMessageResponse();
        response.setSequence(sequence);
        response.setServerMsgId(serverMsgId);
        response.setSenderId(senderId);
        response.setSenderName(senderName);
        response.setContent(content);
        response.setPreviewType(MessagePreviewType.TEXT);
        response.setSendTime(1742382300000L);
        return response;
    }

    private static ConversationResponse conversationResponse(String conversationId,
                                                             int conversationType,
                                                             String targetId,
                                                             String preview) {
        ConversationResponse response = new ConversationResponse();
        response.setConversationId(conversationId);
        response.setConversationType(conversationType);
        response.setTargetId(targetId);
        response.setKind(ConversationKind.DIRECT);
        response.setTitle(targetId);
        response.setSubtitle("Direct conversation");
        response.setLastMessagePreview(preview);
        response.setLastMessagePreviewType(MessagePreviewType.TEXT);
        return response;
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }
}
