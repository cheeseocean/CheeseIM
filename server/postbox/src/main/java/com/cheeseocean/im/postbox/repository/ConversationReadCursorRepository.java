package com.cheeseocean.im.postbox.repository;

import com.cheeseocean.im.postbox.entity.ConversationReadCursorDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationReadCursorRepository extends MongoRepository<ConversationReadCursorDocument, String> {

    ConversationReadCursorDocument findByUserIdAndConversationId(String userId, String conversationId);
}
