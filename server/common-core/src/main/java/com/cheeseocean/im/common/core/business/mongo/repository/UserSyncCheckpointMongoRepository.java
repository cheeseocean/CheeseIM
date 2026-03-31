package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationSyncPointDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserSyncCheckpointMongoRepository extends MongoRepository<UserConversationSyncPointDoc, String> {

    Optional<UserConversationSyncPointDoc> findByUserIdAndConversationId(String userId, String conversationId);

    List<UserConversationSyncPointDoc> findByUserIdAndConversationIdIn(String userId, List<String> conversationIds);

    List<UserConversationSyncPointDoc> findByUserId(String userId);
}
