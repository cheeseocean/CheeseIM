package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.ConversationSequence;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.ConversationSequenceDoc;
import com.cheeseocean.im.common.core.business.repository.ConversationSequenceRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * {@link ConversationSequenceRepository} 的 MongoDB 实现。
 */
public class ConversationSequenceRepositoryImpl implements ConversationSequenceRepository {

    private final MongoTemplate mongoTemplate;

    public ConversationSequenceRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long allocate(String conversationId, long size) {
        // 通过 findAndModify + inc 保证会话级 seq 分配原子递增。
        Query query = Query.query(Criteria.where("_id").is(conversationId));
        Update update = new Update()
                .setOnInsert("_id", conversationId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("minSeq", 0L)
                .inc("maxSeq", size);
        ConversationSequenceDoc doc = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(false).upsert(true),
                ConversationSequenceDoc.class
        );
        return doc == null ? 0L : doc.getMaxSeq();
    }

    @Override
    public void setMaxSeq(String conversationId, long seq) {
        upsertSeq(conversationId, "maxSeq", seq, "minSeq", 0L);
    }

    @Override
    public long getMaxSeq(String conversationId) {
        ConversationSequence range = find(conversationId);
        return range == null ? 0L : range.getMaxSeq();
    }

    @Override
    public void setMinSeq(String conversationId, long seq) {
        upsertSeq(conversationId, "minSeq", seq, "maxSeq", 0L);
    }

    @Override
    public long getMinSeq(String conversationId) {
        ConversationSequence range = find(conversationId);
        return range == null ? 0L : range.getMinSeq();
    }

    @Override
    public ConversationSequence find(String conversationId) {
        Query                   query = Query.query(Criteria.where("_id").is(conversationId));
        ConversationSequenceDoc doc   = mongoTemplate.findOne(query, ConversationSequenceDoc.class);
        if (doc == null) {
            return null;
        }
        ConversationSequence range = new ConversationSequence();
        range.setConversationId(doc.getConversationId());
        range.setMaxSeq(doc.getMaxSeq());
        range.setMinSeq(doc.getMinSeq());
        return range;
    }

    private void upsertSeq(String conversationId, String seqField, long seq, String defaultField, long defaultValue) {
        // maxSeq/minSeq 分开维护，但首次写入时要保证另一侧字段存在。
        Query query = Query.query(Criteria.where("_id").is(conversationId));
        Update update = new Update()
                .setOnInsert("_id", conversationId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert(defaultField, defaultValue)
                .set(seqField, seq);
        mongoTemplate.upsert(query, update, ConversationSequenceDoc.class);
    }
}
