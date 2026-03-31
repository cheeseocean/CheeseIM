package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.user.FriendshipDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** friendships 集合的 Spring Data 访问接口 */
public interface FriendshipMongoRepository extends MongoRepository<FriendshipDoc, String> {

    List<FriendshipDoc> findByOwnerUserId(String ownerUserId);

    boolean existsByOwnerUserIdAndFriendUserId(String ownerUserId, String friendUserId);
}
