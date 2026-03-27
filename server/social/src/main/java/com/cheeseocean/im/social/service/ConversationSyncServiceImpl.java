package com.cheeseocean.im.social.service;

import com.cheeseocean.im.common.api.conversation.ConversationSyncCommand;
import com.cheeseocean.im.common.api.conversation.ConversationSyncService;
import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.dto.message.SequencedMessage;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.common.core.util.MessagePreviewUtil;
import com.cheeseocean.im.social.model.Conversation;
import com.cheeseocean.im.social.repository.ConversationStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户会话记录（MongoDB）管理服务。
 *
 * 生命周期：
 *  • 懒创建   — {@code cmd.newConversation()} 为 true 时为所有参与者插入会话记录，幂等。
 *  • 实时更新 — 每批消息落库后写入 latestMsgSeq、latestMsg 及各用户的未读增量。
 *  • 已读同步 — markRead 将 readSeq 交由 ReadSeqPersistenceWriter 异步持久化至 MongoDB。
 */
@Service
@DubboService
public class ConversationSyncServiceImpl implements ConversationSyncService {

    private final ConversationStore conversationStore;
    private final ReadSeqPersistenceWriter readSeqPersistenceWriter;
    private final ObjectMapper objectMapper;

    public ConversationSyncServiceImpl(ConversationStore conversationStore,
                                       ReadSeqPersistenceWriter readSeqPersistenceWriter,
                                       ObjectMapper objectMapper) {
        this.conversationStore = conversationStore;
        this.readSeqPersistenceWriter = readSeqPersistenceWriter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void createIfNew(ConversationSyncCommand cmd) {
        if (!cmd.newConversation()) return;
        for (String participantId : cmd.allParticipants()) {
            conversationStore.createIfAbsent(buildConversation(participantId, cmd));
        }
    }

    @Override
    public void sync(ConversationSyncCommand cmd) {
        String latestMsgJson = serializeLatestMsg(cmd.latestMessage());

        for (String participantId : cmd.allParticipants()) {
            if (cmd.newConversation()) {
                conversationStore.createIfAbsent(buildConversation(participantId, cmd));
            }

            conversationStore.updateLatestMessage(
                    participantId,
                    cmd.conversationId(),
                    cmd.latestMessage().getSeq(),
                    latestMsgJson
            );

            int unreadDelta = unreadDeltaFor(participantId, cmd.senderIds());
            if (unreadDelta > 0) {
                conversationStore.incrementUnread(participantId, cmd.conversationId(), unreadDelta);
            }
        }
    }

    @Override
    public void markRead(String ownerUserId, String conversationId, long readSeq) {
        readSeqPersistenceWriter.enqueue(ownerUserId, conversationId, readSeq);
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private Conversation buildConversation(String ownerId, ConversationSyncCommand cmd) {
        Conversation conv = new Conversation();
        conv.setOwnerUserId(ownerId);
        conv.setConversationId(cmd.conversationId());
        conv.setConversationType(cmd.sessionType());
        conv.setTargetId(resolveTargetId(ownerId, cmd));
        return conv;
    }

    private String resolveTargetId(String ownerId, ConversationSyncCommand cmd) {
        if (cmd.sessionType() == SessionType.SINGLE.getCode()) {
            String senderId = cmd.latestMessage().getSenderId();
            String recvId   = cmd.latestMessage().getRecvId();
            return ownerId.equals(senderId) ? recvId : senderId;
        }
        return cmd.latestMessage().getGroupId();
    }

    private int unreadDeltaFor(String participantId, List<String> senderIds) {
        int delta = 0;
        for (String senderId : senderIds) {
            if (!participantId.equals(senderId)) {
                delta++;
            }
        }
        return delta;
    }

    private String serializeLatestMsg(SequencedMessage message) {
        try {
            ConversationLastMessageSummary summary = new ConversationLastMessageSummary();
            summary.setSeq(message.getSeq());
            summary.setSenderId(message.getSenderId());
            summary.setContent(message.getContent());
            summary.setContentType(message.getContentType());
            summary.setPreviewText(MessagePreviewUtil.resolvePreview(
                    message.getContentType(), message.getContent(), message.getExt()));
            summary.setPreviewType(MessagePreviewUtil.resolvePreviewType(
                    message.getContentType(),
                    message.getOptions() != null && Boolean.TRUE.equals(message.getOptions().isNotification())));
            summary.setSendTime(message.getSendTime());
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("会话最新消息序列化失败", e);
        }
    }
}
