package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.postman.service.GroupFanoutPlanner;
import com.cheeseocean.im.postman.service.GroupMembershipFacade;
import com.cheeseocean.im.postman.service.ConversationSeqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngressEventListener {

    private static final Logger log = LoggerFactory.getLogger(IngressEventListener.class);

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GroupMembershipFacade groupMembershipFacade;
    private final GroupFanoutPlanner groupFanoutPlanner;
    private final ConversationSeqService conversationSeqService;

    public IngressEventListener(ObjectMapper objectMapper,
                                KafkaTemplate<String, Object> kafkaTemplate,
                                GroupMembershipFacade groupMembershipFacade,
                                GroupFanoutPlanner groupFanoutPlanner,
                                ConversationSeqService conversationSeqService) {
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.kafkaTemplate = kafkaTemplate;
        this.groupMembershipFacade = groupMembershipFacade;
        this.groupFanoutPlanner = groupFanoutPlanner;
        this.conversationSeqService = conversationSeqService;
    }

    @KafkaListener(topics = TopicNames.INGRESS, groupId = "postman-ingress")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, IngressEvent.class));
        } catch (Exception e) {
            log.error("Failed to parse ingress payload: {}", payload, e);
        }
    }

    void handle(IngressEvent event) {
        long seq = conversationSeqService.nextSeq(event.getConversationId());
        SequencedMessage message = toSequencedMessage(event, seq);

        if (event.getOptions() == null || event.getOptions().isNeedHistory()) {
            HistoryEvent historyEvent = new HistoryEvent();
            historyEvent.setConversationId(event.getConversationId());
            historyEvent.setBeginSeq(seq);
            historyEvent.setEndSeq(seq);
            historyEvent.setMessages(List.of(message));
            kafkaTemplate.send(TopicNames.HISTORY, event.getConversationId(), historyEvent);
        }

        if (event.getOptions() != null && !event.getOptions().isNeedOnlinePush()) {
            return;
        }

        if (event.getSessionType() != null && event.getSessionType() == 2) {
            routeGroupIngress(message);
            return;
        }
        kafkaTemplate.send(TopicNames.DELIVERY, event.getConversationId(), singleDelivery(message));
    }

    private void routeGroupIngress(SequencedMessage message) {
        List<String> members = groupMembershipFacade.loadTargets(message.getConversationId());
        for (List<String> batch : groupFanoutPlanner.partition(members)) {
            DeliveryEvent deliveryEvent = new DeliveryEvent();
            deliveryEvent.setConversationId(message.getConversationId());
            deliveryEvent.setMessage(message);
            deliveryEvent.setTargetUserIds(batch);
            kafkaTemplate.send(TopicNames.DELIVERY, message.getConversationId(), deliveryEvent);
        }
    }

    private DeliveryEvent singleDelivery(SequencedMessage message) {
        DeliveryEvent deliveryEvent = new DeliveryEvent();
        deliveryEvent.setConversationId(message.getConversationId());
        deliveryEvent.setMessage(message);
        deliveryEvent.setTargetUserIds(List.of(message.getRecvId()));
        return deliveryEvent;
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
