package com.cheeseocean.im.business.service.friend;

import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.core.notification.NotificationSender;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendRealtimeNotifierTest {

    @Test
    void friendRequestCreatedShouldSendNotificationToBothParticipants() {
        NotificationSender notificationSender = mock(NotificationSender.class);
        when(notificationSender.sendToUser(any(), any(), any(), any(), any(), any()))
                .thenReturn(new SendMessageResp());

        FriendRealtimeNotifier notifier = new FriendRealtimeNotifier(notificationSender);

        notifier.friendRequestCreated("userA", "userB");

        verify(notificationSender, times(2)).sendToUser(
                eq("userA"),
                any(),
                eq(ContentType.SYSTEM_NOTIFY),
                eq("friend_request_created"),
                any(),
                any()
        );
    }
}
