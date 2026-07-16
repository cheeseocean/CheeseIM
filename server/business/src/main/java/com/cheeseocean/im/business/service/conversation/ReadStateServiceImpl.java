package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.conversation.ReadStateService;
import com.cheeseocean.im.common.api.dto.conversation.ReadSeqUpdate;
import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import com.cheeseocean.im.common.core.business.repository.ConversationSequenceRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationVersionLogRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 已读状态统一实现。
 *
 * <p>Redis 热状态提供即时可见性，写后缓冲负责将单调 readSeq 落入 Mongo；版本日志仅写入
 * 阅读者自己的流，以便其其他设备通过增量同步收敛。实时通知由返回结果交给入口层投递。
 */
@Service
@DubboService
public class ReadStateServiceImpl implements ReadStateService {

    private final ConversationService conversationService;
    private final ConversationSequenceRepository conversationSequenceRepository;
    private final UserConversationSyncPointRepository syncPointRepository;
    private final ConversationStateStore conversationStateStore;
    private final ReadSeqPersistenceWriter readSeqPersistenceWriter;
    private final ConversationVersionLogRepository versionLogRepository;
    private final ConversationControlEventRepository controlEventRepository;
    private final ObjectMapper objectMapper;

    @DubboReference(check = false, retries = 0)
    private ControlNotificationDispatcher controlNotificationDispatcher;

    public ReadStateServiceImpl(ConversationService conversationService,
                                ConversationSequenceRepository conversationSequenceRepository,
                                UserConversationSyncPointRepository syncPointRepository,
                                ConversationStateStore conversationStateStore,
                                ReadSeqPersistenceWriter readSeqPersistenceWriter,
                                ConversationVersionLogRepository versionLogRepository,
                                ConversationControlEventRepository controlEventRepository,
                                ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.conversationSequenceRepository = conversationSequenceRepository;
        this.syncPointRepository = syncPointRepository;
        this.conversationStateStore = conversationStateStore;
        this.readSeqPersistenceWriter = readSeqPersistenceWriter;
        this.versionLogRepository = versionLogRepository;
        this.controlEventRepository = controlEventRepository;
        this.objectMapper = objectMapper;
    }

    ReadStateServiceImpl(ConversationService conversationService,
                         ConversationSequenceRepository conversationSequenceRepository,
                         UserConversationSyncPointRepository syncPointRepository,
                         ConversationStateStore conversationStateStore,
                         ReadSeqPersistenceWriter readSeqPersistenceWriter,
                         ConversationVersionLogRepository versionLogRepository) {
        this(conversationService, conversationSequenceRepository, syncPointRepository, conversationStateStore,
                readSeqPersistenceWriter, versionLogRepository, null, null);
    }

    @Override
    public ReadSeqUpdate acknowledge(String userId, String conversationId, long requestedReadSeq) {
        if (isBlank(userId) || isBlank(conversationId) || requestedReadSeq <= 0) {
            ImMetrics.readAdvance("invalid");
            return null;
        }
        UserConversation conversation = conversationService.getConversation(userId, conversationId);
        if (conversation == null) {
            ImMetrics.readAdvance("not_found");
            return null;
        }

        long knownReadSeq = resolveReadSeq(userId, conversationId);
        long knownMaxSeq = resolveMaxSeq(userId, conversationId);
        ConversationStateStore.ReadState state = conversationStateStore.advanceReadState(
                userId, conversationId, requestedReadSeq, knownReadSeq, knownMaxSeq);
        // 重复 ACK 同样补写 Mongo，避免首次推进后进程在持久化入队前退出造成永久缺口。
        if (state.readSeq() > 0) {
            readSeqPersistenceWriter.enqueue(userId, conversationId, state.readSeq());
        }
        if (!state.changed()) {
            ImMetrics.readAdvance("unchanged");
            return result(userId, conversationId, state.readSeq(), false, List.of());
        }

        versionLogRepository.append(userId, conversationId, ConversationVersionOperation.READ_STATE_UPDATED);
        ReadSeqUpdate update = result(userId, conversationId, state.readSeq(), true, notificationTargets(conversation, userId));
        dispatchReadNotification(update);
        ImMetrics.readAdvance("advanced");
        return update;
    }

