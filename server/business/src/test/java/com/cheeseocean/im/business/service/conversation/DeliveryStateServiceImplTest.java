package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.conversation.DeliverySeqUpdate;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.store.delivery.DeliveryStateStore;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationSequenceRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

class DeliveryStateServiceImplTest {
    @Test
    void shouldClampToVisibleMaxAndPersistOnlyActualDeviceHighWatermark() {
        ConversationService conversations = mock(ConversationService.class);
        ConversationStateStore conversationState = mock(ConversationStateStore.class);
        DeliveryStateStore deliveryState = mock(DeliveryStateStore.class);
        DeliverySeqPersistenceWriter writer = mock(DeliverySeqPersistenceWriter.class);
        UserConversation conversation = new UserConversation();
        conversation.setConversationId("s:u1:u2"); conversation.setChatType(ChatType.PRIVATE.getCode());
        conversation.setTargetId("u1");
        when(conversations.getConversation("u2", "s:u1:u2")).thenReturn(conversation);
        when(conversationState.getUserMaxSeq("u2", "s:u1:u2")).thenReturn(12L);
        when(deliveryState.advance("u2", "ios-1", "s:u1:u2", 12L))
                .thenReturn(new DeliveryStateStore.AdvanceResult(12L, true));
        DeliveryStateServiceImpl service = new DeliveryStateServiceImpl(conversations, conversationState, deliveryState, writer,
                mock(UserConversationSyncPointRepository.class), mock(ConversationSequenceRepository.class), null, null);

        DeliverySeqUpdate result = service.acknowledge("u2", "ios-1", "s:u1:u2", 99L, "op-1");

        assertEquals(12L, result.getDeliveredSeq());
        verify(writer).enqueue("u2", "ios-1", "s:u1:u2", 12L);
    }

    @Test
    void shouldFallbackToPersistedUserMaxWhenRedisHotStateIsMissing() {
        ConversationService conversations = mock(ConversationService.class);
        ConversationStateStore conversationState = mock(ConversationStateStore.class);
        DeliveryStateStore deliveryState = mock(DeliveryStateStore.class);
        UserConversationSyncPointRepository syncPoints = mock(UserConversationSyncPointRepository.class);
        UserConversation conversation = new UserConversation();
        conversation.setConversationId("s:u1:u2"); conversation.setChatType(ChatType.PRIVATE.getCode());
        when(conversations.getConversation("u2", "s:u1:u2")).thenReturn(conversation);
        when(syncPoints.getMaxSeq("u2", "s:u1:u2")).thenReturn(7L);
        when(deliveryState.advance("u2", "ios-1", "s:u1:u2", 7L))
                .thenReturn(new DeliveryStateStore.AdvanceResult(7L, true));
        DeliveryStateServiceImpl service = new DeliveryStateServiceImpl(conversations, conversationState, deliveryState,
                mock(DeliverySeqPersistenceWriter.class), syncPoints, mock(ConversationSequenceRepository.class), null, null);

        DeliverySeqUpdate result = service.acknowledge("u2", "ios-1", "s:u1:u2", 99L, "op-2");

        assertEquals(7L, result.getDeliveredSeq());
        verify(deliveryState).advance("u2", "ios-1", "s:u1:u2", 7L);
    }

    @Test
    void unchangedRetryShouldRepairPersistenceAndSenderControlOutbox() {
        ConversationService conversations = mock(ConversationService.class);
        ConversationStateStore conversationState = mock(ConversationStateStore.class);
        DeliveryStateStore deliveryState = mock(DeliveryStateStore.class);
        DeliverySeqPersistenceWriter writer = mock(DeliverySeqPersistenceWriter.class);
        ConversationControlEventRepository controlEvents = mock(ConversationControlEventRepository.class);
        UserConversation conversation = new UserConversation();
        conversation.setConversationId("s:u1:u2");
        conversation.setChatType(ChatType.PRIVATE.getCode());
        conversation.setTargetId("u1");
        when(conversations.getConversation("u2", "s:u1:u2")).thenReturn(conversation);
        when(conversationState.getUserMaxSeq("u2", "s:u1:u2")).thenReturn(12L);
        when(deliveryState.advance("u2", "ios-1", "s:u1:u2", 12L))
                .thenReturn(new DeliveryStateStore.AdvanceResult(12L, false));
        ConversationControlEvent saved = new ConversationControlEvent();
        saved.setEventId("delivery-event-1");
        when(controlEvents.appendPartitioned(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(saved));
        DeliveryStateServiceImpl service = new DeliveryStateServiceImpl(
                conversations, conversationState, deliveryState, writer,
                mock(UserConversationSyncPointRepository.class), mock(ConversationSequenceRepository.class),
                controlEvents, new ObjectMapper());

        DeliverySeqUpdate result = service.acknowledge("u2", "ios-1", "s:u1:u2", 12L, "op-retry");

        assertEquals(false, result.isChanged());
        verify(writer).enqueue("u2", "ios-1", "s:u1:u2", 12L);
        verify(controlEvents).appendPartitioned(argThat(event ->
                event.getType() == ControlEventTypeEnum.DELIVERY_ADVANCED
                        && event.getTargetUserIds().equals(List.of("u1"))
                        && event.getPayload().contains("\"deliveredSeq\":12")));
    }
}
