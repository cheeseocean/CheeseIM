package com.cheeseocean.im.business.infra.mongo.repository;

import com.cheeseocean.im.business.infra.mongo.document.FriendRequestDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** friend_requests 集合的 Spring Data 访问接口 */
public interface FriendRequestMongoRepository extends MongoRepository<FriendRequestDoc, String> {

    List<FriendRequestDoc> findByToUserIdAndHandleResultOrderByUpdatedAtDesc(String toUserId, int handleResult);

    List<FriendRequestDoc> findByFromUserIdAndHandleResultOrderByUpdatedAtDesc(String fromUserId, int handleResult);

    boolean existsByFromUserIdAndToUserIdAndHandleResult(String fromUserId, String toUserId, int handleResult);

    Optional<FriendRequestDoc> findByFromUserIdAndToUserIdAndHandleResult(String fromUserId, String toUserId, int handleResult);
}
