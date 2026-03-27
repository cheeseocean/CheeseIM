package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.annotation.QueueProducer;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import org.springframework.stereotype.Component;

@Component
@QueueProducer
public class IngressEventPublisher {

    private final QueueAdapter queueAdapter;

    public IngressEventPublisher(QueueAdapter queueAdapter) {
        this.queueAdapter = queueAdapter;
    }

    // key 由消息字段计算
    // single chat 和 notification 共享同一 key，进同一批次，由消费方在批次内分流处理。
    public void publish(IngressEvent event) {
        String key = ConversationIdUtil.buildQueueKey(
                event.getSessionType(), event.getSenderId(), event.getRecvId(), event.getGroupId());
        queueAdapter.send(TopicNames.INGRESS, key, event);
    }
}
