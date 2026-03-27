package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.postmaster.history.BlockHistoryPersistenceService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class HistoryEventListener {

    private final ObjectMapper                   objectMapper;
    private final BlockHistoryPersistenceService persistenceService;

    public HistoryEventListener(ObjectMapper objectMapper,
                                BlockHistoryPersistenceService persistenceService) {
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.persistenceService = persistenceService;
    }

    @QueueListener(topic = TopicNames.HISTORY, group = "postbox-history", concurrency = 1)
    public void onMessage(HistoryEvent event) {
        try {
            persistenceService.persist(event);
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse history event payload", e);
        }
    }
}
