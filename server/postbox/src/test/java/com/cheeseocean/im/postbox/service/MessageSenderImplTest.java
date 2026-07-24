package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ReceiveOption;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionRequest;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionResult;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionService;
import com.cheeseocean.im.common.core.store.idempotency.message.MessageSendInboxStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageSenderImplTest {

    @Test
    void sendMessageShouldUseAggregatedPermissionAndDisableOfflinePushWhenDisturbed() {
        IngressMessagePublisher publisher = mock(IngressMessagePublisher.class);
        MessageSendPermissionService permissionService = mock(MessageSendPermissionService.class);
        MessageSendInboxStore inboxStore = acquiredInboxStore();
        MessageSenderImpl sender = new MessageSenderImpl(publisher, inboxStore);
        ReflectionTestUtils.setField(sender, "messageSendPermissionService", permissionService);
        when(permissionService.check(any(MessageSendPermissionRequest.class))).thenReturn(
                MessageSendPermissionResult.allow(
                        ReceiveOption.DO_NOT_DISTURB.getCode(),
                        ReceiveOption.RECEIVE.getCode()));
        Message message = privateTextMessage();

        SendMessageResp response = sender.sendMessage(new SendMessageReq(message));

        assertTrue(response.isAccepted());
        assertNotNull(message.getServerMsgId());
        assertFalse(message.getOptions().getNeedOfflinePush());
        verify(permissionService).check(any(MessageSendPermissionRequest.class));
        verify(publisher).publish(message);
    }

    @Test
    void sendMessageShouldRejectWhenAggregatedPermissionIsBlocked() {
        IngressMessagePublisher publisher = mock(IngressMessagePublisher.class);
        MessageSendPermissionService permissionService = mock(MessageSendPermissionService.class);
        MessageSenderImpl sender = new MessageSenderImpl(publisher, acquiredInboxStore());
        ReflectionTestUtils.setField(sender, "messageSendPermissionService", permissionService);
        when(permissionService.check(any(MessageSendPermissionRequest.class))).thenReturn(
                MessageSendPermissionResult.blocked(
                        ReceiveOption.RECEIVE.getCode(),
                        ReceiveOption.RECEIVE.getCode()));
        Message message = privateTextMessage();

        SendMessageResp response = sender.sendMessage(new SendMessageReq(message));

        assertFalse(response.isAccepted());
        verify(permissionService).check(any(MessageSendPermissionRequest.class));
        verify(publisher, never()).publish(any(Message.class));
    }

    private Message privateTextMessage() {
        Message message = new Message();
        message.setClientMsgId("client-1");
        message.setSenderId("u1");
        message.setReceiverId("u2");
        message.setChatType(ChatType.PRIVATE);
        message.setContentType(ContentType.TEXT);
        return message;
    }

    private MessageSendInboxStore acquiredInboxStore() {
        MessageSendInboxStore store = mock(MessageSendInboxStore.class);
        when(store.claim(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> new MessageSendInboxStore.Claim(
                        MessageSendInboxStore.ClaimStatus.ACQUIRED,
                        invocation.getArgument(2),
                        1_000L,
                        0L,
                        null));
        when(store.bindEffectiveOfflinePush(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(store.markAccepted(anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(3));
        return store;
    }
}
