package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.domain.BlacklistDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BlacklistMongoRepository extends MongoRepository<BlacklistDoc, String> {

    boolean existsByUserIdAndTargetUserId(String userId, String targetUserId);

    List<BlacklistDoc> findByUserId(String userId);

    void deleteByUserIdAndTargetUserId(String userId, String targetUserId);
}
