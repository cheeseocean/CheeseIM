package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.cache.redis.StringSetCacheHelper;
import com.cheeseocean.im.common.core.enums.HandleResultEnum;
import com.cheeseocean.im.common.core.business.domain.Blacklist;
import com.cheeseocean.im.common.core.business.domain.FriendRequest;
import com.cheeseocean.im.common.core.business.domain.Friendship;
import com.cheeseocean.im.common.core.business.mongo.document.user.BlacklistDoc;
import com.cheeseocean.im.common.core.business.mongo.document.user.FriendRequestDoc;
import com.cheeseocean.im.common.core.business.mongo.document.user.FriendshipDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.BlacklistMongoRepository;
import com.cheeseocean.im.common.core.business.mongo.repository.FriendRequestMongoRepository;
import com.cheeseocean.im.common.core.business.mongo.repository.FriendshipMongoRepository;
import com.cheeseocean.im.common.core.business.repository.FriendRepository;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link FriendRepository} 的 MongoDB + Redis 实现。
 *
 * <p>Redis 负责好友列表、申请队列、黑名单的缓存，命中则跳过 MongoDB。
 * 写操作先落 MongoDB，再同步更新缓存。
 */
public class FriendRepositoryImpl implements FriendRepository {

    private static final int PENDING  = HandleResultEnum.PENDING.getCode();
    private static final int ACCEPTED = HandleResultEnum.ACCEPTED.getCode();
    private static final int REJECTED = HandleResultEnum.REJECTED.getCode();

    private final FriendshipMongoRepository friendshipRepo;
    private final FriendRequestMongoRepository friendRequestRepo;
    private final BlacklistMongoRepository blacklistRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    public FriendRepositoryImpl(FriendshipMongoRepository friendshipRepo,
                                FriendRequestMongoRepository friendRequestRepo,
                                BlacklistMongoRepository blacklistRepo,
                                RedisTemplate<String, Object> redisTemplate) {
        this.friendshipRepo = friendshipRepo;
        this.friendRequestRepo = friendRequestRepo;
        this.blacklistRepo = blacklistRepo;
        this.redisTemplate = redisTemplate;
    }

    // ── 好友关系 ──────────────────────────────────────────────────────────────

    @Override
    public boolean areAcceptedFriends(String userId, String friendUserId) {
        return StringSetCacheHelper.containsOrLoad(
                redisTemplate,
                friendKey(userId),
                friendLoadedKey(userId),
                friendUserId,
                () -> friendshipRepo.existsByOwnerUserIdAndFriendUserId(userId, friendUserId)
        );
    }

    @Override
    public List<String> listFriendIds(String userId) {
        return StringSetCacheHelper.getOrLoad(
                redisTemplate,
                friendKey(userId),
                friendLoadedKey(userId),
                () -> friendshipRepo.findByOwnerUserId(userId).stream()
                        .map(FriendshipDoc::getFriendUserId)
                        .sorted()
                        .toList()
        );
    }

    @Override
    public void acceptFriendPair(String userId, String friendUserId) {
        long now = System.currentTimeMillis();
        // 更新申请状态
        FriendRequestDoc req = friendRequestRepo
                .findByFromUserIdAndToUserIdAndHandleResult(friendUserId, userId, PENDING)
                .orElseThrow(() -> new IllegalStateException("好友申请不存在"));
        req.setHandleResult(ACCEPTED);
        req.setUpdatedAt(now);
        friendRequestRepo.save(req);
        // 双向建立好友关系
        saveFriendshipIfAbsent(userId, friendUserId, now);
        saveFriendshipIfAbsent(friendUserId, userId, now);
        // 更新缓存
        redisTemplate.opsForSet().add(friendKey(userId), friendUserId);
        redisTemplate.opsForSet().add(friendKey(friendUserId), userId);
        StringSetCacheHelper.markLoaded(redisTemplate, friendLoadedKey(userId));
        StringSetCacheHelper.markLoaded(redisTemplate, friendLoadedKey(friendUserId));
        redisTemplate.opsForSet().remove(incomingKey(userId), friendUserId);
        redisTemplate.opsForSet().remove(outgoingKey(friendUserId), userId);
    }

    // ── 好友申请 ──────────────────────────────────────────────────────────────

