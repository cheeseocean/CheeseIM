package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryRpc;
import com.cheeseocean.im.common.api.rpc.OnlineDispatchRpc;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeliveryEventListener {

    private final ObjectMapper objectMapper;
    private final OnlineRouteQueryRpc onlineRouteQueryRpc;
    private final OnlineDispatchRpc onlineDispatchRpc;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeliveryEventListener(ObjectMapper objectMapper,
                                 OnlineRouteQueryRpc onlineRouteQueryRpc,
                                 OnlineDispatchRpc onlineDispatchRpc,
                                 KafkaTemplate<String, Object> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.onlineRouteQueryRpc = onlineRouteQueryRpc;
        this.onlineDispatchRpc = onlineDispatchRpc;
        this.kafkaTemplate = kafkaTemplate;
    }

    public DeliveryEventListener(OnlineRouteQueryRpc onlineRouteQueryRpc,
                                 OnlineDispatchRpc onlineDispatchRpc,
                                 KafkaTemplate<String, Object> kafkaTemplate) {
        this(new ObjectMapper(), onlineRouteQueryRpc, onlineDispatchRpc, kafkaTemplate);
    }

    @KafkaListener(topics = TopicNames.DELIVERY, groupId = "push-delivery")
    public void onMessage(String payload) {
        try {
            handle(objectMapper.readValue(payload, DeliveryEvent.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse delivery event payload", e);
        }
    }

    void handle(DeliveryEvent event) {
        if (event == null || event.getMessage() == null || event.getTargetUserIds() == null) {
            return;
        }
        for (String userId : event.getTargetUserIds()) {
            deliverToUser(userId, event.getMessage());
        }
    }

    private void deliverToUser(String userId, SequencedMessage message) {
        List<RouteSnapshot> routes = onlineRouteQueryRpc.findByUser(userId);
        if (routes == null || routes.isEmpty()) {
            emitOfflinePushIfNeeded(userId, message);
            return;
        }

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId(userId);
        req.setPayload(toDispatchPayload(message));

        DispatchMessageResp resp = onlineDispatchRpc.dispatchMessage(req);
        if (!hasSuccessfulDispatch(resp)) {
            emitOfflinePushIfNeeded(userId, message);
        }
    }

    private boolean hasSuccessfulDispatch(DispatchMessageResp resp) {
        if (resp == null || resp.getResults() == null || resp.getResults().isEmpty()) {
            return false;
        }
        for (DispatchResult result : resp.getResults()) {
            if (result.isSuccess()) {
                return true;
            }
        }
        return false;
    }

    private void emitOfflinePushIfNeeded(String userId, SequencedMessage message) {
        if (message.getOptions() == null || !message.getOptions().isNeedOfflinePush()) {
            return;
        }
        kafkaTemplate.send(TopicNames.OFFLINE_PUSH, userId, toOfflinePushEvent(userId, message));
    }

    private DispatchPayload toDispatchPayload(SequencedMessage message) {
        DispatchPayload payload = new DispatchPayload();
        payload.setConversationId(message.getConversationId());
        payload.setSeq(message.getSeq());
        payload.setClientMsgId(message.getClientMsgId());
        payload.setServerMsgId(message.getServerMsgId());
        payload.setContentType(message.getContentType());
        payload.setContent(message.getContent());
        payload.setSendTime(message.getSendTime());
        payload.setExt(message.getExt());
        return payload;
    }

    private OfflinePushEvent toOfflinePushEvent(String userId, SequencedMessage message) {
        OfflinePushEvent event = new OfflinePushEvent();
        event.setUserId(userId);
        event.setConversationId(message.getConversationId());
        event.setSeq(message.getSeq());
        event.setServerMsgId(message.getServerMsgId());
        event.setTitle(message.getSenderId());
        event.setContent(message.getContent());
        event.setExt(message.getExt());
        return event;
    }
}
