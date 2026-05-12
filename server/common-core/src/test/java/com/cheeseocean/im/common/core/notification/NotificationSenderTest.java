package com.cheeseocean.im.common.core.notification;

import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessageSource;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSenderTest {

    @Test
    void sendToUserShouldWrapPayloadIntoNotificationMessage() {
        MessageSender messageSender = mock(MessageSender.class);
        when(messageSender.sendMessage(any())).thenReturn(new SendMessageResp());

        NotificationSender sender = new NotificationSender(ObjectMapperFactory.createDefaultMapper());
        ReflectionTestUtils.setField(sender, "messageSender", messageSender);

        sender.sendToUser("system", "u100", ContentType.SYSTEM_NOTIFY, Map.of("event", "friend_request"));

        var reqCaptor = forClass(SendMessageReq.class);
        verify(messageSender).sendMessage(reqCaptor.capture());

        SendMessageReq req = reqCaptor.getValue();
        assertEquals("system", req.getMsg().getSenderId());
        assertEquals("u100", req.getMsg().getReceiverId());
        assertEquals(ChatType.NOTIFICATION, req.getMsg().getChatType());
        assertEquals(ContentType.SYSTEM_NOTIFY, req.getMsg().getContentType());
        assertEquals(MessageSource.SYSTEM, req.getMsg().getSource());
        assertEquals(PlatformType.UNKNOWN, req.getMsg().getPlatformType());
        assertEquals(false, req.getMsg().getOptions().getNeedConversation());
        assertEquals(false, req.getMsg().getOptions().getNeedUnreadCount());
        assertEquals(true, req.getMsg().getOptions().getNeedOfflinePush());
        assertEquals("System notifications", req.getMsg().getOfflinePushInfo().getTitle());
        assertNotNull(req.getMsg().getClientMsgId());
        assertEquals("{\"event\":\"friend_request\"}", new String(req.getMsg().getContent(), StandardCharsets.UTF_8));
    }

    @Test
    void sendToUsersShouldDeduplicateReceivers() {
        MessageSender messageSender = mock(MessageSender.class);
        when(messageSender.sendMessage(any())).thenReturn(new SendMessageResp());

        NotificationSender sender = new NotificationSender(ObjectMapperFactory.createDefaultMapper());
        ReflectionTestUtils.setField(sender, "messageSender", messageSender);

        sender.sendToUsers("system", List.of("u100", "u200", "u100"), ContentType.SYSTEM_NOTIFY, Map.of("ok", true));

        verify(messageSender, times(2)).sendMessage(any());
    }

    @Test
    void sendShouldRequireGroupIdForGroupSession() {
        MessageSender messageSender = mock(MessageSender.class);
        NotificationSender sender = new NotificationSender(ObjectMapperFactory.createDefaultMapper());
        ReflectionTestUtils.setField(sender, "messageSender", messageSender);

        assertThrows(
                IllegalArgumentException.class,
                () -> sender.send("system", null, null, ContentType.SYSTEM_NOTIFY, ChatType.GROUP, Map.of())
        );
    }
}
