package com.cheeseocean.im.postbox.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.DeliveryTaskCommand;
import com.cheeseocean.im.common.dto.HistoryTask;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.service.HistoryTaskPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryTaskListenerTest {

    @Test
    void onMessageShouldPersistSingleTaskAndPublishDeliveryCommand() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        HistoryTaskPersistenceService persistenceService = mock(HistoryTaskPersistenceService.class);
        when(persistenceService.persist(any())).thenReturn(persistedHistory("msg-1", 1001L));
        HistoryTaskListener listener = new HistoryTaskListener(kafkaTemplate, persistenceService);
        listener.onMessage(singleTask());

        verify(persistenceService).persist(any(HistoryTask.class));
        verify(kafkaTemplate).send(eq(KafkaTopics.DELIVERY), eq("userB"), any(DeliveryTaskCommand.class));
    }

    @Test
    void onMessageShouldPublishDeliveryCommandPerBatchReceiver() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        HistoryTaskPersistenceService persistenceService = mock(HistoryTaskPersistenceService.class);
        when(persistenceService.persist(any())).thenReturn(persistedHistory("msg-group", 2002L, 2002L));
        HistoryTaskListener listener = new HistoryTaskListener(kafkaTemplate, persistenceService);
        listener.onMessage(groupTask());

        verify(kafkaTemplate, times(2))
                .send(eq(KafkaTopics.DELIVERY), anyString(), any(DeliveryTaskCommand.class));
    }

    @Test
    void onMessageShouldSkipPublishWhenTaskWasAlreadyPersisted() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        HistoryTaskPersistenceService persistenceService = mock(HistoryTaskPersistenceService.class);
        when(persistenceService.persist(any())).thenReturn(persistedHistory(false, "msg-1", 1001L));
        HistoryTaskListener listener = new HistoryTaskListener(kafkaTemplate, persistenceService);
        listener.onMessage(singleTask());

        verify(persistenceService).persist(any(HistoryTask.class));
        verify(kafkaTemplate, times(0))
                .send(eq(KafkaTopics.DELIVERY), anyString(), any(DeliveryTaskCommand.class));
    }

    private static HistoryTask singleTask() {
        HistoryTask task = new HistoryTask();
        task.setEventId("evt-1");
        task.setTraceId("trace-1");
        task.setMessageId("msg-1");
        task.setClientMsgId("client-1");
        task.setConversationId("single:userA:userB");
        task.setConversationSeq(1001L);
        task.setSenderId("userA");
        task.setReceiverId("userB");
        task.setSessionType(1);
        task.setContentType(101);
        task.setContent("hello");
        return task;
    }

    private static HistoryTask groupTask() {
        HistoryTask task = new HistoryTask();
        task.setEventId("evt-group");
        task.setTraceId("trace-group");
        task.setMessageId("msg-group");
        task.setClientMsgId("client-group");
        task.setConversationId("group:crew");
        task.setConversationSeq(2002L);
        task.setSenderId("captain");
        task.setSessionType(2);
        task.setContentType(101);
        task.setContent("assemble");
        task.setTargetUserIds(List.of("u1", "u2"));
        return task;
    }

    private static HistoryTaskPersistenceService.PersistedHistory persistedHistory(String serverMsgId,
                                                                                   Long... storedSequences) {
        return persistedHistory(true, serverMsgId, storedSequences);
    }

    private static HistoryTaskPersistenceService.PersistedHistory persistedHistory(boolean newlyPersisted,
                                                                                   String serverMsgId,
                                                                                   Long... storedSequences) {
        MessageDocument message = new MessageDocument();
        message.setServerMsgId(serverMsgId);
        message.setSequence(storedSequences.length == 0 ? null : storedSequences[0]);
        return new HistoryTaskPersistenceService.PersistedHistory(message, List.of(storedSequences), newlyPersisted);
    }
}
