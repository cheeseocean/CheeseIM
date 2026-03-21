package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.ReceiptEvent;
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
            kafkaTemplate.send(KafkaTopics.MESSAGE_RECEIPT_TOPIC, event.getConversationId(), event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish receipt event " + event.getEventId(), e);
        }
    }
}
