package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.event.FriendRelationEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FriendRelationEventListener {
    private static final Logger       log = CommonLoggers.POSTMAN;
    private final ObjectMapper            objectMapper;
    private final OnlineRouteQueryService onlineRouteQueryService;
    private final OnlineDispatcher        onlineDispatcher;

    public FriendRelationEventListener(ObjectMapper objectMapper,
                                       OnlineRouteQueryService onlineRouteQueryService,
                                       OnlineDispatcher onlineDispatcher) {
        this.objectMapper = objectMapper;
        this.onlineRouteQueryService = onlineRouteQueryService;
        this.onlineDispatcher = onlineDispatcher;
    }

    @QueueListener(topic = TopicNames.FRIEND_RELATION, group = "push-friend-relation")
    public void onMessage(FriendRelationEvent event) {
        try {
            handle(event);
        } catch (Exception e) {
            log.error("Failed to handle ingress event: {}", event, e);
        }
    }

    void handle(FriendRelationEvent event) {
        if (event == null || event.getRecipientUserId() == null || event.getEventType() == null) {
            return;
        }
        List<?> routes = onlineRouteQueryService.findByUser(event.getRecipientUserId());
        if (routes == null || routes.isEmpty()) {
            return;
        }

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId(event.getRecipientUserId());
        req.setPayload(toDispatchPayload(event));
        onlineDispatcher.dispatchMessage(req);
    }

    private DispatchPayload toDispatchPayload(FriendRelationEvent event) {
        DispatchPayload payload = new DispatchPayload();
        payload.setServerMsgId("friend-event:" + event.getEventType() + ":" + event.getRecipientUserId() + ":" + event.getOccurredAt());
        payload.setConversationId("social:friends:" + event.getRecipientUserId() + ":" + event.getPeerUserId());
        payload.setContentType(0);
        payload.setContent("refresh");
        payload.setSendTime(event.getOccurredAt());
        payload.getExt().put("notificationType", event.getEventType());
        payload.getExt().put("eventType", event.getEventType());
        payload.getExt().put("actorUserId", event.getActorUserId());
        payload.getExt().put("peerUserId", event.getPeerUserId());
        payload.getExt().put("recipientUserId", event.getRecipientUserId());
        payload.getExt().put("occurredAt", String.valueOf(event.getOccurredAt()));
        return payload;
    }
}
