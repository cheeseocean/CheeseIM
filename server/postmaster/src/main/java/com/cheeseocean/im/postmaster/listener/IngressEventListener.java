package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.postmaster.service.GroupFanoutPlanner;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.MessagePolicyEngine;
import com.cheeseocean.im.postmaster.service.MessageRouteDecision;
import com.cheeseocean.im.postmaster.service.MessageStateService;
import com.cheeseocean.im.postmaster.service.ConversationSeqService;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngressEventListener {

    private static final Logger log = LoggerFactory.getLogger(IngressEventListener.class);

    private final ObjectMapper objectMapper;
    private final QueueAdapter queueAdapter;
    private final GroupMembershipFacade groupMembershipFacade;
    private final GroupFanoutPlanner groupFanoutPlanner;
    private final ConversationSeqService conversationSeqService;
    private final MessagePolicyEngine messagePolicyEngine;
    private final MessageStateService messageStateService;

    public IngressEventListener(ObjectMapper objectMapper,
                                QueueAdapter queueAdapter,
                                GroupMembershipFacade groupMembershipFacade,
                                GroupFanoutPlanner groupFanoutPlanner,
                                ConversationSeqService conversationSeqService,
                                MessagePolicyEngine messagePolicyEngine,
                                MessageStateService messageStateService) {
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.queueAdapter = queueAdapter;
        this.groupMembershipFacade = groupMembershipFacade;
        this.groupFanoutPlanner = groupFanoutPlanner;
        this.conversationSeqService = conversationSeqService;
        this.messagePolicyEngine = messagePolicyEngine;
        this.messageStateService = messageStateService;
    }

    @QueueListener(topic = TopicNames.INGRESS, group = "postman-ingress", concurrency = 1)
    public void onMessage(IngressEvent event) {
        try {
            handle(event);
        } catch (Exception e) {
            log.error("Failed to handle ingress event: {}", event, e);
        }
    }

    void handle(IngressEvent event) {
        long seq = conversationSeqService.nextSeq(event.getConversationId());
        SequencedMessage message = toSequencedMessage(event, seq);
        MessageRouteDecision decision = messagePolicyEngine.decide(event);

        if (decision.persistHistory()) {
            HistoryEvent historyEvent = new HistoryEvent();
            historyEvent.setConversationId(event.getConversationId());
            historyEvent.setBeginSeq(seq);
            historyEvent.setEndSeq(seq);
            historyEvent.setMessages(List.of(message));
            queueAdapter.send(TopicNames.HISTORY, event.getConversationId(), historyEvent);
        }

        List<String> targets = resolveTargets(message, decision);
        messageStateService.apply(message, targets);

        if (!decision.sendDelivery()) {
            return;
        }

        if (event.getSessionType() != null && event.getSessionType() == SessionType.GROUP.getCode()) {
            routeGroupIngress(message, targets);
            return;
        }
        queueAdapter.send(TopicNames.DELIVERY, event.getConversationId(), singleDelivery(message, targets));
    }

    private void routeGroupIngress(SequencedMessage message, List<String> targets) {
        for (List<String> batch : groupFanoutPlanner.partition(targets)) {
            DeliveryEvent deliveryEvent = new DeliveryEvent();
            deliveryEvent.setConversationId(message.getConversationId());
            deliveryEvent.setMessage(message);
            deliveryEvent.setTargetUserIds(batch);
            queueAdapter.send(TopicNames.DELIVERY, message.getConversationId(), deliveryEvent);
        }
    }

    private DeliveryEvent singleDelivery(SequencedMessage message, List<String> targets) {
        DeliveryEvent deliveryEvent = new DeliveryEvent();
        deliveryEvent.setConversationId(message.getConversationId());
        deliveryEvent.setMessage(message);
        deliveryEvent.setTargetUserIds(targets);
        return deliveryEvent;
    }

    private List<String> resolveTargets(SequencedMessage message, MessageRouteDecision decision) {
        if (message.getSessionType() != null && message.getSessionType() == SessionType.GROUP.getCode()) {
            return groupMembershipFacade.loadTargets(message.getConversationId());
        }
        if (decision.senderSync() && message.getSenderId() != null) {
            return List.of(message.getRecvId(), message.getSenderId());
        }
        return List.of(message.getRecvId());
    }

    private SequencedMessage toSequencedMessage(IngressEvent event, long seq) {
        SequencedMessage message = new SequencedMessage();
        message.setConversationId(event.getConversationId());
        message.setSeq(seq);
        message.setClientMsgId(event.getClientMsgId());
        message.setServerMsgId(event.getServerMsgId());
        message.setSenderId(event.getSenderId());
        message.setRecvId(event.getRecvId());
        message.setGroupId(event.getGroupId());
        message.setSessionType(event.getSessionType());
        message.setContentType(event.getContentType());
        message.setContent(event.getContent());
        message.setSendTime(event.getSendTime());
        message.setOptions(event.getOptions());
        message.setExt(event.getExt());
        return message;
    }
}
