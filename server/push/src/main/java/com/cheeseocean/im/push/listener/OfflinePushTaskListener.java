package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.OfflinePushTask;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryRpc;
import com.cheeseocean.im.push.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OfflinePushTaskListener {

    private final ObjectMapper objectMapper;
    private final MessagePushServiceImpl messagePushService;
    private final OnlineRouteQueryRpc onlineRouteQueryRpc;

    public OfflinePushTaskListener(ObjectMapper objectMapper,
                                   MessagePushServiceImpl messagePushService,
                                   OnlineRouteQueryRpc onlineRouteQueryRpc) {
        this.objectMapper = objectMapper;
        this.messagePushService = messagePushService;
        this.onlineRouteQueryRpc = onlineRouteQueryRpc;
    }

    @KafkaListener(topics = KafkaTopics.Message.OFFLINE_PUSH, groupId = "push-offline")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, OfflinePushTask.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse offline push task payload", e);
        }
    }

    void handle(OfflinePushTask task) {
        if (!onlineRouteQueryRpc.findByUser(task.getReceiverId()).isEmpty()) {
            return;
        }
        messagePushService.pushOffline(task);
    }
}
