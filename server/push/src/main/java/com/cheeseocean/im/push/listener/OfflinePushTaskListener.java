package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.OfflinePushTask;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import com.cheeseocean.im.push.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OfflinePushTaskListener {

    private final ObjectMapper objectMapper;
    private final MessagePushServiceImpl messagePushService;
    private final OnlineRouteService onlineRouteService;

    public OfflinePushTaskListener(ObjectMapper objectMapper,
                                   MessagePushServiceImpl messagePushService,
                                   OnlineRouteService onlineRouteService) {
        this.objectMapper = objectMapper;
        this.messagePushService = messagePushService;
        this.onlineRouteService = onlineRouteService;
    }

    @KafkaListener(topics = KafkaTopics.OFFLINE_PUSH_TOPIC, groupId = "push-offline")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, OfflinePushTask.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse offline push task payload", e);
        }
    }

    void handle(OfflinePushTask task) {
        if (!onlineRouteService.findByUser(task.getReceiverId()).isEmpty()) {
            return;
        }
        messagePushService.pushOffline(task);
    }
}
