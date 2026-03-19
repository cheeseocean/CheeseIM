package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.DeliveryTask;
import com.cheeseocean.im.postman.service.DeliveryCompensationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryCompensationListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCompensationListener.class);

    private final ObjectMapper objectMapper;
    private final DeliveryCompensationService deliveryCompensationService;

    public DeliveryCompensationListener(ObjectMapper objectMapper,
                                        DeliveryCompensationService deliveryCompensationService) {
        this.objectMapper = objectMapper;
        this.deliveryCompensationService = deliveryCompensationService;
    }

    @KafkaListener(topics = KafkaTopics.DELIVERY_COMPENSATION_TOPIC, groupId = "postman-delivery-compensation")
    public void onCompensation(String payload) {
        try {
            DeliveryTask task = objectMapper.readValue(payload, DeliveryTask.class);
            deliveryCompensationService.replay(task);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse delivery compensation payload: {}", payload, e);
        }
    }
}
