package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.message.ConversationLastMessageQueryService;
import com.cheeseocean.im.common.core.business.domain.ConversationOffsetRange;
import com.cheeseocean.im.common.core.business.domain.UserConversationState;
import com.cheeseocean.im.common.core.business.repository.ConversationOffsetRangeRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationQueryServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void getConversationsShouldComputeUnreadFromOffsetsAndUseMessageDomainLatest() throws Exception {
        UserConversationStateRepository stateRepository = mock(UserConversationStateRepository.class);
        ConversationOffsetRangeRepository offsetRepository = mock(ConversationOffsetRangeRepository.class);
        ConversationLastMessageQueryService lastMessageQueryService = mock(ConversationLastMessageQueryService.class);

        UserConversationState state = new UserConversationState();
        state.setOwnerUserId("u1");
        state.setConversationId("c1");
        state.setConversationType(1);
        state.setTargetId("u2");
        state.setUnreadCount(99);
        state.setLatestMsgSeq(3L);
        state.setLatestMsg("{\"legacy\":true}");

        ConversationOffsetRange range = new ConversationOffsetRange();
        range.setOwnerUserId("u1");
        range.setConversationId("c1");
        range.setReadSeq(4L);
        range.setMaxSeq(9L);

        ConversationLastMessageSummary summary = new ConversationLastMessageSummary();
        summary.setSeq(9L);
        summary.setSenderId("u2");
        summary.setPreviewText("latest");

        when(stateRepository.findByIds("u1", List.of("c1"))).thenReturn(List.of(state));
        when(offsetRepository.findByIds("u1", List.of("c1"))).thenReturn(List.of(range));
        when(lastMessageQueryService.getLatestMessages(List.of("c1"))).thenReturn(Map.of("c1", summary));

        ConversationQueryServiceImpl service = new ConversationQueryServiceImpl(
                stateRepository, offsetRepository, lastMessageQueryService, OBJECT_MAPPER
        );

        var result = service.getConversations("u1", List.of("c1")).get(0);

        assertEquals(5, result.getUnreadCount());
        assertEquals(4L, result.getReadSeq());
        assertEquals(9L, result.getLatestMsgSeq());
        assertEquals(OBJECT_MAPPER.writeValueAsString(summary), result.getLatestMsg());
    }

    @Test
    void getConversationShouldFallbackToLegacyStateWhenOffsetsOrSummaryMissing() {
        UserConversationStateRepository stateRepository = mock(UserConversationStateRepository.class);
        ConversationOffsetRangeRepository offsetRepository = mock(ConversationOffsetRangeRepository.class);
        ConversationLastMessageQueryService lastMessageQueryService = mock(ConversationLastMessageQueryService.class);

        UserConversationState state = new UserConversationState();
        state.setOwnerUserId("u1");
        state.setConversationId("c1");
        state.setUnreadCount(7);
        state.setLatestMsgSeq(3L);
        state.setLatestMsg("{\"legacy\":true}");

        when(stateRepository.findOne("u1", "c1")).thenReturn(state);
        when(offsetRepository.findByIds("u1", List.of("c1"))).thenReturn(List.of());
        when(lastMessageQueryService.getLatestMessages(List.of("c1"))).thenReturn(Map.of());

        ConversationQueryServiceImpl service = new ConversationQueryServiceImpl(
                stateRepository, offsetRepository, lastMessageQueryService, OBJECT_MAPPER
        );

        var result = service.getConversation("u1", "c1");

        assertEquals(7, result.getUnreadCount());
        assertEquals(3L, result.getLatestMsgSeq());
        assertEquals("{\"legacy\":true}", result.getLatestMsg());
        assertNull(result.getReadSeq());
    }
}
