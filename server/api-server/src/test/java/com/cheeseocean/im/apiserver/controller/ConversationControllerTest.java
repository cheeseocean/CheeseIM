package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.facade.ConversationFacade;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.api.enums.ConversationKind;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.api.HistoryMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationControllerTest {

    @Test
    void listShouldReturnConversationCards() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.listConversations("Bearer token", 20))
                .thenReturn(List.of(conversation("c1:userA:userB", "userA", "Need the final mockups.")));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(get("/api/im/conversations")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value("c1:userA:userB"))
                .andExpect(jsonPath("$[0].title").value("userA"))
                .andExpect(jsonPath("$[0].lastMessagePreview").value("Need the final mockups."));

        verify(conversationFacade).listConversations("Bearer token", 20);
    }

    @Test
    void messagesShouldReturnRecentMessages() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getConversationMessages("Bearer token", "c2:crew", 30))
                .thenReturn(List.of(history(102L, "s-102", "userC", "Casey", "Stand-up moved to 11:30.")));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(get("/api/im/conversations/c2:crew/messages")
                        .param("limit", "30")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sequence").value(102))
                .andExpect(jsonPath("$[0].senderName").value("Casey"))
                .andExpect(jsonPath("$[0].content").value("Stand-up moved to 11:30."));

        verify(conversationFacade).getConversationMessages("Bearer token", "c2:crew", 30);
    }

    @Test
    void allShouldReturnUserConversations() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getAllConversations("Bearer token"))
                .thenReturn(List.of(userConversation("c2:crew", 2, "crew")));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(get("/api/im/conversations/all")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value("c2:crew"))
                .andExpect(jsonPath("$[0].targetId").value("crew"));

        verify(conversationFacade).getAllConversations("Bearer token");
    }

    @Test
    void getConversationShouldReturnSingleConversation() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getConversation("Bearer token", "c2:crew"))
                .thenReturn(userConversation("c2:crew", 2, "crew"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(get("/api/im/conversations/c2:crew")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("c2:crew"));

        verify(conversationFacade).getConversation("Bearer token", "c2:crew");
    }

    @Test
    void idsShouldReturnConversationIds() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getConversationIds("Bearer token")).thenReturn(List.of("c1", "c2"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(get("/api/im/conversations/ids")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("c1"))
                .andExpect(jsonPath("$[1]").value("c2"));

        verify(conversationFacade).getConversationIds("Bearer token");
    }

    @Test
    void hashShouldReturnConversationIdsHash() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getConversationIdsHash("Bearer token")).thenReturn(42L);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(get("/api/im/conversations/ids/hash")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(42));

        verify(conversationFacade).getConversationIdsHash("Bearer token");
    }

    @Test
    void notNotifyShouldReturnConversationIds() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getNotNotifyConversationIds("Bearer token")).thenReturn(List.of("c1"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(get("/api/im/conversations/not-notify")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("c1"));

        verify(conversationFacade).getNotNotifyConversationIds("Bearer token");
    }

    @Test
    void pinnedShouldReturnConversationIds() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        when(conversationFacade.getPinnedConversationIds("Bearer token")).thenReturn(List.of("c2"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(get("/api/im/conversations/pinned")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("c2"));

        verify(conversationFacade).getPinnedConversationIds("Bearer token");
    }

    @Test
    void setConversationsShouldDelegateToFacade() throws Exception {
        ConversationFacade conversationFacade = mock(ConversationFacade.class);
        SetConversationRequest request = new SetConversationRequest();
        request.setConversationId("c2:crew");
        request.setConversationType(2);
        request.setTargetId("crew");
        request.setPinned(true);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationFacade)).build();

        mockMvc.perform(put("/api/im/conversations")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(conversationFacade).setConversations("Bearer token", request);
    }

    private static ConversationSummaryResponse conversation(String conversationId, String title, String preview) {
        ConversationSummaryResponse response = new ConversationSummaryResponse();
        response.setConversationId(conversationId);
        response.setKind(ConversationKind.DIRECT);
        response.setTitle(title);
        response.setSubtitle("Direct conversation");
        response.setLastMessagePreview(preview);
        response.setLastMessagePreviewType(MessagePreviewType.TEXT);
        response.setUnreadCount(1);
        response.setLastMessageTime(1742382300000L);
        return response;
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

    private static UserConversation userConversation(String conversationId, int conversationType, String targetId) {
        UserConversation response = new UserConversation();
        response.setConversationId(conversationId);
        response.setConversationType(conversationType);
        response.setTargetId(targetId);
        return response;
    }
}
