package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.util.BlockIndexUtil;
import com.cheeseocean.im.postbox.history.AttachmentMetadataDoc;
import com.cheeseocean.im.postbox.history.MessageBlockDoc;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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

    private final MongoTemplate mongoTemplate;

    public BlockMessageQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * 读取最近会话映射，用于拼装会话列表。
     */
    public List<MessageIdMappingDoc> findRecentConversationMappings(int limit) {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "sendTime"))
                .limit(Math.max(1, limit));
        return mongoTemplate.find(query, MessageIdMappingDoc.class);
    }

    /**
     * 读取指定会话下指定 seq 的 slot：按 {@link BlockIndexUtil#docId} 点查 `_id` 后块内定位。
     */
    public MessageSlot findSlot(String conversationId, long seq) {
        Query query = Query.query(Criteria.where("_id").is(BlockIndexUtil.docId(conversationId, seq)));
        MessageBlockDoc block = mongoTemplate.findOne(query, MessageBlockDoc.class);
        if (block == null || block.getMessages() == null) {
            return null;
        }
        for (MessageSlot slot : block.getMessages()) {
            if (slot != null && slot.getSeq() != null && slot.getSeq() == seq) {
                return slot;
            }
        }
        return null;
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
        AttachmentMetadataDoc metadata = mongoTemplate.findById(attachmentId, AttachmentMetadataDoc.class);
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