    @Override
    public List<FriendRequest> listIncomingPendingRequests(String userId) {
        List<FriendRequest> requests = friendRequestRepo
                .findByToUserIdAndHandleResultOrderByUpdatedAtDesc(userId, PENDING)
                .stream().map(this::toDomain).toList();
        replaceSet(incomingKey(userId), requests.stream().map(FriendRequest::getFromUserId).toList());
        return requests;
    }

    @Override
    public List<FriendRequest> listOutgoingPendingRequests(String userId) {
        List<FriendRequest> requests = friendRequestRepo
                .findByFromUserIdAndHandleResultOrderByUpdatedAtDesc(userId, PENDING)
                .stream().map(this::toDomain).toList();
        replaceSet(outgoingKey(userId), requests.stream().map(FriendRequest::getToUserId).toList());
        return requests;
    }

    @Override
    public void savePendingRequest(String fromUserId, String toUserId, String reqMsg) {
        long now = System.currentTimeMillis();
        FriendRequestDoc doc = new FriendRequestDoc();
        doc.setId(fromUserId + ":" + toUserId);
        doc.setFromUserId(fromUserId);
        doc.setToUserId(toUserId);
        doc.setHandleResult(PENDING);
        doc.setReqMsg(reqMsg);
        doc.setCreateTime(now);
        doc.setUpdatedAt(now);
        friendRequestRepo.save(doc);
        redisTemplate.opsForSet().add(incomingKey(toUserId), fromUserId);
        redisTemplate.opsForSet().add(outgoingKey(fromUserId), toUserId);
    }

    @Override
    public boolean hasOutgoingPendingRequest(String userId, String friendUserId) {
        Boolean cached = redisTemplate.opsForSet().isMember(outgoingKey(userId), friendUserId);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        boolean pending = friendRequestRepo.existsByFromUserIdAndToUserIdAndHandleResult(userId, friendUserId, PENDING);
        if (pending) {
            redisTemplate.opsForSet().add(outgoingKey(userId), friendUserId);
        }
        return pending;
    }

    @Override
    public boolean hasIncomingPendingRequest(String userId, String friendUserId) {
        Boolean cached = redisTemplate.opsForSet().isMember(incomingKey(userId), friendUserId);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        boolean pending = friendRequestRepo.existsByFromUserIdAndToUserIdAndHandleResult(friendUserId, userId, PENDING);
        if (pending) {
            redisTemplate.opsForSet().add(incomingKey(userId), friendUserId);
        }
        return pending;
    }

    @Override
    public Optional<FriendRequest> findPendingRequest(String fromUserId, String toUserId) {
        return friendRequestRepo.findByFromUserIdAndToUserIdAndHandleResult(fromUserId, toUserId, PENDING)
                .map(this::toDomain);
    }

    @Override
    public void rejectRequest(String fromUserId, String toUserId) {
        updateRequestResult(fromUserId, toUserId, REJECTED);
    }

    @Override
    public void cancelRequest(String fromUserId, String toUserId) {
        updateRequestResult(fromUserId, toUserId, REJECTED);
    }

    // ── 黑名单 ────────────────────────────────────────────────────────────────

    @Override
    public boolean isBlocked(String userId, String targetUserId) {
        return StringSetCacheHelper.containsOrLoad(
                redisTemplate,
                blacklistKey(targetUserId),
                blacklistLoadedKey(targetUserId),
                userId,
                () -> blacklistRepo.existsByOwnerUserIdAndBlockUserId(targetUserId, userId)
        );
    }

    @Override
    public void blockUser(String userId, String targetUserId) {
        if (blacklistRepo.existsByOwnerUserIdAndBlockUserId(userId, targetUserId)) {
            return;
        }
        BlacklistDoc doc = new BlacklistDoc();
        doc.setId(userId + ":" + targetUserId);
        doc.setOwnerUserId(userId);
        doc.setBlockUserId(targetUserId);
        doc.setCreatedAt(System.currentTimeMillis());
        blacklistRepo.save(doc);
        redisTemplate.opsForSet().add(blacklistKey(userId), targetUserId);
        StringSetCacheHelper.markLoaded(redisTemplate, blacklistLoadedKey(userId));
    }

    @Override
    public void unblockUser(String userId, String targetUserId) {
        blacklistRepo.deleteByOwnerUserIdAndBlockUserId(userId, targetUserId);
        redisTemplate.opsForSet().remove(blacklistKey(userId), targetUserId);
        StringSetCacheHelper.markLoaded(redisTemplate, blacklistLoadedKey(userId));
    }

