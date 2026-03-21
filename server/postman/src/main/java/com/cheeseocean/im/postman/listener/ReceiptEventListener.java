package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.ReceiptEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "cheeseim.message-flow",
        name = "async-receipt-enabled",
        havingValue = "true"
)
public class ReceiptEventListener {

    private final ObjectMapper objectMapper;
    private final MessageStoreService messageStoreService;

    public ReceiptEventListener(ObjectMapper objectMapper, MessageStoreService messageStoreService) {
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.messageStoreService = messageStoreService;
    }

    @KafkaListener(topics = KafkaTopics.MESSAGE_RECEIPT_TOPIC, groupId = "postman-receipt")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, ReceiptEvent.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse receipt event payload", e);
        }
    }

    void handle(ReceiptEvent event) {
        DeliveryAck ack = new DeliveryAck();
        ack.setServerMsgId(event.getServerMsgId());
        ack.setConversationId(event.getConversationId());
        ack.setUserId(event.getUserId());
        ack.setDeviceId(event.getDeviceId());
        ack.setEventTime(event.getReceiptTime());
        ack.setSeq(event.getSeq());
        ack.setAckType(event.isReadCursor() ? "READ" : "RECEIVED");
        messageStoreService.applyAck(ack);
    }
}
