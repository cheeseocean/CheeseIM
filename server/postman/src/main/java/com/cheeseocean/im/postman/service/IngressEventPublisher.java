package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.postman.metrics.MessageFlowMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class IngressEventPublisher {

    private static final long SEND_TIMEOUT_MILLIS = Duration.ofSeconds(5).toMillis();

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessageFlowMetrics messageFlowMetrics;

    public IngressEventPublisher(@Qualifier("postmanObjectKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                                 MessageFlowMetrics messageFlowMetrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageFlowMetrics = messageFlowMetrics;
    }

    public void publish(IngressEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.MESSAGE_INGRESS_TOPIC, event.getConversationId(), event)
                    .get(SEND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            messageFlowMetrics.recordAcceptedIngress();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish ingress event " + event.getMessageId(), e);
        }
    }
}
