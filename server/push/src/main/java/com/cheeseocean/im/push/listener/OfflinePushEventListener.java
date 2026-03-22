package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryRpc;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.push.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OfflinePushEventListener {

    private final ObjectMapper objectMapper;
    private final MessagePushServiceImpl messagePushService;
    private final OnlineRouteQueryRpc onlineRouteQueryRpc;

    public OfflinePushEventListener(ObjectMapper objectMapper,
                                    MessagePushServiceImpl messagePushService,
                                    OnlineRouteQueryRpc onlineRouteQueryRpc) {
        this.objectMapper = objectMapper;
        this.messagePushService = messagePushService;
        this.onlineRouteQueryRpc = onlineRouteQueryRpc;
    }

    @KafkaListener(topics = TopicNames.OFFLINE_PUSH, groupId = "push-offline")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, OfflinePushEvent.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse offline push event payload", e);
        }
    }

    void handle(OfflinePushEvent event) {
        if (!onlineRouteQueryRpc.findByUser(event.getUserId()).isEmpty()) {
            return;
        }
        messagePushService.pushOffline(event);
    }
}
