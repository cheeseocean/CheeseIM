package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngressEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public IngressEventPublisher(@Qualifier("postboxObjectKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(IngressEvent event) {
        kafkaTemplate.send(TopicNames.INGRESS, event.getConversationId(), event);
    }
}
