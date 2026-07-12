package com.cheeseocean.im.postman.service.impl;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.push.OfflinePushReq;
import com.cheeseocean.im.common.api.dto.push.PushResult;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.rpc.OfflinePusher;
import com.cheeseocean.im.common.api.enums.DeliveryState;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.postman.entity.OfflinePushResult;
import com.cheeseocean.im.postman.entity.PushAttempt;
import com.cheeseocean.im.postman.service.OfflinePushService;
import com.cheeseocean.im.postman.service.PushDecisionService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.cheeseocean.im.postman.state.PushStateStore;

@Service
@DubboService(interfaceClass = OfflinePusher.class)
public class MessagePushServiceImpl implements OfflinePusher {

    private final OfflinePushService offlinePushService;
    private final PushDecisionService decisionService;
    private final PushStateStore pushStateStore;

    public MessagePushServiceImpl(OfflinePushService offlinePushService,
                                  PushDecisionService decisionService,
                                  PushStateStore pushStateStore) {
        this.offlinePushService = offlinePushService;
        this.decisionService = decisionService;
        this.pushStateStore = pushStateStore;
    }

    @Override
    public PushResult pushOffline(OfflinePushReq req) {
        if (req == null || req.getUserId() == null || req.getServerMsgId() == null) {
            return PushResult.failed(req == null ? null : req.getUserId(), "invalid-request");
        }
        PushDecisionService.PushDecision decision = decisionService.decide(
                pushStateStore.claimPush(req.getServerMsgId(), req.getUserId()));

        if (!decision.shouldPush()) {
            return PushResult.failed(req.getUserId(), decision.reason());
        }

        OfflinePushResult result = offlinePushService.pushMessageToUser(toMessage(req), req.getUserId());
        return result.isSuccess() ? PushResult.success(req.getUserId(), "offline-push") : PushResult.failed(req.getUserId(), result.getErrorMessage());
    }

    public PushResult pushOffline(OfflinePushEvent event) {
        return pushOffline(toRequest(event));
    }

    @Override
    public void cancelPending(String serverMsgId, String userId) {
        pushStateStore.cancelAttempt(serverMsgId, userId);
        pushStateStore.recordDeliveryState(serverMsgId, userId, DeliveryState.READ);
    }

    public void recordDeliveryState(String serverMsgId, DeliveryState state) {
        recordDeliveryState(serverMsgId, null, state);
    }

    public void recordDeliveryState(String serverMsgId, String userId, DeliveryState state) {
        String resolvedUserId = userId;
        if (resolvedUserId == null) {
            resolvedUserId = pushStateStore.findAnyAttempt(serverMsgId).map(PushAttempt::getUserId).orElse(null);
        }
        if (resolvedUserId != null) {
            pushStateStore.recordDeliveryState(serverMsgId, resolvedUserId, state);
        }
    }

    public Optional<PushAttempt> findAttempt(String serverMsgId, String userId) {
        return pushStateStore.findAttempt(serverMsgId, userId);
    }

    private Message toMessage(OfflinePushReq proto) {
        Message message = new Message();
        message.setServerMsgId(proto.getServerMsgId());
        message.setSenderId(proto.getSenderId());
        message.setReceiverId(proto.getUserId());
        message.setContent(proto.getContent() == null ? null : proto.getContent().getBytes(StandardCharsets.UTF_8));
        if (proto.getContentType() != null) {
            message.setContentType(ContentType.fromCode(proto.getContentType()));
        }
        if (proto.getSessionType() != null) {
            message.setChatType(ChatType.fromCode(proto.getSessionType()));
        }
        message.setAttributes(proto.getExt());
        MessageOptions options = new MessageOptions();
        if (proto.getExt() != null && proto.getExt().containsKey("notification")) {
            options.setNotification(Boolean.parseBoolean(proto.getExt().get("notification")));
            message.setOptions(options);
        }
        return message;
    }

    private OfflinePushReq toRequest(OfflinePushEvent event) {
        OfflinePushReq req = new OfflinePushReq();
        req.setUserId(event.getUserId());
        req.setConversationId(event.getConversationId());
        req.setSeq(event.getSeq());
        req.setServerMsgId(event.getServerMsgId());
        req.setContent(event.getContent());
        Map<String, String> ext = new HashMap<>();
        if (event.getAttributes() != null) {
            ext.putAll(event.getAttributes());
        }
        ext.put("notification", String.valueOf(event.isNotification()));
        req.setExt(ext);
        req.setSenderId(event.getSenderId());
        req.setSessionType(event.getSessionType());
        req.setContentType(event.getContentType());
        return req;
    }
}
