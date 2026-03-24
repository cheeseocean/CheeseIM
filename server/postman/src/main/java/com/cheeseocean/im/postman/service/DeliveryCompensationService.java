package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.message.DeliveryTask;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.enums.DeliveryState;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.annotation.QueueProducer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@QueueProducer
public class DeliveryCompensationService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCompensationService.class);

    private final QueueAdapter queueAdapter;
    private final MeterRegistry meterRegistry;
    private final int maxRetries;
    private final long retryDelaySeconds;

    public DeliveryCompensationService(QueueAdapter queueAdapter,
                                       MeterRegistry meterRegistry,
                                       @Value("${cheeseim.delivery.compensation.max-retries:3}") int maxRetries,
                                       @Value("${cheeseim.delivery.compensation.retry-delay-seconds:10}") long retryDelaySeconds) {
        this.queueAdapter = queueAdapter;
        this.meterRegistry = meterRegistry;
        this.maxRetries = maxRetries;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public DeliveryTask handleTimeout(DeliveryTask task) {
        if (task.getRetryCount() >= maxRetries) {
            return publishDeadLetter(task);
        }
        return schedule(task);
    }

    public DeliveryTask schedule(DeliveryTask task) {
        DeliveryTask retryTask = copyOf(task);
        retryTask.markRetryScheduled(task.getRetryCount() + 1, Instant.now().plusSeconds(retryDelaySeconds));
        publish(TopicNames.RETRY, retryTask);
        emitStateChange(retryTask);
        return retryTask;
    }

    public DeliveryTask publishDeadLetter(DeliveryTask task) {
        DeliveryTask deadLetter = copyOf(task);
        deadLetter.moveTo(DeliveryState.FAILED_FINAL);
        publish(TopicNames.DLQ, deadLetter);
        emitStateChange(deadLetter);
        return deadLetter;
    }

    public DeliveryTask replay(DeliveryTask task) {
        log.info("delivery_replay messageId={} state={} receiver={} retryCount={}",
                task.getServerMsgId(), task.getState(), task.getReceiverId(), task.getRetryCount());
        meterRegistry.counter("im.delivery.replay", "state", task.getState().name()).increment();
        return task;
    }

    private void publish(String topic, DeliveryTask task) {
        queueAdapter.send(topic, task.getServerMsgId(), task);
    }

    private void emitStateChange(DeliveryTask task) {
        log.info("delivery_state_change messageId={} state={} receiver={} retryCount={}",
                task.getServerMsgId(), task.getState(), task.getReceiverId(), task.getRetryCount());
        meterRegistry.counter("im.delivery.state", "state", task.getState().name()).increment();
    }

    private DeliveryTask copyOf(DeliveryTask task) {
        DeliveryTask copy = DeliveryTask.newTask(task.getServerMsgId(), task.getReceiverId(), task.getDeviceId());
        copy.moveTo(task.getState());
        copy.setRetryCount(task.getRetryCount());
        copy.setNextRetryAt(task.getNextRetryAt());
        return copy;
    }
}
