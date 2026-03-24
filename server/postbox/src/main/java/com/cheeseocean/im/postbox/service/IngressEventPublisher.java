package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.annotation.QueueProducer;
import org.springframework.stereotype.Component;

@Component
@QueueProducer
public class IngressEventPublisher {

    private final QueueAdapter queueAdapter;

    public IngressEventPublisher(QueueAdapter queueAdapter) {
        this.queueAdapter = queueAdapter;
    }

    public void publish(IngressEvent event) {
        queueAdapter.send(TopicNames.INGRESS, event.getConversationId(), event);
    }
}
