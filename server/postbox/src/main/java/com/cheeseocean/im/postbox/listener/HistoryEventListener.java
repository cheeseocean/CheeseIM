package com.cheeseocean.im.postbox.listener;

import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.postbox.history.BlockHistoryPersistenceService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HistoryEventListener {

    private final ObjectMapper objectMapper;
    private final BlockHistoryPersistenceService persistenceService;

    public HistoryEventListener(ObjectMapper objectMapper,
                                BlockHistoryPersistenceService persistenceService) {
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.persistenceService = persistenceService;
    }

    @KafkaListener(topics = TopicNames.HISTORY, groupId = "postbox-history")
    public void onMessage(String payload) {
        try {
            persistenceService.persist(objectMapper.readValue(payload, HistoryEvent.class));
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse history event payload", e);
        }
    }
}
