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
            return null;
        }
        UserConversation conversation = conversationService.getConversation(userId, conversationId);
        if (conversation == null) {
            return null;
        }

        long currentReadSeq = resolveReadSeq(userId, conversationId);
        long maxSeq = resolveMaxSeq(userId, conversationId);
        long boundedReadSeq = Math.min(requestedReadSeq, maxSeq);
        if (boundedReadSeq <= currentReadSeq) {
            return result(userId, conversationId, currentReadSeq, false, List.of());
        }

        conversationStateStore.setUserReadSeq(userId, conversationId, boundedReadSeq);
        conversationStateStore.setUnread(userId, conversationId, safeUnreadCount(maxSeq, boundedReadSeq));
        readSeqPersistenceWriter.enqueue(userId, conversationId, boundedReadSeq);
        versionLogRepository.append(userId, conversationId, ConversationVersionOperation.READ_STATE_UPDATED);
        ReadSeqUpdate update = result(userId, conversationId, boundedReadSeq, true, notificationTargets(conversation, userId));
        dispatchReadNotification(update);
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

    private int safeUnreadCount(long maxSeq, long readSeq) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, maxSeq - readSeq));
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
        ConversationControlEvent event = appendControlEvent(update, body);
        String deliveryId = event == null ? "read:" + update.getReaderUserId() + ":" + update.getConversationId() + ":" + update.getReadSeq() : event.getEventId();
        if (controlNotificationDispatcher == null) {
            return;
        }
        ServerEnvelope envelope = ServerEnvelope.of(CommandType.CHAT_READ, deliveryId, body);
        for (String userId : update.getNotifyUserIds()) {
            if (isBlank(userId)) {
                continue;
            }
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

    private ConversationControlEvent appendControlEvent(ReadSeqUpdate update, Map<String, Object> body) {
        if (controlEventRepository == null || objectMapper == null) {
            return null;
        }
        try {
            ConversationControlEvent event = new ConversationControlEvent();
            event.setConversationId(update.getConversationId());
            event.setType(ControlEventTypeEnum.READ_ADVANCED);
            event.setTargetUserIds(update.getNotifyUserIds());
            event.setPayload(objectMapper.writeValueAsString(body));
            event.setExpiresAt(System.currentTimeMillis() + 180L * 24 * 60 * 60 * 1000);
            return controlEventRepository.append(event);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
