package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.GatewayPushService;
import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.DeliveryTaskCommand;
import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.dto.OfflinePushTask;
import com.cheeseocean.im.postman.service.DeliveryCompensationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "cheeseim.message-flow",
        name = "async-delivery-enabled",
        havingValue = "true"
)
public class DeliveryTaskListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTaskListener.class);

    private final ObjectMapper objectMapper;
    private final GatewayPushService gatewayPushService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DeliveryCompensationService deliveryCompensationService;

    public DeliveryTaskListener(ObjectMapper objectMapper,
                                GatewayPushService gatewayPushService,
                                @Qualifier("postmanObjectKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                                DeliveryCompensationService deliveryCompensationService) {
        this.objectMapper = objectMapper;
        this.gatewayPushService = gatewayPushService;
        this.kafkaTemplate = kafkaTemplate;
        this.deliveryCompensationService = deliveryCompensationService;
    }

    @KafkaListener(topics = KafkaTopics.DELIVERY, groupId = "postman-delivery")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, DeliveryTaskCommand.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse delivery task payload", e);
        }
    }

    void handle(DeliveryTaskCommand task) {
        GatewayPushResult pushResult = gatewayPushService.pushToUser(task.getReceiverId(), toMessageProto(task));
        deliveryCompensationService.recordAttempt(task, pushResult);
        if (!pushResult.isRouteFound() || pushResult.getDeliveredDeviceIds().isEmpty()) {
            kafkaTemplate.send(KafkaTopics.OFFLINE_PUSH, task.deliveryKey(), OfflinePushTask.from(task));
            log.debug("Queued offline push fallback for messageId={}, receiverId={}",
                    task.getMessageId(), task.getReceiverId());
        }
    }

    private MessageProto toMessageProto(DeliveryTaskCommand task) {
        MessageProto proto = new MessageProto();
        proto.setServerMsgId(task.getMessageId());
        proto.setConversationId(task.getConversationId());
        proto.setConversationSeq(task.getConversationSeq());
        proto.setSenderId(task.getSenderId());
        proto.setReceiverId(task.getReceiverId());
        proto.setContent(task.getContent());
        proto.setContentType(task.getContentType());
        proto.setSessionType(task.getSessionType());
        proto.setAttachedInfo(task.getAttachedInfo());
        return proto;
    }
}
