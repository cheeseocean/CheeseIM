package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.postman.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OfflinePushEventListener {

    private final ObjectMapper objectMapper;
    private final MessagePushServiceImpl  messagePushService;
    private final OnlineRouteQueryService onlineRouteQueryService;

    public OfflinePushEventListener(ObjectMapper objectMapper,
                                    MessagePushServiceImpl messagePushService,
                                    OnlineRouteQueryService onlineRouteQueryService) {
        this.objectMapper = objectMapper;
        this.messagePushService = messagePushService;
        this.onlineRouteQueryService = onlineRouteQueryService;
    }

    @QueueListener(topic = TopicNames.OFFLINE_PUSH, group = "push-offline")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, OfflinePushEvent.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse offline push event payload", e);
        }
    }

    void handle(OfflinePushEvent event) {
        if (!onlineRouteQueryService.findByUser(event.getUserId()).isEmpty()) {
            return;
        }
        messagePushService.pushOffline(event);
    }
}
