package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.annotation.QueueProducer;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import org.springframework.stereotype.Component;

@Component
@QueueProducer
public class IngressMessagePublisher {

    private final QueueAdapter queueAdapter;

    public IngressMessagePublisher(QueueAdapter queueAdapter) {
        this.queueAdapter = queueAdapter;
    }

    // key 由消息字段计算
    // single chat 和 notification 共享同一 key，进同一批次，由消费方在批次内分流处理。
    public void publish(Message message) {
        String key = ConversationIdUtil.buildQueueKey(
                message.getSessionType(), message.getSenderId(), message.getReceiverId(), message.getGroupId());
        queueAdapter.send(TopicNames.INGRESS, key, ProtoMessageMapper.toProto(message).toByteArray());
    }
}
