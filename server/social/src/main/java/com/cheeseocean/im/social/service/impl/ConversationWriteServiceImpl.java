package com.cheeseocean.im.social.service.impl;

import com.cheeseocean.im.common.api.conversation.ConversationWriteService;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.social.model.Conversation;
import com.cheeseocean.im.social.repository.ConversationStore;
import com.cheeseocean.im.social.service.ConversationSettingsNotifier;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话写入服务实现。
 *
 * <p>负责写扩散的会话创建（单聊/群聊）和用户配置更新（置顶/免打扰等）。
 * 所有创建操作均幂等，可安全重试。
 */
@Service
@DubboService
public class ConversationWriteServiceImpl implements ConversationWriteService {

    private final ConversationStore conversationStore;
    private final ConversationSettingsNotifier settingsNotifier;

    public ConversationWriteServiceImpl(ConversationStore conversationStore,
                                        ConversationSettingsNotifier settingsNotifier) {
        this.conversationStore = conversationStore;
        this.settingsNotifier = settingsNotifier;
    }

    @Override
    public void createSingleChatConversation(String senderId, String recvId,
                                             String conversationId, int conversationType) {
        if (conversationType == SessionType.SINGLE.getCode()) {
            // 单聊：双向各创建一条
            conversationStore.createIfAbsent(buildConversation(senderId, conversationId, conversationType, recvId));
            conversationStore.createIfAbsent(buildConversation(recvId, conversationId, conversationType, senderId));
        } else {
            // 通知类：仅为接收方创建
            conversationStore.createIfAbsent(buildConversation(recvId, conversationId, conversationType, senderId));
        }
    }

    @Override
    public void createGroupChatConversations(String groupId, String conversationId, List<String> userIds) {
        for (String userId : userIds) {
            conversationStore.createIfAbsent(
                    buildConversation(userId, conversationId, SessionType.GROUP.getCode(), groupId));
        }
    }

    @Override
    public void setConversations(List<String> userIds, SetConversationRequest request) {
        Map<String, Object> fields = buildUpdateFields(request);

        for (String userId : userIds) {
            conversationStore.upsertFields(
                    userId,
                    request.getConversationId(),
                    request.getConversationType(),
                    request.getTargetId(),
                    fields
            );
        }

        // recvMsgOpt 变更需要通知相关方（如推送服务更新过滤缓存）
        if (request.getRecvMsgOpt() != null) {
            for (String userId : userIds) {
                settingsNotifier.notifyRecvMsgOptChanged(
                        userId,
                        request.getConversationId(),
                        request.getRecvMsgOpt()
                );
            }
        }
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    /** 构建会话领域模型（用于 createIfAbsent）。 */
    private Conversation buildConversation(String ownerUserId, String conversationId,
                                           int conversationType, String targetId) {
        Conversation conv = new Conversation();
        conv.setOwnerUserId(ownerUserId);
        conv.setConversationId(conversationId);
        conv.setConversationType(conversationType);
        conv.setTargetId(targetId);
        return conv;
    }

    /**
     * 将请求中的可选字段转换为 MongoDB update field map。
     * null 字段不放入 map，对应字段不参与更新。
     */
    private Map<String, Object> buildUpdateFields(SetConversationRequest request) {
        Map<String, Object> fields = new HashMap<>();
        if (request.getRecvMsgOpt() != null) {
            fields.put("recvMsgOpt", request.getRecvMsgOpt());
        }
        if (request.getPinned() != null) {
            fields.put("pinned", request.getPinned());
        }
        if (request.getDraftText() != null) {
            fields.put("draftText", request.getDraftText());
        }
        if (request.getAttachedInfo() != null) {
            fields.put("attachedInfo", request.getAttachedInfo());
        }
        return fields;
    }
}
