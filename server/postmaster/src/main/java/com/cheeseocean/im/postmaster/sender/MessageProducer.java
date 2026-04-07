package com.cheeseocean.im.postmaster.sender;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueProducer;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * @author xxxcrel
 * @date 2026/4/5 22:03
 */
@Component
public class MessageProducer implements QueueProducer<Message> {

    private QueueAdapter queueAdapter;

    public MessageProducer(QueueAdapter queueAdapter) {
        this.queueAdapter = queueAdapter;
    }

    @Override
    public void publish(String key, Message data) {
        queueAdapter.send(TopicNames.DELIVERY, key, ProtoMessageMapper.toProto(data).toByteArray());
    }
}
