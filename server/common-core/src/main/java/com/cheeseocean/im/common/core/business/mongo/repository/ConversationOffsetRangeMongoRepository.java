package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.ConversationOffsetRangeDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** seq_user 集合的 Spring Data 访问接口 */
public interface ConversationOffsetRangeMongoRepository
        extends MongoRepository<ConversationOffsetRangeDoc, String> {

    Optional<ConversationOffsetRangeDoc> findByOwnerUserIdAndConversationId(
            String ownerUserId, String conversationId);

    List<ConversationOffsetRangeDoc> findByOwnerUserIdAndConversationIdIn(String ownerUserId, List<String> conversationIds);

    List<ConversationOffsetRangeDoc> findByOwnerUserId(String ownerUserId);
}
