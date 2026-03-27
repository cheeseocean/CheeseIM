package com.cheeseocean.im.postman.service.impl;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.push.OfflinePushReq;
import com.cheeseocean.im.common.api.dto.push.PushResult;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.rpc.OfflinePusher;
import com.cheeseocean.im.common.core.enums.DeliveryState;
import com.cheeseocean.im.postman.entity.OfflinePushResult;
import com.cheeseocean.im.postman.entity.PushAttempt;
import com.cheeseocean.im.postman.service.OfflinePushService;
import com.cheeseocean.im.postman.service.PushDecisionService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@DubboService(interfaceClass = OfflinePusher.class)
public class MessagePushServiceImpl implements OfflinePusher {

    private final OfflinePushService offlinePushService;
    private final PushDecisionService decisionService;
    private final Map<String, PushAttempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, DeliveryState> deliveryStates = new ConcurrentHashMap<>();

    public MessagePushServiceImpl(OfflinePushService offlinePushService, PushDecisionService decisionService) {
        this.offlinePushService = offlinePushService;
        this.decisionService = decisionService;
    }

    @Override
    public PushResult pushOffline(OfflinePushReq req) {
        if (req == null || req.getUserId() == null || req.getServerMsgId() == null) {
            return PushResult.failed(req == null ? null : req.getUserId(), "invalid-request");
        }
        DeliveryState state = deliveryStates.getOrDefault(key(req.getServerMsgId(), req.getUserId()), DeliveryState.INBOXED);
        PushDecisionService.PushDecision decision = decisionService.decide(
                req.getUserId(), req, state, Optional.ofNullable(attempts.get(key(req.getServerMsgId(), req.getUserId()))));

        if (!decision.shouldPush()) {
            return PushResult.failed(req.getUserId(), decision.reason());
        }

        attempts.put(key(req.getServerMsgId(), req.getUserId()), decision.attempt());
        OfflinePushResult result = offlinePushService.pushMessageToUser(toMessage(req), req.getUserId());
        return result.isSuccess() ? PushResult.success(req.getUserId(), "offline-push") : PushResult.failed(req.getUserId(), result.getErrorMessage());
    }

    public PushResult pushOffline(OfflinePushEvent event) {
        return pushOffline(toRequest(event));
    }

    @Override
    public void cancelPending(String serverMsgId, String userId) {
        PushAttempt attempt = attempts.get(key(serverMsgId, userId));
        if (attempt != null) {
            attempt.cancel();
        }
        deliveryStates.put(key(serverMsgId, userId), DeliveryState.READ);
    }

    public void recordDeliveryState(String serverMsgId, DeliveryState state) {
        recordDeliveryState(serverMsgId, null, state);
    }

    public void recordDeliveryState(String serverMsgId, String userId, DeliveryState state) {
        String resolvedUserId = userId;
        if (resolvedUserId == null) {
            resolvedUserId = attempts.values().stream()
                    .filter(attempt -> attempt.getServerMsgId().equals(serverMsgId))
                    .map(PushAttempt::getUserId)
                    .findFirst()
                    .orElse(null);
        }
        if (resolvedUserId != null) {
            deliveryStates.put(key(serverMsgId, resolvedUserId), state);
        }
    }

    public Optional<PushAttempt> findAttempt(String serverMsgId, String userId) {
        return Optional.ofNullable(attempts.get(key(serverMsgId, userId)));
    }

    private String key(String serverMsgId, String userId) {
        return serverMsgId + ":" + userId;
    }

    private Message toMessage(OfflinePushReq proto) {
        Message message = new Message();
        message.setServerMsgID(proto.getServerMsgId());
        message.setSendID(proto.getSenderId());
        message.setRecvID(proto.getUserId());
        message.setContent(proto.getContent());
        message.setContentType(proto.getContentType());
        message.setSessionType(proto.getSessionType());
        if (proto.getExt() != null) {
            message.setAttachedInfo(proto.getExt().get("attachedInfo"));
        }
        Map<String, Boolean> options = new HashMap<>();
        if (proto.getExt() != null && proto.getExt().containsKey("notification")) {
            options.put("notification", Boolean.parseBoolean(proto.getExt().get("notification")));
        }
        if (!options.isEmpty()) {
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
        Map<String, String> ext = new HashMap<>(event.getExt());
        ext.put("notification", String.valueOf(event.isNotification()));
        req.setExt(ext);
        req.setSenderId(event.getSenderId());
        req.setSessionType(event.getSessionType());
        req.setContentType(event.getContentType());
        return req;
    }
}
