package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.event.ConversationSettingsEvent;
import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationSettingsEventListener {

    private static final Logger log = CommonLoggers.POSTMAN;

    private final OnlineRouteQueryService onlineRouteQueryService;
    private final OnlineDispatcher        onlineDispatcher;

    public ConversationSettingsEventListener(OnlineRouteQueryService onlineRouteQueryService,
                                             OnlineDispatcher onlineDispatcher) {
        this.onlineRouteQueryService = onlineRouteQueryService;
        this.onlineDispatcher = onlineDispatcher;
    }

    @QueueListener(topic = TopicNames.CONVERSATION_SETTINGS, group = "push-conversation-settings")
    public void onMessage(ConversationSettingsEvent event) {
        try {
            handle(event);
        } catch (Exception e) {
            log.error("Failed to handle conversation settings event: {}", event, e);
        }
    }

    void handle(ConversationSettingsEvent event) {
        if (event == null || event.getRecipientUserId() == null) {
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

    private DispatchPayload toDispatchPayload(ConversationSettingsEvent event) {
        DispatchPayload payload = new DispatchPayload();
        payload.setServerMsgId("conv-settings:" + event.getConversationId() + ":" + event.getOccurredAt());
        payload.setConversationId(event.getConversationId());
        payload.setContentType(0);
        payload.setContent("refresh");
        payload.setSendTime(event.getOccurredAt());
        payload.getExt().put("notificationType", "conversation_recv_msg_opt_changed");
        payload.getExt().put("conversationId",   event.getConversationId());
        payload.getExt().put("recvMsgOpt",        String.valueOf(event.getRecvMsgOpt()));
        payload.getExt().put("occurredAt",        String.valueOf(event.getOccurredAt()));
        return payload;
    }
}
