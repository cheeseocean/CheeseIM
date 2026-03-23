package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.postman.service.ConversationSeqService;
import com.cheeseocean.im.postman.service.DefaultMessagePolicyEngine;
import com.cheeseocean.im.postman.service.GroupFanoutPlanner;
import com.cheeseocean.im.postman.service.GroupMembershipFacade;
import com.cheeseocean.im.postman.service.MessageStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngressEventListenerTest {

    @Test
    void ingressListenerShouldPublishHistoryAndDeliveryForSingleChat() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.nextSeq("c1:userA:userB")).thenReturn(1001L);
        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                kafkaTemplate,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService);

        listener.handle(singleIngressEvent());

        var historyCaptor = forClass(HistoryEvent.class);
        var deliveryCaptor = forClass(DeliveryEvent.class);
        verify(kafkaTemplate).send(eq(TopicNames.HISTORY), eq("c1:userA:userB"), historyCaptor.capture());
        verify(kafkaTemplate).send(eq(TopicNames.DELIVERY), eq("c1:userA:userB"), deliveryCaptor.capture());

        SequencedMessage message = historyCaptor.getValue().getMessages().get(0);
        assertEquals(1001L, message.getSeq());
        assertEquals("userB", deliveryCaptor.getValue().getTargetUserIds().get(0));
    }

    @Test
    void ingressListenerShouldSplitGroupMembersIntoBatchesAndPublishDelivery() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(groupMembershipFacade.loadTargets("c2:crew")).thenReturn(List.of("u1", "u2", "u3"));
        when(conversationSeqService.nextSeq("c2:crew")).thenReturn(2002L);

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                kafkaTemplate,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService);

        listener.handle(groupIngressEvent());

        verify(groupMembershipFacade).loadTargets("c2:crew");
        verify(kafkaTemplate).send(eq(TopicNames.HISTORY), eq("c2:crew"), any(HistoryEvent.class));
        verify(kafkaTemplate, times(2))
                .send(eq(TopicNames.DELIVERY), eq("c2:crew"), any(DeliveryEvent.class));
    }

    @Test
    void ingressListenerShouldSkipDeliveryWhenOnlinePushIsDisabled() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.nextSeq("c1:userA:userB")).thenReturn(1001L);

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                kafkaTemplate,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService);

        IngressEvent event = singleIngressEvent();
        event.getOptions().setNeedOnlinePush(false);

        listener.handle(event);

        verify(kafkaTemplate).send(eq(TopicNames.HISTORY), eq("c1:userA:userB"), any(HistoryEvent.class));
        verify(kafkaTemplate, times(0)).send(eq(TopicNames.DELIVERY), eq("c1:userA:userB"), any(DeliveryEvent.class));
    }

    @Test
    void ingressListenerShouldAddSenderToDeliveryTargetsWhenSenderSyncEnabled() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.nextSeq("c1:userA:userB")).thenReturn(1002L);

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                kafkaTemplate,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService);

        IngressEvent event = singleIngressEvent();
        event.getOptions().setSenderSync(true);

        var deliveryCaptor = forClass(DeliveryEvent.class);
        listener.handle(event);

        verify(kafkaTemplate).send(eq(TopicNames.DELIVERY), eq("c1:userA:userB"), deliveryCaptor.capture());
        assertEquals(List.of("userB", "userA"), deliveryCaptor.getValue().getTargetUserIds());
    }

    @Test
    void ingressListenerShouldApplyMessageStatePolicy() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);
        when(conversationSeqService.nextSeq("c1:userA:userB")).thenReturn(1003L);

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                kafkaTemplate,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService);

        IngressEvent event = singleIngressEvent();
        event.getOptions().setNeedConversation(true);
        event.getOptions().setNeedUnreadCount(true);
        event.getOptions().setNeedLastMessage(true);

        listener.handle(event);

        verify(messageStateService).apply(any(SequencedMessage.class), eq(List.of("userB")));
    }

    @Test
    void readReceiptShouldNotReachIngressPipeline() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        ConversationSeqService conversationSeqService = mock(ConversationSeqService.class);
        MessageStateService messageStateService = mock(MessageStateService.class);

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                kafkaTemplate,
                groupMembershipFacade,
                new GroupFanoutPlanner(2),
                conversationSeqService,
                new DefaultMessagePolicyEngine(),
                messageStateService);

        assertThrows(IllegalStateException.class, () -> listener.handle(readReceiptIngressEvent()));
    }

    private static IngressEvent singleIngressEvent() {
        IngressEvent event = new IngressEvent();
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        options.setNeedOfflinePush(true);
        options.setSenderSync(false);
        event.setRequestId("req-single");
        event.setClientMsgId("client-single");
        event.setConversationId("c1:userA:userB");
        event.setServerMsgId("msg-single");
        event.setSenderId("userA");
        event.setRecvId("userB");
        event.setSessionType(SessionType.SINGLE.getCode());
        event.setContentType(ContentType.TEXT.getCode());
        event.setContent("hello");
        event.setOptions(options);
        return event;
    }

    private static IngressEvent groupIngressEvent() {
        IngressEvent event = new IngressEvent();
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(true);
        options.setNeedOnlinePush(true);
        options.setNeedOfflinePush(true);
        options.setSenderSync(true);
        event.setRequestId("req-group");
        event.setClientMsgId("client-group");
        event.setConversationId("c2:crew");
        event.setServerMsgId("msg-group");
        event.setSenderId("captain");
        event.setGroupId("crew");
        event.setSessionType(SessionType.GROUP.getCode());
        event.setContentType(ContentType.TEXT.getCode());
        event.setContent("assemble");
        event.setOptions(options);
        return event;
    }

    private static IngressEvent readReceiptIngressEvent() {
        IngressEvent event = new IngressEvent();
        MessageOptions options = new MessageOptions();
        options.setNeedConversation(true);
        event.setRequestId("req-read");
        event.setClientMsgId("client-read");
        event.setConversationId("c1:userA:userB");
        event.setSenderId("userA");
        event.setRecvId("userB");
        event.setSessionType(SessionType.SINGLE.getCode());
        event.setContentType(ContentType.READ_RECEIPT.getCode());
        event.setContent("{\"receiptType\":\"READ_CURSOR\",\"seq\":19}");
        event.setOptions(options);
        return event;
    }
}
