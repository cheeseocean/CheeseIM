package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.conversation.ReadSeqUpdate;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import com.cheeseocean.im.common.core.business.repository.ConversationSequenceRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationVersionLogRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ReadStateServiceImplTest {

    @Test
    void acknowledgeShouldClampToMaxSeqWriteVersionLogAndReturnPrivateTargets() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationSequenceRepository sequenceRepository = mock(ConversationSequenceRepository.class);
        UserConversationSyncPointRepository syncPointRepository = mock(UserConversationSyncPointRepository.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        ReadSeqPersistenceWriter writer = mock(ReadSeqPersistenceWriter.class);
        ConversationVersionLogRepository versionLogRepository = mock(ConversationVersionLogRepository.class);
        UserConversation conversation = new UserConversation();
        conversation.setConversationId("s:u1:u2");
        conversation.setChatType(ChatType.PRIVATE.getCode());
        conversation.setTargetId("u2");
        when(conversationService.getConversation("u1", "s:u1:u2")).thenReturn(conversation);
        when(stateStore.getUserReadSeq("u1", "s:u1:u2")).thenReturn(3L);
        when(stateStore.getUserMaxSeq("u1", "s:u1:u2")).thenReturn(10L);
        when(stateStore.advanceReadState("u1", "s:u1:u2", 99L, 3L, 10L))
                .thenReturn(new ConversationStateStore.ReadState(10L, 0, true));

        ReadStateServiceImpl service = new ReadStateServiceImpl(conversationService, sequenceRepository,
                syncPointRepository, stateStore, writer, versionLogRepository);

        ReadSeqUpdate result = service.acknowledge("u1", "s:u1:u2", 99L);

        assertTrue(result.isChanged());
        assertEquals(10L, result.getReadSeq());
        assertEquals(List.of("u1", "u2"), result.getNotifyUserIds());
        verify(stateStore).advanceReadState("u1", "s:u1:u2", 99L, 3L, 10L);
        verify(writer).enqueue("u1", "s:u1:u2", 10L);
        verify(versionLogRepository).append("u1", "s:u1:u2", ConversationVersionOperation.READ_STATE_UPDATED);
    }

    @Test
    void acknowledgeShouldRejectInvisibleConversation() {
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.getConversation("u1", "s:u1:u2")).thenReturn(null);
        ReadStateServiceImpl service = new ReadStateServiceImpl(conversationService,
                mock(ConversationSequenceRepository.class), mock(UserConversationSyncPointRepository.class),
                mock(ConversationStateStore.class), mock(ReadSeqPersistenceWriter.class),
                mock(ConversationVersionLogRepository.class));

        assertNull(service.acknowledge("u1", "s:u1:u2", 1L));
    }

    @Test
    void acknowledgeShouldKeepExistingCursorWhenRequestDoesNotAdvance() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        ReadSeqPersistenceWriter writer = mock(ReadSeqPersistenceWriter.class);
        UserConversation conversation = new UserConversation();
        conversation.setConversationId("g:crew");
        conversation.setChatType(ChatType.GROUP.getCode());
        when(conversationService.getConversation("u1", "g:crew")).thenReturn(conversation);
        when(stateStore.getUserReadSeq("u1", "g:crew")).thenReturn(7L);
        when(stateStore.getUserMaxSeq("u1", "g:crew")).thenReturn(10L);
        when(stateStore.advanceReadState("u1", "g:crew", 6L, 7L, 10L))
                .thenReturn(new ConversationStateStore.ReadState(7L, 3, false));
        ReadStateServiceImpl service = new ReadStateServiceImpl(conversationService,
                mock(ConversationSequenceRepository.class), mock(UserConversationSyncPointRepository.class),
                stateStore, writer, mock(ConversationVersionLogRepository.class));

        ReadSeqUpdate result = service.acknowledge("u1", "g:crew", 6L);

        assertFalse(result.isChanged());
        assertEquals(7L, result.getReadSeq());
        assertTrue(result.getNotifyUserIds().isEmpty());
        verify(stateStore).advanceReadState("u1", "g:crew", 6L, 7L, 10L);
        verify(writer).enqueue("u1", "g:crew", 7L);
        verify(stateStore, never()).setUserReadSeq("u1", "g:crew", 6L);
    }

    @Test
    void acknowledgeShouldUseAtomicStoreResultWhenConcurrentMessageAdvancesMaxSeq() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        ReadSeqPersistenceWriter writer = mock(ReadSeqPersistenceWriter.class);
        ConversationVersionLogRepository versionLogRepository = mock(ConversationVersionLogRepository.class);
        UserConversation conversation = new UserConversation();
        conversation.setConversationId("g:crew");
        conversation.setChatType(ChatType.GROUP.getCode());
        when(conversationService.getConversation("u1", "g:crew")).thenReturn(conversation);
        when(stateStore.getUserReadSeq("u1", "g:crew")).thenReturn(3L);
        when(stateStore.getUserMaxSeq("u1", "g:crew")).thenReturn(10L);
        when(stateStore.advanceReadState("u1", "g:crew", 10L, 3L, 10L))
                .thenReturn(new ConversationStateStore.ReadState(10L, 1, true));
        ReadStateServiceImpl service = new ReadStateServiceImpl(conversationService,
                mock(ConversationSequenceRepository.class), mock(UserConversationSyncPointRepository.class),
                stateStore, writer, versionLogRepository);

        ReadSeqUpdate result = service.acknowledge("u1", "g:crew", 10L);

        assertEquals(10L, result.getReadSeq());
        assertTrue(result.isChanged());
        verify(writer).enqueue("u1", "g:crew", 10L);
        verify(versionLogRepository).append("u1", "g:crew", ConversationVersionOperation.READ_STATE_UPDATED);
    }
}
