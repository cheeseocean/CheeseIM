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

import java.util.List;

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
}
