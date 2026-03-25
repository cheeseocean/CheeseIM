package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.event.FriendRelationEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryRpc;
import com.cheeseocean.im.common.api.rpc.OnlineDispatchRpc;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FriendRelationEventListener {
    private static final Logger       log = LoggerFactory.getLogger(FriendRelationEventListener.class);
    private final    ObjectMapper objectMapper;
    private final OnlineRouteQueryRpc onlineRouteQueryRpc;
    private final OnlineDispatchRpc onlineDispatchRpc;

    public FriendRelationEventListener(ObjectMapper objectMapper,
                                       OnlineRouteQueryRpc onlineRouteQueryRpc,
                                       OnlineDispatchRpc onlineDispatchRpc) {
        this.objectMapper = objectMapper;
        this.onlineRouteQueryRpc = onlineRouteQueryRpc;
        this.onlineDispatchRpc = onlineDispatchRpc;
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
        List<?> routes = onlineRouteQueryRpc.findByUser(event.getRecipientUserId());
        if (routes == null || routes.isEmpty()) {
            return;
        }

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId(event.getRecipientUserId());
        req.setPayload(toDispatchPayload(event));
        onlineDispatchRpc.dispatchMessage(req);
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
