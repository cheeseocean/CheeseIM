package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.social.domain.FriendRequestDoc;
import com.cheeseocean.im.social.domain.FriendshipDoc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public class FriendRepository {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ACCEPTED = "accepted";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_CANCELLED = "cancelled";

    private final FriendRequestMongoRepository friendRequestMongoRepository;
    private final FriendshipMongoRepository friendshipMongoRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public FriendRepository(FriendRequestMongoRepository friendRequestMongoRepository,
                            FriendshipMongoRepository friendshipMongoRepository,
                            RedisTemplate<String, Object> redisTemplate) {
        this.friendRequestMongoRepository = friendRequestMongoRepository;
        this.friendshipMongoRepository = friendshipMongoRepository;
        this.redisTemplate = redisTemplate;
    }

    public void acceptFriendPair(String userId, String friendUserId) {
        long now = System.currentTimeMillis();
        FriendRequestDoc request = friendRequestMongoRepository
                .findByFromUserIdAndToUserIdAndStatus(friendUserId, userId, STATUS_PENDING)
                .orElseThrow(() -> new IllegalStateException("friend request not found"));
        request.setStatus(STATUS_ACCEPTED);
        request.setUpdatedAt(now);
        friendRequestMongoRepository.save(request);

        saveFriendshipIfAbsent(userId, friendUserId, now);
        saveFriendshipIfAbsent(friendUserId, userId, now);

        redisTemplate.opsForSet().add(friendKey(userId), friendUserId);
        redisTemplate.opsForSet().add(friendKey(friendUserId), userId);
        redisTemplate.opsForSet().remove(userIncomingKey(userId), friendUserId);
        redisTemplate.opsForSet().remove(userOutgoingKey(friendUserId), userId);
    }

    public void rejectPendingRequest(String fromUserId, String toUserId) {
        updateRequestStatus(fromUserId, toUserId, STATUS_REJECTED);
    }

    public void cancelPendingRequest(String fromUserId, String toUserId) {
        updateRequestStatus(fromUserId, toUserId, STATUS_CANCELLED);
    }

    public boolean areAcceptedFriends(String userId, String friendUserId) {
        Boolean cached = redisTemplate.opsForSet().isMember(friendKey(userId), friendUserId);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        boolean accepted = friendshipMongoRepository.existsByUserIdAndFriendUserId(userId, friendUserId);
        if (accepted) {
            redisTemplate.opsForSet().add(friendKey(userId), friendUserId);
        }
        return accepted;
    }

    public List<String> listFriendIds(String userId) {
        Set<Object> cached = redisTemplate.opsForSet().members(friendKey(userId));
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(String::valueOf).sorted().toList();
        }
        List<String> friendIds = friendshipMongoRepository.findByUserId(userId).stream()
                .map(FriendshipDoc::getFriendUserId)
                .sorted()
                .toList();
        replaceSet(friendKey(userId), friendIds);
        return friendIds;
    }

    public List<FriendRequestRecord> listIncomingRequests(String userId) {
        List<FriendRequestRecord> records = friendRequestMongoRepository
                .findByToUserIdAndStatusOrderByUpdatedAtDesc(userId, STATUS_PENDING)
                .stream()
                .map(doc -> new FriendRequestRecord(doc.getFromUserId(), doc.getToUserId(), doc.getRequestMessage()))
                .toList();
        replaceSet(userIncomingKey(userId), records.stream().map(FriendRequestRecord::getFromUserId).toList());
        return records;
    }

    public List<FriendRequestRecord> listOutgoingRequests(String userId) {
        List<FriendRequestRecord> records = friendRequestMongoRepository
                .findByFromUserIdAndStatusOrderByUpdatedAtDesc(userId, STATUS_PENDING)
                .stream()
                .map(doc -> new FriendRequestRecord(doc.getFromUserId(), doc.getToUserId(), doc.getRequestMessage()))
                .toList();
        replaceSet(userOutgoingKey(userId), records.stream().map(FriendRequestRecord::getToUserId).toList());
        return records;
    }

    public void savePendingRequest(String fromUserId, String toUserId, String requestMessage) {
        long now = System.currentTimeMillis();
        FriendRequestDoc request = new FriendRequestDoc();
        request.setId(requestId(fromUserId, toUserId));
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setStatus(STATUS_PENDING);
        request.setRequestMessage(requestMessage);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        friendRequestMongoRepository.save(request);

        redisTemplate.opsForSet().add(userIncomingKey(toUserId), fromUserId);
        redisTemplate.opsForSet().add(userOutgoingKey(fromUserId), toUserId);
    }

    public boolean hasOutgoingRequest(String userId, String friendUserId) {
        Boolean cached = redisTemplate.opsForSet().isMember(userOutgoingKey(userId), friendUserId);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        boolean pending = friendRequestMongoRepository.existsByFromUserIdAndToUserIdAndStatus(
                userId, friendUserId, STATUS_PENDING
        );
        if (pending) {
            redisTemplate.opsForSet().add(userOutgoingKey(userId), friendUserId);
        }
        return pending;
    }

    public FriendRequestRecord getPendingRequest(String fromUserId, String toUserId) {
        return friendRequestMongoRepository.findByFromUserIdAndToUserIdAndStatus(fromUserId, toUserId, STATUS_PENDING)
                .map(doc -> new FriendRequestRecord(doc.getFromUserId(), doc.getToUserId(), doc.getRequestMessage()))
                .orElse(null);
    }

    public boolean hasIncomingRequest(String userId, String friendUserId) {
        Boolean cached = redisTemplate.opsForSet().isMember(userIncomingKey(userId), friendUserId);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        boolean pending = friendRequestMongoRepository.existsByFromUserIdAndToUserIdAndStatus(
                friendUserId, userId, STATUS_PENDING
        );
        if (pending) {
            redisTemplate.opsForSet().add(userIncomingKey(userId), friendUserId);
        }
        return pending;
    }

    private void updateRequestStatus(String fromUserId, String toUserId, String status) {
        FriendRequestDoc request = friendRequestMongoRepository
                .findByFromUserIdAndToUserIdAndStatus(fromUserId, toUserId, STATUS_PENDING)
                .orElseThrow(() -> new IllegalStateException("friend request not found"));
        request.setStatus(status);
        request.setUpdatedAt(System.currentTimeMillis());
        friendRequestMongoRepository.save(request);
        redisTemplate.opsForSet().remove(userIncomingKey(toUserId), fromUserId);
        redisTemplate.opsForSet().remove(userOutgoingKey(fromUserId), toUserId);
    }

    private void saveFriendshipIfAbsent(String userId, String friendUserId, long createdAt) {
        if (friendshipMongoRepository.existsByUserIdAndFriendUserId(userId, friendUserId)) {
            return;
        }
        FriendshipDoc friendship = new FriendshipDoc();
        friendship.setId(friendshipId(userId, friendUserId));
        friendship.setUserId(userId);
        friendship.setFriendUserId(friendUserId);
        friendship.setCreatedAt(createdAt);
        friendshipMongoRepository.save(friendship);
    }

    private void replaceSet(String key, Collection<String> values) {
        redisTemplate.delete(key);
        if (values == null || values.isEmpty()) {
            return;
        }
        redisTemplate.opsForSet().add(key, values.toArray());
    }

    private String friendKey(String userId) {
        return RedisKeys.userFriends(userId);
    }

    private String userIncomingKey(String userId) {
        return RedisKeys.userIncomingFriendRequests(userId);
    }

    private String userOutgoingKey(String userId) {
        return RedisKeys.userOutgoingFriendRequests(userId);
    }

    private String requestId(String fromUserId, String toUserId) {
        return fromUserId + ":" + toUserId;
    }

    private String friendshipId(String userId, String friendUserId) {
        return userId + ":" + friendUserId;
    }

    public static final class FriendRequestRecord implements Serializable {
        private final String fromUserId;
        private final String toUserId;
        private final String requestMessage;

        public FriendRequestRecord(String fromUserId, String toUserId, String requestMessage) {
            this.fromUserId = fromUserId;
            this.toUserId = toUserId;
            this.requestMessage = requestMessage;
        }

        public String getFromUserId() {
            return fromUserId;
        }

        public String getToUserId() {
            return toUserId;
        }

        public String getRequestMessage() {
            return requestMessage;
        }
    }
}
