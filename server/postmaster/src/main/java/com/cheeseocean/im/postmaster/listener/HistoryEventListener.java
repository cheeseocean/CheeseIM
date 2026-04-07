package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.protocol.ProtoHistoryEventMapper;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.postmaster.history.BlockHistoryPersistenceService;
import org.springframework.stereotype.Component;

/**
 * 历史消息队列Listener
 * @author xxxcrel
 */
@Component
public class HistoryEventListener {

    private final BlockHistoryPersistenceService persistenceService;

    public HistoryEventListener(BlockHistoryPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @QueueListener(topic = TopicNames.HISTORY, group = "postbox-history", concurrency = 1)
    public void onMessage(byte[] payload) {
        try {
            HistoryEvent event = ProtoHistoryEventMapper.parse(payload);
            persistenceService.persist(event);
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse history event payload", e);
        }
    }
}
