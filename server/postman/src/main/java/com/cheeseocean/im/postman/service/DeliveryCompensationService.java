package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.DeliveryTaskCommand;
import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.DeliveryTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeliveryCompensationService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCompensationService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final int maxRetries;
    private final long retryDelaySeconds;

    public DeliveryCompensationService(@Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                                       ObjectMapper objectMapper,
                                       MeterRegistry meterRegistry,
                                       @Value("${cheeseim.delivery.compensation.max-retries:3}") int maxRetries,
                                       @Value("${cheeseim.delivery.compensation.retry-delay-seconds:10}") long retryDelaySeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
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
        publish(KafkaTopics.RETRY, retryTask);
        emitStateChange(retryTask);
        return retryTask;
    }

    public DeliveryTask publishDeadLetter(DeliveryTask task) {
        DeliveryTask deadLetter = copyOf(task);
        deadLetter.moveTo(DeliveryState.FAILED_FINAL);
        publish(KafkaTopics.DLQ, deadLetter);
        emitStateChange(deadLetter);
        return deadLetter;
    }

    public DeliveryTask replay(DeliveryTask task) {
        log.info("delivery_replay messageId={} state={} receiver={} retryCount={}",
                task.getServerMsgId(), task.getState(), task.getReceiverId(), task.getRetryCount());
        meterRegistry.counter("im.delivery.replay", "state", task.getState().name()).increment();
        return task;
    }

    public void recordAttempt(DeliveryTaskCommand task, GatewayPushResult pushResult) {
        String routeState = pushResult.isRouteFound() ? "route_found" : "route_missing";
        meterRegistry.counter("im.delivery.gateway.attempt", "result", routeState).increment();
        log.info("delivery_gateway_attempt messageId={} receiverId={} routeFound={} deliveredDevices={} failedDevices={}",
                task.getMessageId(),
                task.getReceiverId(),
                pushResult.isRouteFound(),
                pushResult.getDeliveredDeviceIds().size(),
                pushResult.getFailedDeviceIds().size());
    }

    private void publish(String topic, DeliveryTask task) {
        try {
            kafkaTemplate.send(topic, task.getServerMsgId(), objectMapper.writeValueAsString(task));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize delivery task", e);
        }
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
