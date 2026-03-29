package com.cheeseocean.im.business.infra.mongo.repository;

import com.cheeseocean.im.business.infra.mongo.document.BlacklistDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** blacklist 集合的 Spring Data 访问接口 */
public interface BlacklistMongoRepository extends MongoRepository<BlacklistDoc, String> {

    boolean existsByOwnerUserIdAndBlockUserId(String ownerUserId, String blockUserId);

    List<BlacklistDoc> findByOwnerUserId(String ownerUserId);

    void deleteByOwnerUserIdAndBlockUserId(String ownerUserId, String blockUserId);
}
