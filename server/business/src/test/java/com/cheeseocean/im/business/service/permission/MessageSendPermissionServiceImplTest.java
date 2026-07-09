package com.cheeseocean.im.business.service.permission;

import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.enums.ReceiveOption;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionRequest;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionResult;
import com.cheeseocean.im.common.api.user.UserInfoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageSendPermissionServiceImplTest {

    @Test
    void checkShouldAggregateBlacklistAndReceiveOptions() {
        FriendRelationService friendRelationService = mock(FriendRelationService.class);
        UserInfoService userInfoService = mock(UserInfoService.class);
        ConversationService conversationService = mock(ConversationService.class);
        MessageSendPermissionServiceImpl service = new MessageSendPermissionServiceImpl(
                friendRelationService,
                userInfoService,
                conversationService);
        MessageSendPermissionRequest request = request();
        when(userInfoService.getReceiveOptions("u2")).thenReturn(ReceiveOption.DO_NOT_DISTURB.getCode());
        when(conversationService.getReceiveOption("u2", "s:u1:u2")).thenReturn(ReceiveOption.RECEIVE.getCode());
        when(friendRelationService.isBlocked("u1", "u2")).thenReturn(false);

        MessageSendPermissionResult result = service.check(request);

        assertFalse(result.isBlockedByReceiver());
        assertEquals(ReceiveOption.DO_NOT_DISTURB.getCode(), result.getGlobalReceiveOption());
        assertEquals(ReceiveOption.RECEIVE.getCode(), result.getConversationReceiveOption());
    }

    @Test
    void checkShouldMarkBlockedWhenReceiverBlockedSender() {
        FriendRelationService friendRelationService = mock(FriendRelationService.class);
        UserInfoService userInfoService = mock(UserInfoService.class);
        ConversationService conversationService = mock(ConversationService.class);
        MessageSendPermissionServiceImpl service = new MessageSendPermissionServiceImpl(
                friendRelationService,
                userInfoService,
                conversationService);
        MessageSendPermissionRequest request = request();
        when(friendRelationService.isBlocked("u1", "u2")).thenReturn(true);

        MessageSendPermissionResult result = service.check(request);

        assertTrue(result.isBlockedByReceiver());
        verifyNoInteractions(userInfoService, conversationService);
    }

    private MessageSendPermissionRequest request() {
        MessageSendPermissionRequest request = new MessageSendPermissionRequest();
        request.setSenderId("u1");
        request.setReceiverId("u2");
        request.setConversationId("s:u1:u2");
        return request;
    }
}
