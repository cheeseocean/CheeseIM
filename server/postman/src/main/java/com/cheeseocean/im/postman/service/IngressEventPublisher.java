package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.postman.metrics.MessageFlowMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class IngressEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessageFlowMetrics messageFlowMetrics;

    public IngressEventPublisher(@Qualifier("objectKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                                 MessageFlowMetrics messageFlowMetrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageFlowMetrics = messageFlowMetrics;
    }

    public void publish(IngressEvent event) {
        kafkaTemplate.send(KafkaTopics.MESSAGE_INGRESS_TOPIC, event.getConversationId(), event);
        messageFlowMetrics.recordAcceptedIngress();
    }
}
