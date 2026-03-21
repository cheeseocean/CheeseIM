package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.DeliveryTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeliveryCompensationServiceTest {

    @Test
    void timedOutOnlineDeliveryShouldScheduleCompensation() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeliveryCompensationService service = new DeliveryCompensationService(
                kafkaTemplate,
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                3,
                10L);

        DeliveryTask task = DeliveryTask.newTask("s-1", "userB", "ios-1");
        task.moveTo(DeliveryState.ONLINE_DELIVERING);

        DeliveryTask scheduled = service.handleTimeout(task);

        assertEquals(1, scheduled.getRetryCount());
        assertEquals(DeliveryState.FAILED_RECOVERABLE, scheduled.getState());
        assertNotNull(scheduled.getNextRetryAt());
        verify(kafkaTemplate).send(eq(KafkaTopics.RETRY), eq("s-1"), contains("\"retryCount\":1"));
    }

    @Test
    void exhaustedRetriesShouldPublishDeadLetterEvent() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DeliveryCompensationService service = new DeliveryCompensationService(
                kafkaTemplate,
                new ObjectMapper(),
                meterRegistry,
                2,
                10L);

        DeliveryTask task = DeliveryTask.newTask("s-2", "userB", "ios-1");
        task.moveTo(DeliveryState.FAILED_RECOVERABLE);
        task.setRetryCount(2);

        DeliveryTask deadLettered = service.handleTimeout(task);

        assertEquals(DeliveryState.FAILED_FINAL, deadLettered.getState());
        verify(kafkaTemplate).send(eq(KafkaTopics.DLQ), eq("s-2"), contains("\"serverMsgId\":\"s-2\""));
        assertEquals(1.0d, meterRegistry.get("im.delivery.state").tag("state", "FAILED_FINAL").counter().count());
    }
}
