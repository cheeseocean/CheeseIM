package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.postbox.history.MessageBlockDoc;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
     * 读取指定会话下指定 seq 的 slot。
     */
    public MessageSlot findSlot(String conversationId, long seq) {
        long blockNo = Math.max(1L, ((seq - 1L) / 100L) + 1L);
        Query query = Query.query(Criteria.where("conversationId").is(conversationId).and("blockNo").is(blockNo));
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
     * 读取附件候选消息。
     */
    public List<AttachmentMessageCandidate> findAttachmentCandidates(String attachmentId, int limit) {
        if (attachmentId == null || attachmentId.isBlank()) {
            return List.of();
        }
        Query query = new Query()
                .addCriteria(Criteria.where("content").regex(attachmentId))
                .with(Sort.by(Sort.Direction.DESC, "sendTime"))
                .limit(Math.max(1, limit));
        List<MessageIdMappingDoc> mappings = mongoTemplate.find(query, MessageIdMappingDoc.class);
        List<AttachmentMessageCandidate> candidates = new ArrayList<>();
        for (MessageIdMappingDoc mapping : mappings) {
            if (mapping == null || mapping.getConversationId() == null || mapping.getSeq() == null) {
                continue;
            }
            MessageSlot slot = findSlot(mapping.getConversationId(), mapping.getSeq());
            if (slot == null) {
                continue;
            }
            String content = MessagePreviewResolver.normalizeContent(slot.getContent());
            if (content == null || !content.contains(attachmentId)) {
                continue;
            }
            candidates.add(new AttachmentMessageCandidate(mapping.getConversationId(), mapping.getServerMsgId(), content));
        }
        return candidates;
    }

    public record AttachmentMessageCandidate(String conversationId, String serverMsgId, String content) {
    }
}
