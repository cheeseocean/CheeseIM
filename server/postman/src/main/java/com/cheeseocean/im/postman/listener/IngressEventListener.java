package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.HistoryTask;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.postman.service.GroupFanoutPlanner;
import com.cheeseocean.im.postman.service.GroupMembershipFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public IngressEventListener(ObjectMapper objectMapper,
                                @Qualifier("postmanObjectKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                                GroupMembershipFacade groupMembershipFacade,
                                GroupFanoutPlanner groupFanoutPlanner) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.groupMembershipFacade = groupMembershipFacade;
        this.groupFanoutPlanner = groupFanoutPlanner;
    }

    @KafkaListener(topics = KafkaTopics.MESSAGE_INGRESS_TOPIC, groupId = "postman-ingress")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, IngressEvent.class));
        } catch (Exception e) {
            log.error("Failed to parse ingress payload: {}", payload, e);
        }
    }

    void handle(IngressEvent event) {
        if (event.isGroupDelivery()) {
            routeGroupIngress(event);
            return;
        }
        kafkaTemplate.send(KafkaTopics.PERSISTENT_TOPIC, event.getConversationId(), HistoryTask.single(event));
    }

    private void routeGroupIngress(IngressEvent event) {
        List<String> members = groupMembershipFacade.loadTargets(event.getConversationId());
        for (List<String> batch : groupFanoutPlanner.partition(members)) {
            kafkaTemplate.send(KafkaTopics.PERSISTENT_TOPIC, event.getConversationId(), HistoryTask.groupBatch(event, batch));
        }
    }
}
