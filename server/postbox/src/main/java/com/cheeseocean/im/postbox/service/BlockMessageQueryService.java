package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.history.MessageHistoryRepository;
import com.cheeseocean.im.common.core.history.document.AttachmentMetadataDoc;
import com.cheeseocean.im.common.core.history.document.MessageIdMappingDoc;
import com.cheeseocean.im.common.core.history.document.MessageSlot;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 历史块读取服务。
 *
 * @author xxxcrel
 */
@Service
public class BlockMessageQueryService {

    private final MessageHistoryRepository messageHistoryRepository;

    public BlockMessageQueryService(MessageHistoryRepository messageHistoryRepository) {
        this.messageHistoryRepository = messageHistoryRepository;
    }

    /**
     * 读取最近会话映射，用于拼装会话列表。
     */
    public List<MessageIdMappingDoc> findRecentConversationMappings(int limit) {
        return messageHistoryRepository.findRecentMappings(limit);
    }

    /**
     * 读取指定会话下指定 seq 的 slot：按 {@link BlockIndexUtil#docId} 点查 `_id` 后块内定位。
     */
    public MessageSlot findSlot(String conversationId, long seq) {
        return messageHistoryRepository.findSlot(conversationId, seq);
    }

    /**
     * 按 attachmentId 查附件所属消息：先点查 {@code attachment_metadata._id}，
     * 再按 (conversationId, seq) 读取 slot 还原内容。
     * 替代原 message_id_mapping 上的 content regex 全扫（ASSESSMENT P1-10）。
     */
    public Optional<AttachmentMessageCandidate> findAttachmentCandidate(String attachmentId) {
        if (attachmentId == null || attachmentId.isBlank()) {
            return Optional.empty();
        }
        AttachmentMetadataDoc metadata = messageHistoryRepository.findAttachmentMetadata(attachmentId);
        if (metadata == null || metadata.getConversationId() == null || metadata.getSeq() == null) {
            return Optional.empty();
        }
        MessageSlot slot = findSlot(metadata.getConversationId(), metadata.getSeq());
        if (slot == null) {
            return Optional.empty();
        }
        String content = MessagePreviewResolver.normalizeContent(slot.getContent());
        return Optional.of(new AttachmentMessageCandidate(
                metadata.getConversationId(), metadata.getServerMsgId(), content));
    }

    public record AttachmentMessageCandidate(String conversationId, String serverMsgId, String content) {
    }
}
