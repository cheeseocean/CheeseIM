package com.cheeseocean.im.postbox.history;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageIdMappingRepository extends MongoRepository<MessageIdMappingDoc, String> {
}
