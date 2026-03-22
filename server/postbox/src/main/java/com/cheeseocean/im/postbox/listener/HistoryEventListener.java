package com.cheeseocean.im.postbox.listener;

import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.postbox.history.BlockHistoryPersistenceService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HistoryEventListener {

    private final BlockHistoryPersistenceService persistenceService;

    public HistoryEventListener(BlockHistoryPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @KafkaListener(
            topics = TopicNames.HISTORY,
            groupId = "postbox-history",
            containerFactory = "historyEventKafkaListenerContainerFactory"
    )
    public void onMessage(HistoryEvent event) {
        persistenceService.persist(event);
    }
}
