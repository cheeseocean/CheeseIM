package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.conversation.DeliveryStateService;
import com.cheeseocean.im.common.api.dto.conversation.DeliverySeqUpdate;
import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.store.delivery.DeliveryStateStore;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationSequenceRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 设备送达高水位统一实现；只接受客户端显式 ACK，不接入 channel write 结果。 */
@Service
@DubboService
public class DeliveryStateServiceImpl implements DeliveryStateService {
    private final ConversationService conversationService;
    private final ConversationStateStore conversationStateStore;
    private final DeliveryStateStore deliveryStateStore;
    private final DeliverySeqPersistenceWriter persistenceWriter;
    private final UserConversationSyncPointRepository syncPointRepository;
    private final ConversationSequenceRepository sequenceRepository;
    private final ConversationControlEventRepository controlEventRepository;
    private final ObjectMapper objectMapper;

    @DubboReference(check = false, retries = 0)
    private ControlNotificationDispatcher notificationDispatcher;

    public DeliveryStateServiceImpl(ConversationService conversationService,
                                    ConversationStateStore conversationStateStore,
                                    DeliveryStateStore deliveryStateStore,
                                    DeliverySeqPersistenceWriter persistenceWriter,
                                    UserConversationSyncPointRepository syncPointRepository,
                                    ConversationSequenceRepository sequenceRepository,
                                    ConversationControlEventRepository controlEventRepository,
                                    ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.conversationStateStore = conversationStateStore;
        this.deliveryStateStore = deliveryStateStore;
        this.persistenceWriter = persistenceWriter;
        this.syncPointRepository = syncPointRepository;
        this.sequenceRepository = sequenceRepository;
        this.controlEventRepository = controlEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public DeliverySeqUpdate acknowledge(String userId, String deviceId, String conversationId,
                                         long maxDeliveredSeq, String opId) {
        if (blank(userId) || blank(deviceId) || blank(conversationId) || blank(opId) || maxDeliveredSeq <= 0) return null;
        UserConversation conversation = conversationService.getConversation(userId, conversationId);
        if (conversation == null) return null;
        long visibleMaxSeq = resolveVisibleMaxSeq(userId, conversationId);
        long bounded = Math.min(maxDeliveredSeq, visibleMaxSeq);
        if (bounded <= 0) return null;
        DeliveryStateStore.AdvanceResult state = deliveryStateStore.advance(userId, deviceId, conversationId, bounded);
        DeliverySeqUpdate update = result(userId, deviceId, conversationId, state);
        // 重复 ACK 也补写，修复“Redis 已推进但首次 writer 入队失败”的半提交。
        persistenceWriter.enqueue(userId, deviceId, conversationId, state.deliveredSeq());
        notifyPrivateSender(conversation, update, opId);
        return update;
    }

    private long resolveVisibleMaxSeq(String userId, String conversationId) {
        Long hot = conversationStateStore.getUserMaxSeq(userId, conversationId);
        if (hot != null && hot > 0) return hot;
        long persisted = syncPointRepository.getMaxSeq(userId, conversationId);
        if (persisted > 0) return persisted;
        Long conversationHot = conversationStateStore.getConversationMaxSeq(conversationId);
        if (conversationHot != null && conversationHot > 0) return conversationHot;
        return Math.max(0L, sequenceRepository.getMaxSeq(conversationId));
    }

    private void notifyPrivateSender(UserConversation conversation, DeliverySeqUpdate update, String opId) {
        if (conversation.getChatType() != ChatType.PRIVATE.getCode()
                || blank(conversation.getTargetId()) || conversation.getTargetId().equals(update.getRecipientUserId())) return;
        long now = System.currentTimeMillis();
        Map<String, Object> body = Map.of(
                "conversationId", update.getConversationId(), "recipientId", update.getRecipientUserId(),
                "deviceId", update.getDeviceId(), "deliveredSeq", update.getDeliveredSeq(), "updatedAt", now);
        String deliveryId = "delivery:" + update.getRecipientUserId() + ":" + update.getDeviceId() + ":" + opId;
        if (controlEventRepository != null && objectMapper != null) {
            try {
                ConversationControlEvent event = new ConversationControlEvent();
                event.setConversationId(update.getConversationId());
                event.setType(ControlEventTypeEnum.DELIVERY_ADVANCED);
                event.setTargetUserIds(List.of(conversation.getTargetId()));
                event.setPayload(objectMapper.writeValueAsString(body));
                event.setExpiresAt(now + 180L * 24 * 60 * 60 * 1000);
                List<ConversationControlEvent> saved = controlEventRepository.appendPartitioned(event);
                if (saved != null && !saved.isEmpty()) deliveryId = saved.get(0).getEventId();
            } catch (Exception ignored) {
                // 实时路径仍尝试，客户端重复 ACK 会再次补写 outbox。
            }
        }
        if (notificationDispatcher != null) {
            ControlNotificationReq request = new ControlNotificationReq();
            request.setUserId(conversation.getTargetId());
            request.setDeliveryId(deliveryId);
            request.setEnvelope(ServerEnvelope.of(CommandType.CHAT_DELIVERY, deliveryId, body));
            try { notificationDispatcher.dispatch(request); } catch (RuntimeException ignored) { }
        }
    }

    private DeliverySeqUpdate result(String userId, String deviceId, String conversationId,
                                     DeliveryStateStore.AdvanceResult state) {
        DeliverySeqUpdate update = new DeliverySeqUpdate();
        update.setRecipientUserId(userId); update.setDeviceId(deviceId); update.setConversationId(conversationId);
        update.setDeliveredSeq(state.deliveredSeq()); update.setChanged(state.changed());
        return update;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
