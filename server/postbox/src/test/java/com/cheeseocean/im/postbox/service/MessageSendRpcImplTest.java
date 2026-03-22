package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.event.IngressEvent;
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
        req.setSessionType(1);
        req.setRecvId("u100");
        req.setClientMsgId("cmsg-1");
        req.setContentType(101);
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
        req.setSessionType(2);
        req.setGroupId("g1");
        req.setClientMsgId("cmsg-2");
        req.setContentType(101);
        req.setContent("team");

        SendMessageResp resp = service.sendMessage(req);

        assertTrue(resp.isAccepted());
        verify(kafkaTemplate).send(eq("ingress"), eq("c2:g1"), org.mockito.ArgumentMatchers.any(IngressEvent.class));
    }
}
