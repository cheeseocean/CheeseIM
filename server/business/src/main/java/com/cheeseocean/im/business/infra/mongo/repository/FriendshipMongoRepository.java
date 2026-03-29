package com.cheeseocean.im.business.infra.mongo.repository;

import com.cheeseocean.im.business.infra.mongo.document.FriendshipDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** friendships 集合的 Spring Data 访问接口 */
public interface FriendshipMongoRepository extends MongoRepository<FriendshipDoc, String> {

    List<FriendshipDoc> findByOwnerUserId(String ownerUserId);

    boolean existsByOwnerUserIdAndFriendUserId(String ownerUserId, String friendUserId);
}
