package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageSendRpcImplTest {

    @Test
    void sendMessagePublishesIngressEventWithConversationIdAndServerMsgId() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        IngressEventPublisher publisher = new IngressEventPublisher(kafkaTemplate);
        MessageSendRpcImpl service = new MessageSendRpcImpl(publisher);

        SendMessageReq req = new SendMessageReq();
        req.setRequestId("req-1");
        req.setSenderId("u200");
        req.setSessionType(SessionType.SINGLE.getCode());
        req.setRecvId("u100");
        req.setClientMsgId("cmsg-1");
        req.setContentType(ContentType.TEXT.getCode());
        req.setContent("hello");
        req.setSendTime(123L);

        SendMessageResp resp = service.sendMessage(req);

        var eventCaptor = forClass(IngressEvent.class);
        verify(kafkaTemplate).send(eq("ingress"), eq("c1:u100:u200"), eventCaptor.capture());

        IngressEvent event = eventCaptor.getValue();
        assertEquals("c1:u100:u200", resp.getConversationId());
        assertNotNull(resp.getServerMsgId());
        assertEquals(resp.getServerMsgId(), event.getServerMsgId());
        assertEquals("u200", event.getSenderId());
    }

    @Test
    void sendMessageFillsDefaultOptionsWhenMissing() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        IngressEventPublisher publisher = new IngressEventPublisher(kafkaTemplate);
        MessageSendRpcImpl service = new MessageSendRpcImpl(publisher);

        SendMessageReq req = new SendMessageReq();
        req.setRequestId("req-2");
        req.setSenderId("u100");
        req.setSessionType(SessionType.GROUP.getCode());
        req.setGroupId("g1");
        req.setClientMsgId("cmsg-2");
        req.setContentType(ContentType.TEXT.getCode());
        req.setContent("team");

        SendMessageResp resp = service.sendMessage(req);

        assertTrue(resp.isAccepted());
        verify(kafkaTemplate).send(eq("ingress"), eq("c2:g1"), org.mockito.ArgumentMatchers.any(IngressEvent.class));
    }

    @Test
    void sendMessageFillsMissingOptionFieldsWithDefaults() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        IngressEventPublisher publisher = new IngressEventPublisher(kafkaTemplate);
        MessageSendRpcImpl service = new MessageSendRpcImpl(publisher);

        MessageOptions options = new MessageOptions();
        options.setNeedHistory(false);

        SendMessageReq req = new SendMessageReq();
        req.setRequestId("req-3");
        req.setSenderId("u100");
        req.setSessionType(SessionType.SINGLE.getCode());
        req.setRecvId("u200");
        req.setClientMsgId("cmsg-3");
        req.setContentType(ContentType.TEXT.getCode());
        req.setContent("hello");
        req.setOptions(options);

        var eventCaptor = forClass(IngressEvent.class);
        service.sendMessage(req);

        verify(kafkaTemplate).send(eq("ingress"), eq("c1:u100:u200"), eventCaptor.capture());
        MessageOptions actual = eventCaptor.getValue().getOptions();
        assertTrue(!actual.isNeedHistory());
        assertTrue(actual.isNeedConversation());
        assertTrue(actual.isNeedUnreadCount());
        assertTrue(actual.isNeedOnlinePush());
        assertTrue(actual.isNeedOfflinePush());
        assertTrue(actual.isSenderSync());
        assertTrue(actual.isNeedLastMessage());
    }

    @Test
    void sendMessageShouldUseTypingDefaults() {
        IngressEvent event = publishEventForContentType(SessionType.SINGLE.getCode(), ContentType.TYPING.getCode());

        MessageOptions options = event.getOptions();
        assertEquals(false, options.isNeedHistory());
        assertEquals(false, options.isNeedConversation());
        assertEquals(false, options.isNeedUnreadCount());
        assertEquals(true, options.isNeedOnlinePush());
        assertEquals(false, options.isNeedOfflinePush());
        assertEquals(false, options.isNeedLastMessage());
    }

    @Test
    void sendMessageShouldUseReadReceiptDefaults() {
        IngressEvent event = publishEventForContentType(SessionType.SINGLE.getCode(), ContentType.READ_RECEIPT.getCode());

        MessageOptions options = event.getOptions();
        assertEquals(false, options.isNeedHistory());
        assertEquals(true, options.isNeedConversation());
        assertEquals(false, options.isNeedUnreadCount());
        assertEquals(true, options.isNeedOnlinePush());
        assertEquals(false, options.isNeedOfflinePush());
        assertEquals(false, options.isNeedLastMessage());
    }

    @Test
    void sendMessageShouldUseRevokeDefaults() {
        IngressEvent event = publishEventForContentType(SessionType.SINGLE.getCode(), ContentType.REVOKE_NOTIFY.getCode());

        MessageOptions options = event.getOptions();
        assertEquals(true, options.isNeedHistory());
        assertEquals(true, options.isNeedConversation());
        assertEquals(false, options.isNeedUnreadCount());
        assertEquals(true, options.isNeedOnlinePush());
        assertEquals(false, options.isNeedOfflinePush());
        assertEquals(true, options.isSenderSync());
        assertEquals(true, options.isNotification());
        assertEquals(true, options.isNeedLastMessage());
    }

    @Test
    void sendMessageShouldUseNotificationDefaultsForSystemNotify() {
        IngressEvent event = publishEventForContentType(SessionType.NOTIFICATION.getCode(), ContentType.SYSTEM_NOTIFY.getCode());

        MessageOptions options = event.getOptions();
        assertEquals(true, options.isNeedHistory());
        assertEquals(true, options.isNeedConversation());
        assertEquals(true, options.isNeedUnreadCount());
        assertEquals(true, options.isNeedOnlinePush());
        assertEquals(false, options.isNeedOfflinePush());
        assertEquals(false, options.isSenderSync());
        assertEquals(true, options.isNotification());
        assertEquals(true, options.isNeedLastMessage());
    }

    @Test
    void sendMessageShouldUseSilentNotificationDefaultsForForceLogout() {
        IngressEvent event = publishEventForContentType(SessionType.NOTIFICATION.getCode(), ContentType.FORCE_LOGOUT.getCode());

        MessageOptions options = event.getOptions();
        assertEquals(false, options.isNeedHistory());
        assertEquals(false, options.isNeedConversation());
        assertEquals(false, options.isNeedUnreadCount());
        assertEquals(false, options.isNeedOnlinePush());
        assertEquals(false, options.isNeedOfflinePush());
        assertEquals(false, options.isSenderSync());
        assertEquals(true, options.isNotification());
        assertEquals(false, options.isNeedLastMessage());
    }

    private IngressEvent publishEventForContentType(int sessionType, int contentType) {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        IngressEventPublisher publisher = new IngressEventPublisher(kafkaTemplate);
        MessageSendRpcImpl service = new MessageSendRpcImpl(publisher);

        SendMessageReq req = new SendMessageReq();
        req.setRequestId("req-type-" + contentType);
        req.setSenderId("u100");
        req.setSessionType(sessionType);
        req.setRecvId("u200");
        req.setClientMsgId("cmsg-type-" + contentType);
        req.setContentType(contentType);
        req.setContent("payload");

        var eventCaptor = forClass(IngressEvent.class);
        service.sendMessage(req);
        verify(kafkaTemplate).send(eq("ingress"), eq(sessionType == SessionType.NOTIFICATION.getCode() ? "c3:u200" : "c1:u100:u200"), eventCaptor.capture());
        return eventCaptor.getValue();
    }
}
