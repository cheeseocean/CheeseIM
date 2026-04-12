package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.conversation.PullMessages;
import com.cheeseocean.im.common.api.dto.conversation.SeqRangeRequest;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.cheeseocean.im.common.core.business.repository.ConversationRangeRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.postbox.service.HistoryQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationSyncServiceImplTest {

    @Test
    void getConversationMaxSeqsShouldPreferUserHotState() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        UserConversationSyncPointRepository syncPointRepository = mock(UserConversationSyncPointRepository.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        HistoryQueryService historyQueryService = mock(HistoryQueryService.class);
        ReadSeqPersistenceWriter readSeqPersistenceWriter = mock(ReadSeqPersistenceWriter.class);

        when(conversationService.getConversationIds("u100")).thenReturn(List.of("s:u100:u200"));
        when(stateStore.getUserMaxSeq("u100", "s:u100:u200")).thenReturn(12L);

        ConversationSyncServiceImpl service = new ConversationSyncServiceImpl(
                conversationService,
                rangeRepository,
                syncPointRepository,
                stateStore,
                historyQueryService,
                readSeqPersistenceWriter
        );

        Map<String, Long> result = service.getConversationMaxSeqs("u100", List.of());

        assertEquals(12L, result.get("s:u100:u200"));
    }

    @Test
    void getConversationMaxSeqsShouldTreatNullConversationIdsAsEmpty() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        UserConversationSyncPointRepository syncPointRepository = mock(UserConversationSyncPointRepository.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        HistoryQueryService historyQueryService = mock(HistoryQueryService.class);
        ReadSeqPersistenceWriter readSeqPersistenceWriter = mock(ReadSeqPersistenceWriter.class);

        when(conversationService.getConversationIds("u100")).thenReturn(null);

        ConversationSyncServiceImpl service = new ConversationSyncServiceImpl(
                conversationService,
                rangeRepository,
                syncPointRepository,
                stateStore,
                historyQueryService,
                readSeqPersistenceWriter
        );

        Map<String, Long> result = service.getConversationMaxSeqs("u100", List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void pullMessagesBySeqRangesShouldReturnMessagesAndCompletedState() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        UserConversationSyncPointRepository syncPointRepository = mock(UserConversationSyncPointRepository.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        HistoryQueryService historyQueryService = mock(HistoryQueryService.class);
        ReadSeqPersistenceWriter readSeqPersistenceWriter = mock(ReadSeqPersistenceWriter.class);

        UserConversation conversation = new UserConversation();
        conversation.setOwnerUserId("u100");
        conversation.setConversationId("s:u100:u200");
        when(conversationService.getConversations("u100", List.of("s:u100:u200"))).thenReturn(List.of(conversation));
        when(stateStore.getUserMaxSeq("u100", "s:u100:u200")).thenReturn(20L);
        when(historyQueryService.pullMessagesBySeqRange("s:u100:u200", 11L, 15L, 10))
                .thenReturn(List.of(message(11L), message(12L)));

        ConversationSyncServiceImpl service = new ConversationSyncServiceImpl(
                conversationService,
                rangeRepository,
                syncPointRepository,
                stateStore,
                historyQueryService,
                readSeqPersistenceWriter
        );

        SeqRangeRequest range = new SeqRangeRequest();
        range.setConversationId("s:u100:u200");
        range.setBeginSeq(11L);
        range.setEndSeq(15L);
        PullMessages response = service.pullMessagesBySeqRanges("u100", List.of(range), 10);

        assertEquals(2, response.getMessagesByConversation().get("s:u100:u200").size());
        assertEquals(12L, response.getEndSeqByConversation().get("s:u100:u200"));
        assertTrue(response.getCompletedByConversation().get("s:u100:u200"));
    }

    @Test
    void pullMessagesBySeqRangesShouldTreatNullVisibleConversationsAsEmpty() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        UserConversationSyncPointRepository syncPointRepository = mock(UserConversationSyncPointRepository.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        HistoryQueryService historyQueryService = mock(HistoryQueryService.class);
        ReadSeqPersistenceWriter readSeqPersistenceWriter = mock(ReadSeqPersistenceWriter.class);

        when(conversationService.getConversations("u100", List.of("s:u100:u200"))).thenReturn(null);

        ConversationSyncServiceImpl service = new ConversationSyncServiceImpl(
                conversationService,
                rangeRepository,
                syncPointRepository,
                stateStore,
                historyQueryService,
                readSeqPersistenceWriter
        );

        SeqRangeRequest range = new SeqRangeRequest();
        range.setConversationId("s:u100:u200");
        range.setBeginSeq(11L);
        range.setEndSeq(15L);
        PullMessages response = service.pullMessagesBySeqRanges("u100", List.of(range), 10);

        assertTrue(response.getMessagesByConversation().get("s:u100:u200").isEmpty());
        assertEquals(10L, response.getEndSeqByConversation().get("s:u100:u200"));
        assertTrue(response.getCompletedByConversation().get("s:u100:u200"));
    }

    @Test
    void ackReadSeqShouldWriteHotStateAndEnqueuePersistence() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        UserConversationSyncPointRepository syncPointRepository = mock(UserConversationSyncPointRepository.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        HistoryQueryService historyQueryService = mock(HistoryQueryService.class);
        ReadSeqPersistenceWriter readSeqPersistenceWriter = mock(ReadSeqPersistenceWriter.class);

        UserConversation conversation = new UserConversation();
        conversation.setConversationId("s:u100:u200");
        when(conversationService.getConversation("u100", "s:u100:u200")).thenReturn(conversation);
        when(stateStore.getUserReadSeq("u100", "s:u100:u200")).thenReturn(5L);
        when(stateStore.getUserMaxSeq("u100", "s:u100:u200")).thenReturn(10L);

        ConversationSyncServiceImpl service = new ConversationSyncServiceImpl(
                conversationService,
                rangeRepository,
                syncPointRepository,
                stateStore,
                historyQueryService,
                readSeqPersistenceWriter
        );

        service.ackReadSeq("u100", "s:u100:u200", 9L);

        verify(stateStore).setUserReadSeq("u100", "s:u100:u200", 9L);
        verify(stateStore).setUnread("u100", "s:u100:u200", 1);
        verify(readSeqPersistenceWriter).enqueue("u100", "s:u100:u200", 9L);
    }

    private static Message message(long seq) {
        Message message = new Message();
        message.setSeq(seq);
        message.setSenderId("u200");
        message.setReceiverId("u100");
        message.setSessionType(SessionType.SINGLE);
        message.setContentType(ContentType.TEXT);
        return message;
    }
}
