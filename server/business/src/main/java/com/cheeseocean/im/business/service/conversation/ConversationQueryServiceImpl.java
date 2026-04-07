package com.cheeseocean.im.business.service.conversation;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.cheeseocean.im.common.api.conversation.ConversationQueryService;
import com.cheeseocean.im.common.api.dto.conversation.ConversationDTO;
import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.message.ConversationLastMessageQueryService;
import com.cheeseocean.im.common.core.business.domain.UserConversationSyncPoint;
import com.cheeseocean.im.common.core.business.domain.UserConversation;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话查询服务实现。
 *
 * <p>对外暴露用户维度的会话业务状态读取能力：
 * 单条查询、批量查询、全量拉取、ID 列表获取、哈希版本及离线推送过滤。
 */
@Service
@DubboService
public class ConversationQueryServiceImpl implements ConversationQueryService {

    private static final String FIELD_CONVERSATION_RECEIVE_OPTIONS = "conversation:receive_options:";

    private final UserConversationRepository stateRepository;
    private final UserConversationSyncPointRepository offsetRepository;
    private final ConversationLastMessageQueryService lastMessageQueryService;
    private final ConversationSettingsNotifier conversationSettingsNotifier;
    private final ObjectMapper objectMapper;

    public ConversationQueryServiceImpl(UserConversationRepository stateRepository,
                                        UserConversationSyncPointRepository offsetRepository,
                                        ConversationLastMessageQueryService lastMessageQueryService,
                                        ConversationSettingsNotifier conversationSettingsNotifier,
                                        ObjectMapper objectMapper) {
        this.stateRepository = stateRepository;
        this.offsetRepository = offsetRepository;
        this.lastMessageQueryService = lastMessageQueryService;
        this.conversationSettingsNotifier = conversationSettingsNotifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConversationDTO getConversation(String ownerUserId, String conversationId) {
        UserConversation state = stateRepository.findOne(ownerUserId, conversationId);
        if (state == null) {
            return null;
        }
        return enrich(ownerUserId, List.of(state)).stream().findFirst().orElse(null);
    }

    @Override
    public List<ConversationDTO> getConversations(String ownerUserId, List<String> conversationIds) {
        return enrich(ownerUserId, stateRepository.findByIds(ownerUserId, conversationIds));
    }

    @Override
    public List<ConversationDTO> getAllConversations(String ownerUserId) {
        return enrich(ownerUserId, stateRepository.findAll(ownerUserId));
    }

    @Override
    public List<String> getConversationIds(String ownerUserId) {
        return stateRepository.findConversationIds(ownerUserId);
    }

    @Override
    public long getConversationIdsHash(String ownerUserId) {
        List<String> ids = new ArrayList<>(stateRepository.findConversationIds(ownerUserId));
        ids.sort(String::compareTo);
        return (long) ids.hashCode() & 0xFFFFFFFFL;
    }

    @Override
    @Cached(name = FIELD_CONVERSATION_RECEIVE_OPTIONS, key = "#ownerUserId + ':' + #conversationId", expire = 300, cacheType = CacheType.REMOTE)
    public int getReceiveOption(String ownerUserId, String conversationId) {
        return stateRepository.getRecvMsgOpt(ownerUserId, conversationId);
    }

    @Override
    @CacheInvalidate(name = FIELD_CONVERSATION_RECEIVE_OPTIONS, key = "#ownerUserId + ':' + #conversationId")
    public void setReceiveOption(String ownerUserId, String conversationId, int recvMsgOpt) {
        stateRepository.setRecvMsgOpt(ownerUserId, conversationId, recvMsgOpt);
    }

    @Override
    public List<String> getOfflinePushUserIds(String conversationId, List<String> candidateUserIds) {
        if (candidateUserIds == null || candidateUserIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> notReceiveIds = stateRepository.findNotReceiveUserIds(conversationId, candidateUserIds);
        Set<String> notReceiveSet = new HashSet<>(notReceiveIds);
        return candidateUserIds.stream()
                .filter(uid -> !notReceiveSet.contains(uid))
                .collect(Collectors.toList());
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private List<ConversationDTO> enrich(String ownerUserId, List<UserConversation> states) {
        if (states == null || states.isEmpty()) {
            return List.of();
        }
        List<String> conversationIds = states.stream()
                .map(UserConversation::getConversationId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        Map<String, UserConversationSyncPoint> offsets = offsetRepository.findByIds(ownerUserId, conversationIds)
                .stream()
                .collect(Collectors.toMap(
                        UserConversationSyncPoint::getConversationId,
                        range -> range,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, ConversationLastMessageSummary> lastMessages =
                lastMessageQueryService.getLatestMessages(conversationIds);

        return states.stream()
                .map(state -> toDTO(
                        state,
                        offsets.get(state.getConversationId()),
                        lastMessages.get(state.getConversationId())))
                .collect(Collectors.toList());
    }

    private ConversationDTO toDTO(UserConversation state,
                                  UserConversationSyncPoint offsetRange,
                                  ConversationLastMessageSummary lastMessage) {
        ConversationDTO dto = new ConversationDTO();
        dto.setOwnerUserId(state.getOwnerUserId());
        dto.setConversationId(state.getConversationId());
        dto.setConversationType(state.getConversationType());
        dto.setTargetId(state.getTargetId());
        dto.setRecvMsgOpt(state.getRecvMsgOpt());
        dto.setUnreadCount(resolveUnreadCount(state, offsetRange));
        dto.setReadSeq(offsetRange == null ? null : offsetRange.getReadSeq());
        dto.setLatestMsgSeq(lastMessage == null ? state.getLatestMsgSeq() : lastMessage.getSeq());
        dto.setLatestMsg(serializeSummary(lastMessage, state.getLatestMsg()));
        dto.setPinned(state.isPinned());
        dto.setDraftText(state.getDraftText());
        dto.setAttachedInfo(state.getAttachedInfo());
        dto.setCreatedAt(state.getCreatedAt());
        dto.setUpdatedAt(state.getUpdatedAt());
        return dto;
    }

    private int resolveUnreadCount(UserConversation state, UserConversationSyncPoint offsetRange) {
        if (offsetRange == null) {
            return state.getUnreadCount();
        }
        long computed = Math.max(0L, offsetRange.getMaxSeq() - offsetRange.getReadSeq());
        return computed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) computed;
    }

    private String serializeSummary(ConversationLastMessageSummary summary, String fallback) {
        if (summary == null) {
            return fallback;
        }
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("会话最新消息序列化失败", e);
        }
    }
}
