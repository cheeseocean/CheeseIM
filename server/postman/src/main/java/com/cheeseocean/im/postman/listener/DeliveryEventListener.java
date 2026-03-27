package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.api.event.DeliveryEvent;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeliveryEventListener {
    private static final Logger       log = CommonLoggers.POSTMAN;
    private final ObjectMapper            objectMapper;
    private final OnlineRouteQueryService onlineRouteQueryService;
    private final OnlineDispatcher        onlineDispatcher;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeliveryEventListener(ObjectMapper objectMapper,
                                 OnlineRouteQueryService onlineRouteQueryService,
                                 OnlineDispatcher onlineDispatcher,
                                 KafkaTemplate<String, Object> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.onlineRouteQueryService = onlineRouteQueryService;
        this.onlineDispatcher = onlineDispatcher;
        this.kafkaTemplate = kafkaTemplate;
    }

    @QueueListener(topic = TopicNames.DELIVERY, group = "push-delivery")
    public void onMessage(DeliveryEvent event) {
        try {
            handle(event);
        } catch (Exception e) {
            log.error("Failed to handle ingress event: {}", event, e);
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
        List<RouteSnapshot> routes = onlineRouteQueryService.findByUser(userId);
        if (routes == null || routes.isEmpty()) {
            emitOfflinePushIfNeeded(userId, message);
            return;
        }

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId(userId);
        req.setPayload(toDispatchPayload(message));

        DispatchMessageResp resp = onlineDispatcher.dispatchMessage(req);
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
        if (message.getOptions() == null || !Boolean.TRUE.equals(message.getOptions().isNeedOfflinePush())) {
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
        payload.getExt().put("senderId", message.getSenderId());
        payload.getExt().put("recvId", message.getRecvId());
        return payload;
    }

    private OfflinePushEvent toOfflinePushEvent(String userId, SequencedMessage message) {
        OfflinePushEvent event = new OfflinePushEvent();
        event.setUserId(userId);
        event.setConversationId(message.getConversationId());
        event.setSeq(message.getSeq());
        event.setServerMsgId(message.getServerMsgId());
        event.setSenderId(message.getSenderId());
        event.setSessionType(message.getSessionType());
        event.setContentType(message.getContentType());
        event.setNotification(message.getOptions() != null && Boolean.TRUE.equals(message.getOptions().isNotification()));
        event.setTitle(message.getSenderId());
        event.setContent(message.getContent());
        event.setExt(message.getExt());
        return event;
    }
}
