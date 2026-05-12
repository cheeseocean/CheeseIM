package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.conversation.ConversationSyncService;
import com.cheeseocean.im.common.api.dto.conversation.ConversationReadSnapshot;
import com.cheeseocean.im.common.api.dto.conversation.PullMessages;
import com.cheeseocean.im.common.api.dto.conversation.SeqRangeRequest;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.core.business.repository.ConversationSequenceRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.postbox.service.HistoryQueryService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 会话消息同步服务实现。
 *
 * <p>负责组织 Redis 热状态、Mongo 同步位点和历史块查询，
 * 为客户端提供基于 seq 的增量同步能力。
 */
@Service
@DubboService
public class ConversationSyncServiceImpl implements ConversationSyncService {

    private static final int DEFAULT_PULL_LIMIT = 100;

    private final ConversationService                 conversationService;
    private final ConversationSequenceRepository      conversationSequenceRepository;
    private final UserConversationSyncPointRepository syncPointRepository;
    private final ConversationStateStore conversationStateStore;
    private final HistoryQueryService historyQueryService;
    private final ReadSeqPersistenceWriter readSeqPersistenceWriter;

    public ConversationSyncServiceImpl(ConversationService conversationService,
                                       ConversationSequenceRepository conversationSequenceRepository,
                                       UserConversationSyncPointRepository syncPointRepository,
                                       ConversationStateStore conversationStateStore,
                                       HistoryQueryService historyQueryService,
                                       ReadSeqPersistenceWriter readSeqPersistenceWriter) {
        this.conversationService = conversationService;
        this.conversationSequenceRepository = conversationSequenceRepository;
        this.syncPointRepository = syncPointRepository;
        this.conversationStateStore = conversationStateStore;
        this.historyQueryService = historyQueryService;
        this.readSeqPersistenceWriter = readSeqPersistenceWriter;
    }

    @Override
    public Map<String, Long> getConversationMaxSeqs(String userId, List<String> conversationIds) {
        List<String> visibleConversationIds = resolveVisibleConversationIds(userId, conversationIds);
        Map<String, Long> result = new LinkedHashMap<>();
        for (String conversationId : visibleConversationIds) {
            result.put(conversationId, resolveUserMaxSeq(userId, conversationId));
        }
        return result;
    }

    @Override
    public PullMessages pullMessagesBySeqRanges(String userId, List<SeqRangeRequest> ranges, int limitPerConversation) {
        PullMessages response = new PullMessages();
        if (isBlank(userId) || ranges == null || ranges.isEmpty()) {
            return response;
        }
        int effectiveLimit = limitPerConversation <= 0 ? DEFAULT_PULL_LIMIT : limitPerConversation;
        Set<String> visibleIds = new LinkedHashSet<>(resolveVisibleConversationIds(
                userId,
                ranges.stream()
                        .map(SeqRangeRequest::getConversationId)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
        ));
        for (SeqRangeRequest range : ranges) {
            if (range == null || isBlank(range.getConversationId())) {
                continue;
            }
            String conversationId = range.getConversationId();
            if (!visibleIds.contains(conversationId) || range.getBeginSeq() <= 0 || range.getEndSeq() < range.getBeginSeq()) {
                response.getMessagesByConversation().put(conversationId, new ArrayList<>());
                response.getEndSeqByConversation().put(conversationId, Math.max(0L, range.getBeginSeq() - 1L));
                response.getCompletedByConversation().put(conversationId, Boolean.TRUE);
                continue;
            }
            long maxVisibleSeq = resolveUserMaxSeq(userId, conversationId);
            long effectiveEndSeq = Math.min(range.getEndSeq(), maxVisibleSeq);
            if (effectiveEndSeq < range.getBeginSeq()) {
                response.getMessagesByConversation().put(conversationId, new ArrayList<>());
                response.getEndSeqByConversation().put(conversationId, Math.max(0L, range.getBeginSeq() - 1L));
                response.getCompletedByConversation().put(conversationId, Boolean.TRUE);
                continue;
            }

            List<Message> messages = historyQueryService.pullMessagesBySeqRange(
                    conversationId,
                    range.getBeginSeq(),
                    effectiveEndSeq,
                    effectiveLimit
            );
            long returnedEndSeq = messages.isEmpty()
                    ? Math.max(0L, range.getBeginSeq() - 1L)
                    : messages.get(messages.size() - 1).getSeq();
            boolean completed = returnedEndSeq >= effectiveEndSeq || messages.size() < effectiveLimit;

            response.getMessagesByConversation().put(conversationId, messages);
            response.getEndSeqByConversation().put(conversationId, returnedEndSeq);
            response.getCompletedByConversation().put(conversationId, completed);
        }
        return response;
    }

