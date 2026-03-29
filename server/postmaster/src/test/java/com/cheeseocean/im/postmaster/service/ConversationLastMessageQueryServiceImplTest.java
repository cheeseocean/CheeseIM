package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationLastMessageQueryServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void getLatestMessagesShouldDeserializeAndDeduplicateConversationIds() throws Exception {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);
        ConversationLastMessageSummary summary = new ConversationLastMessageSummary();
        summary.setSeq(12L);
        summary.setSenderId("userA");
        summary.setPreviewText("hello");

        when(conversationStateStore.getLastMessageSummaries(List.of("c1", "c2"))).thenReturn(Map.of(
                "c1", OBJECT_MAPPER.writeValueAsString(summary),
                "c2", ""
        ));

        ConversationLastMessageQueryServiceImpl service = new ConversationLastMessageQueryServiceImpl(
                conversationStateStore, OBJECT_MAPPER
        );

        Map<String, ConversationLastMessageSummary> actual =
                service.getLatestMessages(List.of("c1", "c1", "c2", "", null));

        verify(conversationStateStore).getLastMessageSummaries(List.of("c1", "c2"));
        assertEquals(1, actual.size());
        assertEquals(12L, actual.get("c1").getSeq());
        assertEquals("hello", actual.get("c1").getPreviewText());
    }

    @Test
    void getLatestMessagesShouldReturnEmptyMapForBlankInput() {
        ConversationStateStore conversationStateStore = mock(ConversationStateStore.class);
        ConversationLastMessageQueryServiceImpl service = new ConversationLastMessageQueryServiceImpl(
                conversationStateStore, OBJECT_MAPPER
        );

        assertTrue(service.getLatestMessages(List.of("", " ")).isEmpty());
    }
}
