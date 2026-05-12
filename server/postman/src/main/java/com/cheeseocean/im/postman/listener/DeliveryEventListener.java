package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.protocol.ProtoOfflinePushEventMapper;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import org.slf4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DeliveryEventListener {
    private static final Logger       log = CommonLoggers.POSTMAN;
    private final OnlineRouteQueryService onlineRouteQueryService;
    private final OnlineDispatcher        onlineDispatcher;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeliveryEventListener(OnlineRouteQueryService onlineRouteQueryService,
                                 OnlineDispatcher onlineDispatcher,
                                 KafkaTemplate<String, Object> kafkaTemplate) {
        this.onlineRouteQueryService = onlineRouteQueryService;
        this.onlineDispatcher = onlineDispatcher;
        this.kafkaTemplate = kafkaTemplate;
    }

    @QueueListener(topic = TopicNames.DELIVERY, group = "push-delivery")
    public void onMessage(Message message) {
        try {
            handle(message);
        } catch (Exception e) {
            log.error("Failed to handle delivery message: {}", message, e);
        }
    }

    void handle(Message message) {
        if (message == null) {
            return;
        }
        for (String userId : resolveTargets(message)) {
            deliverToUser(userId, message);
        }
    }

    private List<String> resolveTargets(Message message) {
        if (message.getChatType() == ChatType.GROUP) {
            log.warn("Skipping group delivery because target fanout data is not attached to message: groupId={}", message.getGroupId());
            return List.of();
        }
        if (message.getReceiverId() == null || message.getReceiverId().isBlank()) {
            return List.of();
        }
        return List.of(message.getReceiverId());
    }

    private void deliverToUser(String userId, Message message) {
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

    private void emitOfflinePushIfNeeded(String userId, Message message) {
        if (message.getOptions() == null || !Boolean.TRUE.equals(message.getOptions().getNeedOfflinePush())) {
            return;
        }
        kafkaTemplate.send(TopicNames.OFFLINE_PUSH, userId, ProtoOfflinePushEventMapper.toProto(toOfflinePushEvent(userId, message)).toByteArray());
    }

    private DispatchPayload toDispatchPayload(Message message) {
        DispatchPayload payload = new DispatchPayload();
        payload.setMsg(message);
        return payload;
    }

    private OfflinePushEvent toOfflinePushEvent(String userId, Message message) {
        OfflinePushEvent event = new OfflinePushEvent();
        event.setUserId(userId);
        event.setConversationId(ConversationIdUtil.buildConversationId(message));
        event.setSeq(message.getSeq());
        event.setServerMsgId(message.getServerMsgId());
        event.setSenderId(message.getSenderId());
        event.setSessionType(message.getChatType() == null ? null : message.getChatType().getCode());
        event.setContentType(message.getContentType() == null ? null : message.getContentType().getCode());
        event.setNotification(message.getOptions() != null && Boolean.TRUE.equals(message.getOptions().getNotification()));
        event.setTitle(message.getSenderId());
        event.setContent(message.getContent() == null ? null : new String(message.getContent(), StandardCharsets.UTF_8));
        event.setAttributes(message.getAttributes());
        return event;
    }
}