    @Override
    public Map<String, ConversationReadSnapshot> getConversationReadSnapshots(String userId, List<String> conversationIds) {
        List<String> visibleConversationIds = resolveVisibleConversationIds(userId, conversationIds);
        Map<String, ConversationReadSnapshot> snapshots = new LinkedHashMap<>();
        for (String conversationId : visibleConversationIds) {
            long readSeq = resolveReadSeq(userId, conversationId);
            long maxSeq = resolveUserMaxSeq(userId, conversationId);
            long unread = Math.max(0L, maxSeq - readSeq);
            int hotUnread = conversationStateStore.getUnread(userId, conversationId);
            if (hotUnread > 0 || unread == 0L) {
                unread = hotUnread;
            }

            ConversationReadSnapshot snapshot = new ConversationReadSnapshot();
            snapshot.setConversationId(conversationId);
            snapshot.setReadSeq(readSeq);
            snapshot.setMaxSeq(maxSeq);
            snapshot.setUnreadCount(unread);
            snapshots.put(conversationId, snapshot);
        }
        return snapshots;
    }

    @Override
    public void ackReadSeq(String userId, String conversationId, long readSeq) {
        if (isBlank(userId) || isBlank(conversationId) || readSeq <= 0) {
            return;
        }
        UserConversation conversation = conversationService.getConversation(userId, conversationId);
        if (conversation == null) {
            return;
        }
        long currentReadSeq = resolveReadSeq(userId, conversationId);
        if (readSeq <= currentReadSeq) {
            return;
        }
        long maxSeq = resolveUserMaxSeq(userId, conversationId);
        long boundedReadSeq = maxSeq > 0 ? Math.min(readSeq, maxSeq) : readSeq;
        conversationStateStore.setUserReadSeq(userId, conversationId, boundedReadSeq);
        int unreadCount = (int) Math.max(0L, maxSeq - boundedReadSeq);
        conversationStateStore.setUnread(userId, conversationId, unreadCount);
        readSeqPersistenceWriter.enqueue(userId, conversationId, boundedReadSeq);
    }

    private List<String> resolveVisibleConversationIds(String userId, List<String> requestedConversationIds) {
        if (isBlank(userId)) {
            return new ArrayList<>();
        }
        if (requestedConversationIds == null || requestedConversationIds.isEmpty()) {
            List<String> conversationIds = conversationService.getConversationIds(userId);
            return conversationIds == null ? new ArrayList<>() : conversationIds;
        }
        List<UserConversation> conversations = conversationService.getConversations(userId, requestedConversationIds);
        if (conversations == null || conversations.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> visibleConversationIds = new ArrayList<>(conversations.size());
        for (UserConversation conversation : conversations) {
            if (conversation != null && !isBlank(conversation.getConversationId())) {
                visibleConversationIds.add(conversation.getConversationId());
            }
        }
        return visibleConversationIds;
    }

    private long resolveUserMaxSeq(String userId, String conversationId) {
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
        return conversationSequenceRepository.getMaxSeq(conversationId);
    }

    private long resolveReadSeq(String userId, String conversationId) {
        Long hotReadSeq = conversationStateStore.getUserReadSeq(userId, conversationId);
        if (hotReadSeq != null && hotReadSeq > 0) {
            return hotReadSeq;
        }
        return syncPointRepository.getReadSeq(userId, conversationId);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