    private long resolveMaxSeq(String userId, String conversationId) {
        Long hotUserMaxSeq = conversationStateStore.getUserMaxSeq(userId, conversationId);
        if (hotUserMaxSeq != null && hotUserMaxSeq > 0) {
            return hotUserMaxSeq;
        }
        long persistedUserMaxSeq = syncPointRepository.getMaxSeq(userId, conversationId);
        if (persistedUserMaxSeq > 0) {
            return persistedUserMaxSeq;
        }
        Long hotConversationMaxSeq = conversationStateStore.getConversationMaxSeq(conversationId);
        if (hotConversationMaxSeq != null && hotConversationMaxSeq > 0) {
            return hotConversationMaxSeq;
        }
        return Math.max(0L, conversationSequenceRepository.getMaxSeq(conversationId));
    }

    private long resolveReadSeq(String userId, String conversationId) {
        Long hotReadSeq = conversationStateStore.getUserReadSeq(userId, conversationId);
        return hotReadSeq != null && hotReadSeq > 0
                ? hotReadSeq
                : syncPointRepository.getReadSeq(userId, conversationId);
    }

    private List<String> notificationTargets(UserConversation conversation, String readerUserId) {
        List<String> targets = new ArrayList<>();
        targets.add(readerUserId);
        if (conversation.getChatType() == ChatType.PRIVATE.getCode()
                && !isBlank(conversation.getTargetId())
                && !readerUserId.equals(conversation.getTargetId())) {
            targets.add(conversation.getTargetId());
        }
        return targets;
    }

    private ReadSeqUpdate result(String userId, String conversationId, long readSeq, boolean changed,
                                 List<String> notificationTargets) {
        ReadSeqUpdate result = new ReadSeqUpdate();
        result.setConversationId(conversationId);
        result.setReaderUserId(userId);
        result.setReadSeq(readSeq);
        result.setChanged(changed);
        result.setNotifyUserIds(notificationTargets);
        return result;
    }

    private void dispatchReadNotification(ReadSeqUpdate update) {
        if (!update.isChanged()) {
            return;
        }
        Map<String, Object> body = Map.of(
                "conversationId", update.getConversationId(),
                "readerId", update.getReaderUserId(),
                "readSeq", update.getReadSeq(),
                "updatedAt", System.currentTimeMillis());
        List<ConversationControlEvent> events = appendControlEvents(update, body);
        if (controlNotificationDispatcher == null) {
            return;
        }
        if (events.isEmpty()) {
            dispatchReadPartition(update.getNotifyUserIds(),
                    "read:" + update.getReaderUserId() + ":" + update.getConversationId() + ":" + update.getReadSeq(), body);
            return;
        }
        for (ConversationControlEvent event : events) {
            dispatchReadPartition(event.getTargetUserIds(), event.getEventId(), body);
        }
    }

    private void dispatchReadPartition(List<String> targetUserIds, String deliveryId, Map<String, Object> body) {
        ServerEnvelope envelope = ServerEnvelope.of(CommandType.CHAT_READ, deliveryId, body);
        for (String userId : targetUserIds) {
            if (!isBlank(userId)) {
                ControlNotificationReq request = new ControlNotificationReq();
                request.setUserId(userId);
                request.setEnvelope(envelope);
                request.setDeliveryId(deliveryId);
                try {
                    controlNotificationDispatcher.dispatch(request);
                } catch (RuntimeException ignored) {
                    // 控制通知失败由离线 read snapshot 收敛，不能回滚已提交的单调 readSeq。
                }
            }
        }
    }

    private List<ConversationControlEvent> appendControlEvents(ReadSeqUpdate update, Map<String, Object> body) {
        if (controlEventRepository == null || objectMapper == null) {
            return List.of();
        }
        try {
            ConversationControlEvent event = new ConversationControlEvent();
            event.setEventId("read:" + update.getReaderUserId() + ":" + update.getConversationId()
                    + ":" + update.getReadSeq());
            event.setConversationId(update.getConversationId());
            event.setType(ControlEventTypeEnum.READ_ADVANCED);
            event.setTargetUserIds(update.getNotifyUserIds());
            event.setPayload(objectMapper.writeValueAsString(body));
            event.setExpiresAt(System.currentTimeMillis() + 180L * 24 * 60 * 60 * 1000);
            List<ConversationControlEvent> events = controlEventRepository.appendPartitioned(event);
            return events == null ? List.of() : events;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
