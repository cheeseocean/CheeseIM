package com.cheeseocean.im.common.api.contract;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.FriendRelationEvent;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.enums.MessagePreviewType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageContractsTest {

    @Test
    void sendMessageContractsCarryMessageIdentityAndPolicy() {
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);

        SendMessageReq req = new SendMessageReq();
        req.setRequestId("req-1");
        req.setSenderId("u100");
        req.setSessionType(1);
        req.setRecvId("u200");
        req.setClientMsgId("cmsg-1");
        req.setContentType(101);
        req.setContent("hello");
        req.setSendTime(123L);
        req.setOptions(options);
        req.setExt(Map.of("lang", "zh"));

        SendMessageResp resp = new SendMessageResp();
        resp.setAccepted(true);
        resp.setRequestId(req.getRequestId());
        resp.setConversationId("c1:u100:u200");
        resp.setClientMsgId(req.getClientMsgId());
        resp.setServerMsgId("smsg-1");

        assertEquals("u200", req.getRecvId());
        assertTrue(req.getOptions().isNeedHistory());
        assertEquals("c1:u100:u200", resp.getConversationId());
        assertEquals("smsg-1", resp.getServerMsgId());
    }

    @Test
    void ingressHistoryAndDeliveryContractsShareSequencedMessage() {
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOfflinePush(true);

        SequencedMessage message = new SequencedMessage();
        message.setConversationId("c1:u100:u200");
        message.setSeq(9L);
        message.setClientMsgId("cmsg-1");
        message.setServerMsgId("smsg-1");
        message.setSenderId("u100");
        message.setRecvId("u200");
        message.setSessionType(1);
        message.setContentType(101);
        message.setContent("hello");
        message.setSendTime(123L);
        message.setOptions(options);
        message.setExt(Map.of("format", "text"));

        IngressEvent ingressEvent = new IngressEvent();
        ingressEvent.setConversationId(message.getConversationId());
        ingressEvent.setClientMsgId(message.getClientMsgId());
        ingressEvent.setServerMsgId(message.getServerMsgId());
        ingressEvent.setSenderId(message.getSenderId());
        ingressEvent.setRecvId(message.getRecvId());
        ingressEvent.setSessionType(message.getSessionType());
        ingressEvent.setContentType(message.getContentType());
        ingressEvent.setContent(message.getContent());
        ingressEvent.setSendTime(message.getSendTime());
        ingressEvent.setOptions(message.getOptions());

        HistoryEvent historyEvent = new HistoryEvent();
        historyEvent.setConversationId(message.getConversationId());
        historyEvent.setBeginSeq(message.getSeq());
        historyEvent.setEndSeq(message.getSeq());
        historyEvent.setMessages(List.of(message));

        DeliveryEvent deliveryEvent = new DeliveryEvent();
        deliveryEvent.setConversationId(message.getConversationId());
        deliveryEvent.setMessage(message);
        deliveryEvent.setTargetUserIds(List.of("u100", "u200"));

        assertEquals(9L, historyEvent.getBeginSeq());
        assertEquals("u200", deliveryEvent.getTargetUserIds().get(1));
        assertTrue(ingressEvent.getOptions().isNeedOfflinePush());
    }

    @Test
    void dispatchContractsCarryConnectionTargetsAndPayload() {
        DispatchPayload payload = new DispatchPayload();
        payload.setConversationId("c1:u100:u200");
        payload.setSeq(9L);
        payload.setServerMsgId("smsg-1");
        payload.setContentType(101);
        payload.setContent("hello");

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("u200");
        req.setConnectionIds(List.of("conn-1", "conn-2"));
        req.setPayload(payload);

        assertEquals("conn-1", req.getConnectionIds().get(0));
        assertEquals("smsg-1", req.getPayload().getServerMsgId());
    }

    @Test
    void conversationLastMessageSummaryCarriesProjectionFields() {
        ConversationLastMessageSummary summary = new ConversationLastMessageSummary();
        summary.setSeq(11L);
        summary.setSenderId("system");
        summary.setContent("System notice");
        summary.setContentType(7001);
        summary.setPreviewText("系统通知");
        summary.setPreviewType(MessagePreviewType.SYSTEM);
        summary.setSendTime(123L);
        summary.setNotification(true);

        assertEquals(11L, summary.getSeq());
        assertEquals("system", summary.getSenderId());
        assertEquals(7001, summary.getContentType());
        assertEquals("系统通知", summary.getPreviewText());
        assertEquals(MessagePreviewType.SYSTEM, summary.getPreviewType());
        assertTrue(summary.isNotification());
    }

    @Test
    void friendRelationEventCarriesRecipientActorAndEventType() {
        FriendRelationEvent event = new FriendRelationEvent();
        event.setRecipientUserId("u200");
        event.setActorUserId("u100");
        event.setPeerUserId("u300");
        event.setEventType("friend_request_created");
        event.setOccurredAt(123L);

        assertEquals("u200", event.getRecipientUserId());
        assertEquals("u100", event.getActorUserId());
        assertEquals("u300", event.getPeerUserId());
        assertEquals("friend_request_created", event.getEventType());
        assertEquals(123L, event.getOccurredAt());
    }
}
