package com.cheeseocean.im.postbox.repository;

import com.cheeseocean.im.postbox.entity.InboxDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InboxDocumentRepository extends MongoRepository<InboxDocument, String> {

    List<InboxDocument> findByUserIdAndReadIsFalseOrderBySequenceAsc(String userId);
}
