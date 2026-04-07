package com.cheeseocean.im.postmaster.sender;

import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.protocol.ProtoHistoryEventMapper;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueProducer;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * @author xxxcrel
 * @date 2026/4/7 15:14
 */
@Component
public class HistoryEventProducer implements QueueProducer<HistoryEvent> {

    private QueueAdapter queueAdapter;

    public HistoryEventProducer(QueueAdapter queueAdapter) {
        this.queueAdapter = queueAdapter;
    }

    @Override
    public void publish(String key, HistoryEvent data) {
        queueAdapter.send(TopicNames.HISTORY, key, ProtoHistoryEventMapper.toProto(data).toByteArray());
    }
}
