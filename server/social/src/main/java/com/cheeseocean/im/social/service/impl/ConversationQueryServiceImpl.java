package com.cheeseocean.im.social.service.impl;

import com.cheeseocean.im.common.api.conversation.ConversationQueryService;
import com.cheeseocean.im.common.api.dto.conversation.ConversationDTO;
import com.cheeseocean.im.social.model.Conversation;
import com.cheeseocean.im.social.repository.ConversationStore;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话查询服务实现。
 *
 * <p>负责对外暴露用户维度的会话读取能力：
 * 单条查询、批量查询、全量拉取、ID 列表获取、哈希版本及离线推送过滤。
 */
@Service
@DubboService
public class ConversationQueryServiceImpl implements ConversationQueryService {

    private final ConversationStore conversationStore;

    public ConversationQueryServiceImpl(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
    }

    @Override
    public ConversationDTO getConversation(String ownerUserId, String conversationId) {
        Conversation conv = conversationStore.findOne(ownerUserId, conversationId);
        return conv == null ? null : toDTO(conv);
    }

    @Override
    public List<ConversationDTO> getConversations(String ownerUserId, List<String> conversationIds) {
        return conversationStore.findByIds(ownerUserId, conversationIds)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ConversationDTO> getAllConversations(String ownerUserId) {
        return conversationStore.findAll(ownerUserId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<String> getConversationIds(String ownerUserId) {
        return conversationStore.findConversationIds(ownerUserId);
    }

    @Override
    public long getConversationIdsHash(String ownerUserId) {
        List<String> ids = conversationStore.findConversationIds(ownerUserId);
        // 排序后哈希，保证相同内容的列表得到相同哈希值
        ids = new ArrayList<>(ids);
        ids.sort(String::compareTo);
        return (long) ids.hashCode() & 0xFFFFFFFFL;
    }

    @Override
    public List<String> getOfflinePushUserIds(String conversationId, List<String> candidateUserIds) {
        if (candidateUserIds == null || candidateUserIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 找出对该会话设置了 NOT_RECEIVE 的用户
        List<String> notReceiveIds = conversationStore.findNotReceiveUserIds(conversationId, candidateUserIds);
        Set<String> notReceiveSet = new HashSet<>(notReceiveIds);
        // 候选列表中排除屏蔽用户，剩余的可接收离线推送
        return candidateUserIds.stream()
                .filter(uid -> !notReceiveSet.contains(uid))
                .collect(Collectors.toList());
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    /** 将领域模型映射为 DTO。 */
    private ConversationDTO toDTO(Conversation conv) {
        ConversationDTO dto = new ConversationDTO();
        dto.setOwnerUserId(conv.getOwnerUserId());
        dto.setConversationId(conv.getConversationId());
        dto.setConversationType(conv.getConversationType());
        dto.setTargetId(conv.getTargetId());
        dto.setRecvMsgOpt(conv.getRecvMsgOpt());
        dto.setUnreadCount(conv.getUnreadCount());
        dto.setLatestMsgSeq(conv.getLatestMsgSeq());
        dto.setLatestMsg(conv.getLatestMsg());
        dto.setPinned(conv.isPinned());
        dto.setDraftText(conv.getDraftText());
        dto.setAttachedInfo(conv.getAttachedInfo());
        return dto;
    }
}
