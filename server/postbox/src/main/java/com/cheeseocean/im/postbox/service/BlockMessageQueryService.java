package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.util.BlockIndexUtil;
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
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class BlockMessageQueryService {

    private final MongoTemplate mongoTemplate;

    public BlockMessageQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<MessageIdMappingDoc> findRecentConversationMappings(int limit) {
        Query query = Query.query(new Criteria()).with(Sort.by(Sort.Direction.DESC, "sendTime"));
        query.limit(limit);
        return mongoTemplate.find(query, MessageIdMappingDoc.class);
    }

    public MessageSlot findSlot(String conversationId, Long seq) {
        if (conversationId == null || seq == null) {
            return null;
        }
        MessageBlockDoc block = mongoTemplate.findById(BlockIndexUtil.docId(conversationId, seq), MessageBlockDoc.class);
        if (block == null || block.getMessages() == null) {
            return null;
        }
        int index = BlockIndexUtil.index(seq);
        if (index >= block.getMessages().size()) {
            return null;
        }
        return block.getMessages().get(index);
    }

    public List<AttachmentMessageCandidate> findAttachmentCandidates(String attachmentId, int limit) {
        Query query = Query.query(Criteria.where("messages.ext.attachedInfo").regex(Pattern.quote(attachmentId)))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        query.limit(Math.max(limit, 1));

        List<AttachmentMessageCandidate> candidates = new ArrayList<>();
        for (MessageBlockDoc block : mongoTemplate.find(query, MessageBlockDoc.class)) {
            if (block.getMessages() == null) {
                continue;
            }
            for (MessageSlot slot : block.getMessages()) {
                if (slot == null || slot.getExt() == null) {
                    continue;
                }
                String attachedInfo = slot.getExt().get("attachedInfo");
                if (attachedInfo == null || !attachedInfo.contains(attachmentId)) {
                    continue;
                }
                candidates.add(new AttachmentMessageCandidate(
                        block.getConversationId(),
                        slot.getServerMsgId(),
                        attachedInfo));
                if (candidates.size() >= limit) {
                    return candidates;
                }
            }
        }
        return candidates;
    }

    public record AttachmentMessageCandidate(String conversationId, String serverMsgId, String attachedInfo) {
    }
}
