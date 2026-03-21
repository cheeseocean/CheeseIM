package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.GatewayPushService;
import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.DeliveryTaskCommand;
import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.dto.OfflinePushTask;
import com.cheeseocean.im.postman.service.DeliveryCompensationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskListenerTest {

    @Test
    void deliveryListenerShouldCallGatewayPushServiceAndQueueOfflinePushWhenNoRoutesExist() {
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayPushService gatewayPushService = mock(GatewayPushService.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        DeliveryCompensationService compensationService = mock(DeliveryCompensationService.class);

        GatewayPushResult pushResult = new GatewayPushResult();
        pushResult.setReceiverId("userB");
        pushResult.setRouteFound(false);
        when(gatewayPushService.pushToUser(eq("userB"), any())).thenReturn(pushResult);

        DeliveryTaskListener listener = new DeliveryTaskListener(
                objectMapper, gatewayPushService, kafkaTemplate, compensationService);

        DeliveryTaskCommand task = task("msg-1", "userB");
        listener.handle(task);

        verify(gatewayPushService).pushToUser(eq("userB"), any());
        verify(compensationService).recordAttempt(task, pushResult);
        verify(kafkaTemplate).send(eq(KafkaTopics.OFFLINE_PUSH_TOPIC), eq("userB"), any(OfflinePushTask.class));
    }

    @Test
    void deliveryListenerShouldNotQueueOfflinePushWhenGatewayDeliveredOnline() {
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayPushService gatewayPushService = mock(GatewayPushService.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        DeliveryCompensationService compensationService = mock(DeliveryCompensationService.class);

        GatewayPushResult pushResult = new GatewayPushResult();
        pushResult.setReceiverId("userB");
        pushResult.setRouteFound(true);
        pushResult.getDeliveredDeviceIds().add("ios-1");
        when(gatewayPushService.pushToUser(eq("userB"), any())).thenReturn(pushResult);

        DeliveryTaskListener listener = new DeliveryTaskListener(
                objectMapper, gatewayPushService, kafkaTemplate, compensationService);

        DeliveryTaskCommand task = task("msg-2", "userB");
        listener.handle(task);

        verify(gatewayPushService).pushToUser(eq("userB"), any());
        verify(compensationService).recordAttempt(task, pushResult);
        verify(kafkaTemplate, never()).send(eq(KafkaTopics.OFFLINE_PUSH_TOPIC), eq("userB"), any(OfflinePushTask.class));
    }

    private static DeliveryTaskCommand task(String messageId, String receiverId) {
        DeliveryTaskCommand command = new DeliveryTaskCommand();
        command.setEventId("evt-" + messageId);
        command.setTraceId("trace-" + messageId);
        command.setMessageId(messageId);
        command.setConversationId("single:userA:" + receiverId);
        command.setConversationSeq(1001L);
        command.setSenderId("userA");
        command.setReceiverId(receiverId);
        command.setSessionType(1);
        command.setContentType(101);
        command.setContent("hello");
        return command;
    }
}
