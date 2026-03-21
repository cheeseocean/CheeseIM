package com.cheeseocean.im.postbox.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.DeliveryTaskCommand;
import com.cheeseocean.im.common.dto.HistoryTask;
import com.cheeseocean.im.postbox.service.HistoryTaskPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HistoryTaskListener {

    private static final Logger log = LoggerFactory.getLogger(HistoryTaskListener.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final HistoryTaskPersistenceService persistenceService;

    public HistoryTaskListener(@Qualifier("postboxObjectKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                               HistoryTaskPersistenceService persistenceService) {
        this.kafkaTemplate = kafkaTemplate;
        this.persistenceService = persistenceService;
    }

    @KafkaListener(
            topics = KafkaTopics.HISTORY,
            groupId = "postbox-history",
            containerFactory = "historyTaskKafkaListenerContainerFactory"
    )
    public void onMessage(HistoryTask task) {
        HistoryTaskPersistenceService.PersistedHistory persisted = persistenceService.persist(task);
        if (!persisted.isNewlyPersisted()) {
            log.debug("Skipping delivery publish for duplicate history task: messageId={}", task.getMessageId());
            return;
        }
        for (String receiverId : resolveReceivers(task)) {
            DeliveryTaskCommand command = DeliveryTaskCommand.from(task, receiverId);
            kafkaTemplate.send(KafkaTopics.DELIVERY, command.deliveryKey(), command);
        }
        log.debug("Persisted history task: messageId={}, sequence={}",
                persisted.getMessage().getServerMsgId(), persisted.firstStoredSequence());
    }

    private List<String> resolveReceivers(HistoryTask task) {
        if (task.getReceiverId() != null && !task.getReceiverId().isBlank()) {
            return List.of(task.getReceiverId());
        }
        return task.getTargetUserIds() == null ? List.of() : task.getTargetUserIds();
    }
}
