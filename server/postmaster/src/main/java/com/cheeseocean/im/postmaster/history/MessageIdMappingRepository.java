package com.cheeseocean.im.postmaster.history;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MessageIdMappingRepository extends MongoRepository<MessageIdMappingDoc, String> {

    Optional<MessageIdMappingDoc> findByServerMsgId(String serverMsgId);
}
