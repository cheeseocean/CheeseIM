package com.cheeseocean.im.common.core.business.mongo.impl;

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
import com.cheeseocean.im.common.core.enums.HandleResultEnum;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link FriendRepository} 的 MongoDB 实现。
 */
public class FriendRepositoryImpl implements FriendRepository {

    private static final int PENDING = HandleResultEnum.PENDING.getCode();
    private static final int ACCEPTED = HandleResultEnum.ACCEPTED.getCode();
    private static final int REJECTED = HandleResultEnum.REJECTED.getCode();

    private final FriendshipMongoRepository friendshipRepo;
    private final FriendRequestMongoRepository friendRequestRepo;
    private final BlacklistMongoRepository blacklistRepo;

    public FriendRepositoryImpl(FriendshipMongoRepository friendshipRepo,
                                FriendRequestMongoRepository friendRequestRepo,
                                BlacklistMongoRepository blacklistRepo) {
        this.friendshipRepo = friendshipRepo;
        this.friendRequestRepo = friendRequestRepo;
        this.blacklistRepo = blacklistRepo;
    }

    @Override
    public boolean areAcceptedFriends(String userId, String friendUserId) {
        return friendshipRepo.existsByOwnerUserIdAndFriendUserId(userId, friendUserId);
    }

    @Override
    public List<String> listFriendIds(String userId) {
        return friendshipRepo.findByOwnerUserId(userId).stream()
                .map(FriendshipDoc::getFriendUserId)
                .sorted()
                .toList();
    }

    @Override
    public void acceptFriendPair(String userId, String friendUserId) {
        long now = System.currentTimeMillis();
        FriendRequestDoc req = friendRequestRepo
                .findByFromUserIdAndToUserIdAndHandleResult(friendUserId, userId, PENDING)
                .orElseThrow(() -> new IllegalStateException("好友申请不存在"));
        req.setHandleResult(ACCEPTED);
        req.setUpdatedAt(now);
        friendRequestRepo.save(req);
        saveFriendshipIfAbsent(userId, friendUserId, now);
        saveFriendshipIfAbsent(friendUserId, userId, now);
    }

    @Override
    public List<FriendRequest> listIncomingPendingRequests(String userId) {
        return friendRequestRepo
                .findByToUserIdAndHandleResultOrderByUpdatedAtDesc(userId, PENDING)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<FriendRequest> listOutgoingPendingRequests(String userId) {
        return friendRequestRepo
                .findByFromUserIdAndHandleResultOrderByUpdatedAtDesc(userId, PENDING)
                .stream().map(this::toDomain).toList();
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
    }

    @Override
    public boolean hasOutgoingPendingRequest(String userId, String friendUserId) {
        return friendRequestRepo.existsByFromUserIdAndToUserIdAndHandleResult(userId, friendUserId, PENDING);
    }

    @Override
    public boolean hasIncomingPendingRequest(String userId, String friendUserId) {
        return friendRequestRepo.existsByFromUserIdAndToUserIdAndHandleResult(friendUserId, userId, PENDING);
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

    @Override
    public boolean isBlocked(String userId, String targetUserId) {
        return blacklistRepo.existsByOwnerUserIdAndBlockUserId(targetUserId, userId);
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
    }

    @Override
    public void unblockUser(String userId, String targetUserId) {
        blacklistRepo.deleteByOwnerUserIdAndBlockUserId(userId, targetUserId);
    }

    @Override
    public List<String> listBlockedUserIds(String userId) {
        return blacklistRepo.findByOwnerUserId(userId).stream()
                .map(BlacklistDoc::getBlockUserId)
                .sorted()
                .toList();
    }

    @Override
    public List<Blacklist> listBlacklist(String userId) {
        return blacklistRepo.findByOwnerUserId(userId).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    private void updateRequestResult(String fromUserId, String toUserId, int result) {
        FriendRequestDoc doc = friendRequestRepo
                .findByFromUserIdAndToUserIdAndHandleResult(fromUserId, toUserId, PENDING)
                .orElseThrow(() -> new IllegalStateException("好友申请不存在"));
        doc.setHandleResult(result);
        doc.setUpdatedAt(System.currentTimeMillis());
        friendRequestRepo.save(doc);
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
}
