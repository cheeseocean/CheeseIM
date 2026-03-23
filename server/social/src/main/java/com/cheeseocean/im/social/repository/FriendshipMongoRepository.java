package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.domain.FriendshipDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FriendshipMongoRepository extends MongoRepository<FriendshipDoc, String> {

    List<FriendshipDoc> findByUserId(String userId);

    boolean existsByUserIdAndFriendUserId(String userId, String friendUserId);
}
