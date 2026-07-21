package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.enums.ReceiveOption;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.ConversationDeliveryPreferenceDoc;
import com.cheeseocean.im.common.core.business.repository.ConversationDeliveryPreferenceRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

/**
 * {@link ConversationDeliveryPreferenceRepository} 的 MongoDB 实现。
 */
public class ConversationDeliveryPreferenceRepositoryImpl
        implements ConversationDeliveryPreferenceRepository {

    private final MongoTemplate mongoTemplate;

    public ConversationDeliveryPreferenceRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void setReceiveOptions(List<String> ownerUserIds,
                                  String conversationId,
                                  int receiveOption) {
        ReceiveOption option = ReceiveOption.fromCode(receiveOption);
        List<String> normalized = ownerUserIds == null ? List.of() : ownerUserIds.stream()
                .filter(ownerUserId -> ownerUserId != null && !ownerUserId.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return;
        }
        if (option == ReceiveOption.RECEIVE) {
            mongoTemplate.remove(
                    Query.query(Criteria.where("conversationId").is(conversationId)
                            .and("ownerUserId").in(normalized)),
                    ConversationDeliveryPreferenceDoc.class);
            return;
        }
        BulkOperations bulk = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                ConversationDeliveryPreferenceDoc.class);
        Instant updatedAt = Instant.now();
        for (String ownerUserId : normalized) {
            bulk.upsert(
                    identity(ownerUserId, conversationId),
                    new Update()
                            .setOnInsert("_id", docId(ownerUserId, conversationId))
                            .set("conversationId", conversationId)
                            .set("ownerUserId", ownerUserId)
                            .set("receiveOption", receiveOption)
                            .set("updatedAt", updatedAt));
        }
        bulk.execute();
    }

    @Override
    public void remove(String ownerUserId, String conversationId) {
        mongoTemplate.remove(
                identity(ownerUserId, conversationId),
                ConversationDeliveryPreferenceDoc.class);
    }

    @Override
    public List<String> findBlockedOwnerUserIds(String conversationId) {
        Query query = Query.query(Criteria.where("conversationId").is(conversationId)
                .and("receiveOption").is(ReceiveOption.BLOCK.getCode()));
        query.fields().include("ownerUserId");
        return mongoTemplate.find(query, ConversationDeliveryPreferenceDoc.class)
                .stream()
                .map(ConversationDeliveryPreferenceDoc::getOwnerUserId)
                .toList();
    }

    private Query identity(String ownerUserId, String conversationId) {
        return Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId))
                .and("conversationId").is(conversationId)
                .and("ownerUserId").is(ownerUserId));
    }

    private String docId(String ownerUserId, String conversationId) {
        return ownerUserId + ":" + conversationId;
    }
}
