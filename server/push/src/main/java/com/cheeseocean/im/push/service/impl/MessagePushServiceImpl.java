package com.cheeseocean.im.push.service.impl;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.MessagePushService;
import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.dto.OfflinePushTask;
import com.cheeseocean.im.common.dto.PushResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.push.entity.OfflinePushResult;
import com.cheeseocean.im.push.entity.PushAttempt;
import com.cheeseocean.im.push.service.OfflinePushService;
import com.cheeseocean.im.push.service.PushDecisionService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@DubboService(interfaceClass = MessagePushService.class)
public class MessagePushServiceImpl implements MessagePushService {

    private final OfflinePushService offlinePushService;
    private final PushDecisionService decisionService;
    private final Map<String, PushAttempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, DeliveryState> deliveryStates = new ConcurrentHashMap<>();

    public MessagePushServiceImpl(OfflinePushService offlinePushService, PushDecisionService decisionService) {
        this.offlinePushService = offlinePushService;
        this.decisionService = decisionService;
    }

    @Override
    public PushResult pushOffline(String userId, MessageProto message) {
        DeliveryState state = deliveryStates.getOrDefault(key(message.getServerMsgId(), userId), DeliveryState.INBOXED);
        PushDecisionService.PushDecision decision = decisionService.decide(
                userId, message, state, Optional.ofNullable(attempts.get(key(message.getServerMsgId(), userId))));

        if (!decision.shouldPush()) {
            return PushResult.failed(userId, decision.reason());
        }

        attempts.put(key(message.getServerMsgId(), userId), decision.attempt());
        OfflinePushResult result = offlinePushService.pushMessageToUser(toMessage(message), userId);
        return result.isSuccess() ? PushResult.success(userId, "offline-push") : PushResult.failed(userId, result.getErrorMessage());
    }

    public PushResult pushOffline(OfflinePushTask task) {
        return pushOffline(task.getReceiverId(), toMessageProto(task));
    }

    public PushResult pushOffline(OfflinePushEvent event) {
        return pushOffline(event.getUserId(), toMessageProto(event));
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

    private Message toMessage(MessageProto proto) {
        Message message = new Message();
        message.setClientMsgID(proto.getClientMsgId());
        message.setServerMsgID(proto.getServerMsgId());
        message.setSendID(proto.getSenderId());
        message.setRecvID(proto.getReceiverId());
        message.setContent(proto.getContent());
        message.setContentType(proto.getContentType());
        message.setSessionType(proto.getSessionType());
        message.setOfflinePushInfo(proto.getOfflinePushInfo());
        return message;
    }

    private MessageProto toMessageProto(OfflinePushTask task) {
        MessageProto proto = new MessageProto();
        proto.setServerMsgId(task.getMessageId());
        proto.setConversationId(task.getConversationId());
        proto.setConversationSeq(task.getConversationSeq());
        proto.setSenderId(task.getSenderId());
        proto.setReceiverId(task.getReceiverId());
        proto.setContent(task.getContent());
        proto.setContentType(task.getContentType());
        proto.setSessionType(task.getSessionType());
        proto.setAttachedInfo(task.getAttachedInfo());
        return proto;
    }

    private MessageProto toMessageProto(OfflinePushEvent event) {
        MessageProto proto = new MessageProto();
        proto.setServerMsgId(event.getServerMsgId());
        proto.setConversationId(event.getConversationId());
        proto.setConversationSeq(event.getSeq());
        proto.setReceiverId(event.getUserId());
        proto.setContent(event.getContent());
        return proto;
    }
}
