package com.cheeseocean.im.postman.task;

import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.enums.ControlEventDeliveryStateEnum;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ControlEventDeliverySchedulerTest {

    @Test
    void shouldDeliverOnlyTargetsInClaimedPartitionAndConfirmItIndependently() {
        ConversationControlEventRepository repository = mock(ConversationControlEventRepository.class);
        ControlNotificationDispatcher dispatcher = mock(ControlNotificationDispatcher.class);
        ConversationControlEvent event = event();
        when(repository.findClaimable(100)).thenReturn(List.of(event));
        when(repository.claim("event-shard-7", 30_000L)).thenReturn(Optional.of(event));
        when(dispatcher.dispatch(any())).thenReturn(true);
        when(repository.markDelivered("event-shard-7", "claim-1")).thenReturn(true);
        ControlEventDeliveryScheduler scheduler = new ControlEventDeliveryScheduler(
                repository, ObjectMapperFactory.createDefaultMapper(), 100, 30_000L, 3);
        ReflectionTestUtils.setField(scheduler, "controlNotificationDispatcher", dispatcher);

        scheduler.deliverClaimableEvents();

        verify(dispatcher).dispatch(argThat(request -> "u7".equals(request.getUserId())
                && "event-shard-7".equals(request.getDeliveryId())));
        verify(repository).markDelivered("event-shard-7", "claim-1");
    }

    @Test
    void duplicateCandidateShouldOnlyBeClaimedAndDeliveredOnce() {
        ConversationControlEventRepository repository = mock(ConversationControlEventRepository.class);
        ControlNotificationDispatcher dispatcher = mock(ControlNotificationDispatcher.class);
        ConversationControlEvent event = event();
        when(repository.findClaimable(100)).thenReturn(List.of(event, event));
        when(repository.claim("event-shard-7", 30_000L)).thenReturn(Optional.of(event));
        when(dispatcher.dispatch(any())).thenReturn(true);
        when(repository.markDelivered("event-shard-7", "claim-1")).thenReturn(true);
        ControlEventDeliveryScheduler scheduler = new ControlEventDeliveryScheduler(
                repository, ObjectMapperFactory.createDefaultMapper(), 100, 30_000L, 3);
        ReflectionTestUtils.setField(scheduler, "controlNotificationDispatcher", dispatcher);

        scheduler.deliverClaimableEvents();

        verify(repository, times(1)).claim("event-shard-7", 30_000L);
        verify(dispatcher, times(1)).dispatch(any());
    }

    private ConversationControlEvent event() {
        ConversationControlEvent event = new ConversationControlEvent();
        event.setEventId("event-shard-7");
        event.setCursor(647L);
        event.setConversationId("g:crew");
        event.setType(ControlEventTypeEnum.MESSAGE_REVOKED);
        event.setTargetUserIds(List.of("u7"));
        event.setPayload("{\"conversationId\":\"g:crew\"}");
        event.setDeliveryState(ControlEventDeliveryStateEnum.CLAIMED);
        event.setDeliveryAttempt(1);
        event.setClaimToken("claim-1");
        return event;
    }
}
