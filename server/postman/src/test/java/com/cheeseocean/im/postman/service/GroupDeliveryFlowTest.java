package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.GatewayPushService;
import com.cheeseocean.im.common.api.MessagePushService;
import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.StoredMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupDeliveryFlowTest {

    @Test
    void groupMessageShouldCreateOneStoredFactAndMultipleInboxTargets() {
        MessageIdempotencyService idempotencyService = mock(MessageIdempotencyService.class);
        MessageStoreService storeService = mock(MessageStoreService.class);
        GatewayPushService gatewayPushService = mock(GatewayPushService.class);
        MessagePushService messagePushService = mock(MessagePushService.class);
        DeliveryCompensationService compensationService = mock(DeliveryCompensationService.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);

        when(idempotencyService.findExisting("userA", "group:g-1", "c-g-1")).thenReturn(Optional.empty());

        StoredMessage stored = new StoredMessage();
        stored.setServerMsgId("s-g-1");
        stored.setConversationId("group:g-1");
        stored.setSenderId("userA");
        when(storeService.saveMessage(any(StoredMessage.class))).thenReturn(stored);
        when(groupMembershipFacade.loadTargets("group:g-1")).thenReturn(List.of("userB", "userC"));
        when(storeService.saveOfflineMessages(any(), eq(List.of("userB", "userC")))).thenReturn(List.of(11L, 12L));

        MessageDeliveryServiceImpl service = new MessageDeliveryServiceImpl(
                idempotencyService,
                new DeliveryStateMachine(),
                storeService,
                gatewayPushService,
                messagePushService,
                compensationService,
                new GroupFanoutPlanner(500),
                null,
                groupMembershipFacade,
                null,
                null,
                new com.cheeseocean.im.postman.config.MessageFlowProperties());

        DeliveryResult result = service.deliver(DeliveryCommand.builder()
                .clientMsgId("c-g-1")
                .conversationId("group:g-1")
                .senderId("userA")
                .deviceId("ios-1")
                .content("group hello")
                .contentType(101)
                .sessionType(2)
                .build());

        assertEquals(DeliveryState.PUSH_TRIGGERED, result.getState());
        assertEquals(11L, result.getStoredMessageId());
        verify(groupMembershipFacade).loadTargets("group:g-1");
        verify(storeService).saveMessage(any(StoredMessage.class));
        verify(storeService).saveOfflineMessages(any(), eq(List.of("userB", "userC")));
    }

    @Test
    void groupMessageShouldFailWhenNoTargetsCanBeResolved() {
        MessageIdempotencyService idempotencyService = mock(MessageIdempotencyService.class);
        MessageStoreService storeService = mock(MessageStoreService.class);
        GatewayPushService gatewayPushService = mock(GatewayPushService.class);
        MessagePushService messagePushService = mock(MessagePushService.class);
        DeliveryCompensationService compensationService = mock(DeliveryCompensationService.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);

        when(idempotencyService.findExisting("userA", "group:g-2", "c-g-2")).thenReturn(Optional.empty());
        when(groupMembershipFacade.loadTargets("group:g-2")).thenReturn(List.of());
        when(storeService.saveMessage(any(StoredMessage.class))).thenReturn(new StoredMessage());

        MessageDeliveryServiceImpl service = new MessageDeliveryServiceImpl(
                idempotencyService,
                new DeliveryStateMachine(),
                storeService,
                gatewayPushService,
                messagePushService,
                compensationService,
                new GroupFanoutPlanner(500),
                null,
                groupMembershipFacade,
                null,
                null,
                new com.cheeseocean.im.postman.config.MessageFlowProperties());

        assertThrows(IllegalStateException.class, () -> service.deliver(DeliveryCommand.builder()
                .clientMsgId("c-g-2")
                .conversationId("group:g-2")
                .senderId("userA")
                .deviceId("ios-1")
                .content("group hello")
                .contentType(101)
                .sessionType(2)
                .build()));
    }
}
