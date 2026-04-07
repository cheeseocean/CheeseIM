package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.protocol.ProtoOfflinePushEventMapper;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.postman.service.impl.MessagePushServiceImpl;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Component
public class OfflinePushEventListener {

    private final MessagePushServiceImpl  messagePushService;
    @DubboReference
    private       OnlineRouteQueryService onlineRouteQueryService;

    public OfflinePushEventListener(MessagePushServiceImpl messagePushService) {
        this.messagePushService = messagePushService;
    }


    @QueueListener(topic = TopicNames.OFFLINE_PUSH, group = "push-offline")
    public void onMessage(byte[] payload) {
        try {
            handle(ProtoOfflinePushEventMapper.parse(payload));
        } catch (Exception e) {
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