    @Override
    public List<String> listBlockedUserIds(String userId) {
        return StringSetCacheHelper.getOrLoad(
                redisTemplate,
                blacklistKey(userId),
                blacklistLoadedKey(userId),
                () -> blacklistRepo.findByOwnerUserId(userId).stream()
                        .map(BlacklistDoc::getBlockUserId)
                        .sorted()
                        .toList()
        );
    }

    @Override
    public List<Blacklist> listBlacklist(String userId) {
        return blacklistRepo.findByOwnerUserId(userId).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private void updateRequestResult(String fromUserId, String toUserId, int result) {
        FriendRequestDoc doc = friendRequestRepo
                .findByFromUserIdAndToUserIdAndHandleResult(fromUserId, toUserId, PENDING)
                .orElseThrow(() -> new IllegalStateException("好友申请不存在"));
        doc.setHandleResult(result);
        doc.setUpdatedAt(System.currentTimeMillis());
        friendRequestRepo.save(doc);
        redisTemplate.opsForSet().remove(incomingKey(toUserId), fromUserId);
        redisTemplate.opsForSet().remove(outgoingKey(fromUserId), toUserId);
    }

    private void saveFriendshipIfAbsent(String ownerUserId, String friendUserId, long createdAt) {
        if (friendshipRepo.existsByOwnerUserIdAndFriendUserId(ownerUserId, friendUserId)) {
            return;
        }
        FriendshipDoc doc = new FriendshipDoc();
        doc.setId(ownerUserId + ":" + friendUserId);
        doc.setOwnerUserId(ownerUserId);
        doc.setFriendUserId(friendUserId);
        doc.setCreatedAt(createdAt);
        friendshipRepo.save(doc);
    }

    private void replaceSet(String key, Collection<String> values) {
        redisTemplate.delete(key);
        if (values != null && !values.isEmpty()) {
            redisTemplate.opsForSet().add(key, values.toArray());
        }
    }

    // ── 转换方法 ─────────────────────────────────────────────────────────────

    private FriendRequest toDomain(FriendRequestDoc doc) {
        FriendRequest req = new FriendRequest();
        req.setId(doc.getId());
        req.setFromUserId(doc.getFromUserId());
        req.setToUserId(doc.getToUserId());
        req.setHandleResult(HandleResultEnum.fromCode(doc.getHandleResult()));
        req.setReqMsg(doc.getReqMsg());
        req.setHandlerUserId(doc.getHandlerUserId());
        req.setHandleMsg(doc.getHandleMsg());
        req.setHandleTime(doc.getHandleTime());
        req.setEx(doc.getEx());
        req.setCreateTime(doc.getCreateTime());
        req.setUpdatedAt(doc.getUpdatedAt());
        return req;
    }

    private Friendship toDomain(FriendshipDoc doc) {
        Friendship f = new Friendship();
        f.setId(doc.getId());
        f.setOwnerUserId(doc.getOwnerUserId());
        f.setFriendUserId(doc.getFriendUserId());
        f.setRemark(doc.getRemark());
        f.setAddSource(doc.getAddSource());
        f.setOperatorUserId(doc.getOperatorUserId());
        f.setPinned(doc.isPinned());
        f.setEx(doc.getEx());
        f.setCreatedAt(doc.getCreatedAt() != null ? doc.getCreatedAt() : 0L);
        return f;
    }

    private Blacklist toDomain(BlacklistDoc doc) {
        Blacklist b = new Blacklist();
        b.setId(doc.getId());
        b.setOwnerUserId(doc.getOwnerUserId());
        b.setBlockUserId(doc.getBlockUserId());
        b.setAddSource(doc.getAddSource());
        b.setOperatorUserId(doc.getOperatorUserId());
        b.setEx(doc.getEx());
        b.setCreatedAt(doc.getCreatedAt() != null ? doc.getCreatedAt() : 0L);
        return b;
    }

    private String friendKey(String userId) { return RedisKeys.userFriends(userId); }
    private String friendLoadedKey(String userId) { return RedisKeys.userFriendsLoaded(userId); }
    private String incomingKey(String userId) { return RedisKeys.userIncomingFriendRequests(userId); }
    private String outgoingKey(String userId) { return RedisKeys.userOutgoingFriendRequests(userId); }
    private String blacklistKey(String userId) { return RedisKeys.userBlacklist(userId); }
    private String blacklistLoadedKey(String userId) { return RedisKeys.userBlacklistLoaded(userId); }
}
