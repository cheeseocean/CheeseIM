package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.message.DeliveryTask;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.enums.DeliveryState;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeliveryCompensationServiceTest {

    @Test
    void timedOutOnlineDeliveryShouldScheduleCompensation() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        DeliveryCompensationService service = new DeliveryCompensationService(
                queueAdapter,
                new SimpleMeterRegistry(),
                3,
                10L);

        DeliveryTask task = DeliveryTask.newTask("s-1", "userB", "ios-1");
        task.moveTo(DeliveryState.ONLINE_DELIVERING);

        DeliveryTask scheduled = service.handleTimeout(task);

        assertEquals(1, scheduled.getRetryCount());
        assertEquals(DeliveryState.FAILED_RECOVERABLE, scheduled.getState());
        assertNotNull(scheduled.getNextRetryAt());
        verify(queueAdapter).send(eq(TopicNames.RETRY), eq("s-1"), eq(scheduled));
    }

    @Test
    void exhaustedRetriesShouldPublishDeadLetterEvent() {
        QueueAdapter queueAdapter = mock(QueueAdapter.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DeliveryCompensationService service = new DeliveryCompensationService(
                queueAdapter,
                meterRegistry,
                2,
                10L);

        DeliveryTask task = DeliveryTask.newTask("s-2", "userB", "ios-1");
        task.moveTo(DeliveryState.FAILED_RECOVERABLE);
        task.setRetryCount(2);

        DeliveryTask deadLettered = service.handleTimeout(task);

        assertEquals(DeliveryState.FAILED_FINAL, deadLettered.getState());
        verify(queueAdapter).send(eq(TopicNames.DLQ), eq("s-2"), eq(deadLettered));
        assertEquals(1.0d, meterRegistry.get("im.delivery.state").tag("state", "FAILED_FINAL").counter().count());
    }
}
