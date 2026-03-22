package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.event.ReceiptEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class GatewayReceiptPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public GatewayReceiptPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ReceiptEvent event) {
        try {
            kafkaTemplate.send(TopicNames.RECEIPT, event.getConversationId(), event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish receipt event " + event.getEventId(), e);
        }
    }
}
