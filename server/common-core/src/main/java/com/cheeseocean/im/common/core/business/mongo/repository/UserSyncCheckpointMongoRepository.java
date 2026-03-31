package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.UserSyncCheckpointDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserSyncCheckpointMongoRepository extends MongoRepository<UserSyncCheckpointDoc, String> {

    Optional<UserSyncCheckpointDoc> findByUserIdAndConversationId(String userId, String conversationId);

    List<UserSyncCheckpointDoc> findByUserIdAndConversationIdIn(String userId, List<String> conversationIds);

    List<UserSyncCheckpointDoc> findByUserId(String userId);
}
