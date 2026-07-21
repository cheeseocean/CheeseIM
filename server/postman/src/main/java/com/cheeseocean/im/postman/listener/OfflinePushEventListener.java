package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.enums.OfflinePushTriggerReason;
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
    @DubboReference(check = false)
    private       OnlineRouteQueryService onlineRouteQueryService;

    public OfflinePushEventListener(MessagePushServiceImpl messagePushService) {
        this.messagePushService = messagePushService;
    }


    @QueueListener(topic = TopicNames.OFFLINE_PUSH, group = "push-offline")
    public void onMessage(byte[] payload) {
        try {
            handle(ProtoOfflinePushEventMapper.parse(payload));
        } catch (java.io.IOException exception) {
            // 交给 QueueAdapter 的有限重试 + DLT，避免毒消息被日志吞掉后直接提交 offset。
            throw new IllegalArgumentException("Malformed offline push protobuf payload", exception);
        }
    }

    void handle(OfflinePushEvent event) {
        OfflinePushTriggerReason reason = event.getTriggerReason();
        boolean nodeFailureCompensation = reason == OfflinePushTriggerReason.NODE_DELIVERY_FAILED
                || reason == OfflinePushTriggerReason.NODE_DELIVERY_TIMEOUT;
        if (!nodeFailureCompensation
                && !onlineRouteQueryService.findByUser(event.getUserId()).isEmpty()) {
            return;
        }
        // 触发原因只服务于内部消费决策，不下发给厂商或客户端。
        event.setTriggerReason(null);
        messagePushService.pushOffline(event);
    }
}
