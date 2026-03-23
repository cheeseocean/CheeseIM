package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.domain.FriendRequestDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestMongoRepository extends MongoRepository<FriendRequestDoc, String> {

    List<FriendRequestDoc> findByToUserIdAndStatusOrderByUpdatedAtDesc(String toUserId, String status);

    List<FriendRequestDoc> findByFromUserIdAndStatusOrderByUpdatedAtDesc(String fromUserId, String status);

    boolean existsByFromUserIdAndToUserIdAndStatus(String fromUserId, String toUserId, String status);

    Optional<FriendRequestDoc> findByFromUserIdAndToUserIdAndStatus(String fromUserId, String toUserId, String status);
}
