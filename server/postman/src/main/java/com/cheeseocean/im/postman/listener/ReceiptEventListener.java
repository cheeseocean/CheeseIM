package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.receipt.ReceiptAckReq;
import com.cheeseocean.im.common.api.rpc.ReceiptAckRpc;
import com.cheeseocean.im.common.constants.KafkaTopics;
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
    private final ReceiptAckRpc receiptAckRpc;

    public ReceiptEventListener(ObjectMapper objectMapper, ReceiptAckRpc receiptAckRpc) {
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.receiptAckRpc = receiptAckRpc;
    }

    @KafkaListener(topics = KafkaTopics.Message.RECEIPT, groupId = "postman-receipt")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, ReceiptEvent.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse receipt event payload", e);
        }
    }

    void handle(ReceiptEvent event) {
        ReceiptAckReq ack = new ReceiptAckReq();
        ack.setServerMsgId(event.getServerMsgId());
        ack.setConversationId(event.getConversationId());
        ack.setUserId(event.getUserId());
        ack.setDeviceId(event.getDeviceId());
        ack.setEventTime(event.getReceiptTime());
        ack.setSeq(event.getSeq());
        ack.setAckType(event.isReadCursor() ? "READ" : "RECEIVED");
        receiptAckRpc.apply(ack);
    }
}
