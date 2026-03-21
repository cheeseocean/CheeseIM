package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.HistoryTask;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.postman.service.GroupFanoutPlanner;
import com.cheeseocean.im.postman.service.GroupMembershipFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngressEventListenerTest {

    @Test
    void ingressListenerShouldPublishHistoryTaskForSingleChat() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                kafkaTemplate,
                groupMembershipFacade,
                new GroupFanoutPlanner(2));

        listener.handle(singleIngressEvent());

        verify(kafkaTemplate).send(eq(KafkaTopics.HISTORY), anyString(), any(HistoryTask.class));
    }

    @Test
    void ingressListenerShouldSplitGroupMembersIntoBatches() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        GroupMembershipFacade groupMembershipFacade = mock(GroupMembershipFacade.class);
        when(groupMembershipFacade.loadTargets("group:crew")).thenReturn(List.of("u1", "u2", "u3"));

        IngressEventListener listener = new IngressEventListener(
                new ObjectMapper(),
                kafkaTemplate,
                groupMembershipFacade,
                new GroupFanoutPlanner(2));

        listener.handle(groupIngressEvent());

        verify(groupMembershipFacade).loadTargets("group:crew");
        verify(kafkaTemplate, times(2))
                .send(eq(KafkaTopics.HISTORY), anyString(), any(HistoryTask.class));
    }

    private static IngressEvent singleIngressEvent() {
        IngressEvent event = new IngressEvent();
        event.setEventId("evt-single");
        event.setTraceId("trace-single");
        event.setMessageId("msg-single");
        event.setClientMsgId("client-single");
        event.setConversationId("single:userA:userB");
        event.setConversationSeq(1001L);
        event.setSenderId("userA");
        event.setReceiverId("userB");
        event.setSessionType(1);
        event.setContentType(101);
        event.setContent("hello");
        return event;
    }

    private static IngressEvent groupIngressEvent() {
        IngressEvent event = new IngressEvent();
        event.setEventId("evt-group");
        event.setTraceId("trace-group");
        event.setMessageId("msg-group");
        event.setClientMsgId("client-group");
        event.setConversationId("group:crew");
        event.setConversationSeq(2002L);
        event.setSenderId("captain");
        event.setSessionType(2);
        event.setContentType(101);
        event.setContent("assemble");
        return event;
    }
}
