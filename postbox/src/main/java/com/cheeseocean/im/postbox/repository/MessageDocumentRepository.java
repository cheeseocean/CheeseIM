package com.cheeseocean.im.postbox.repository;

import com.cheeseocean.im.postbox.entity.MessageDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageDocumentRepository extends MongoRepository<MessageDocument, String> {

    MessageDocument findByServerMsgId(String serverMsgId);
}
