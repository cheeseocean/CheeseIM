package com.cheeseocean.im.business.service.friend;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.cheeseocean.im.common.api.business.domain.FriendRequest;
import com.cheeseocean.im.common.api.business.domain.Friendship;
import com.cheeseocean.im.common.api.enums.HandleResultEnum;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.core.business.repository.BlacklistRepository;
import com.cheeseocean.im.common.core.business.repository.FriendRequestRepository;
import com.cheeseocean.im.common.core.business.repository.FriendshipRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 好友关系服务实现。
 *
 * <p>通过好友关系、好友申请、黑名单三个仓储完成好友域业务逻辑，
 * 并通过 {@link FriendRealtimeNotifier} 向对端推送实时通知。
 * 好友列表和好友申请列表采用 JetCache 做读穿透缓存，
 * 写路径统一在仓储提交后失效关联缓存。
 */
@Service
@DubboService
public class FriendRelationServiceImpl implements FriendRelationService {

    private static final int FRIEND_CACHE_EXPIRE_SECONDS       = 60 * 10;
    private static final int FRIEND_CACHE_LOCAL_EXPIRE_SECONDS = 60;
    private static final int FRIEND_CACHE_LOCAL_LIMIT          = 1_000;

    private final FriendshipRepository               friendshipRepository;
    private final FriendRequestRepository            friendRequestRepository;
    private final BlacklistRepository                blacklistRepository;
    private final FriendRealtimeNotifier             friendRealtimeNotifier;
    private final Cache<String, Friendship>          friendshipCache;
    private final Cache<String, List<Friendship>>    friendListCache;
    private final Cache<String, List<FriendRequest>> incomingRequestCache;
    private final Cache<String, List<FriendRequest>> outgoingRequestCache;

    public FriendRelationServiceImpl(FriendshipRepository friendshipRepository,
                                     FriendRequestRepository friendRequestRepository,
                                     BlacklistRepository blacklistRepository,
                                     FriendRealtimeNotifier friendRealtimeNotifier,
                                     CacheManager cacheManager) {
        this.friendshipRepository = friendshipRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.blacklistRepository = blacklistRepository;
        this.friendRealtimeNotifier = friendRealtimeNotifier;
        this.friendshipCache = cacheManager.getOrCreateCache(
                QuickConfig.newBuilder("business:friend:detail:")
                        .expire(Duration.ofSeconds(FRIEND_CACHE_EXPIRE_SECONDS))
                        .localExpire(Duration.ofSeconds(FRIEND_CACHE_LOCAL_EXPIRE_SECONDS))
                        .cacheType(CacheType.BOTH)
                        .localLimit(FRIEND_CACHE_LOCAL_LIMIT)
                        .build()
        );
        this.friendListCache = cacheManager.getOrCreateCache(
                QuickConfig.newBuilder("business:friend:list:")
                        .expire(Duration.ofSeconds(FRIEND_CACHE_EXPIRE_SECONDS))
                        .localExpire(Duration.ofSeconds(FRIEND_CACHE_LOCAL_EXPIRE_SECONDS))
                        .cacheType(CacheType.BOTH)
                        .localLimit(FRIEND_CACHE_LOCAL_LIMIT)
                        .build()
        );
        this.incomingRequestCache = cacheManager.getOrCreateCache(
                QuickConfig.newBuilder("business:friend:incoming:")
                        .expire(Duration.ofSeconds(FRIEND_CACHE_EXPIRE_SECONDS))
                        .cacheType(CacheType.REMOTE)
                        .build()
        );
        this.outgoingRequestCache = cacheManager.getOrCreateCache(
                QuickConfig.newBuilder("business:friend:outgoing:")
                        .expire(Duration.ofSeconds(FRIEND_CACHE_EXPIRE_SECONDS))
                        .cacheType(CacheType.REMOTE)
                        .build()
        );
    }

    private static String friendshipKey(String userId, String friendUserId) {
        return userId + ":" + friendUserId;
    }

    @Override
    public List<Friendship> listFriends(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        List<Friendship> friendships = friendListCache.computeIfAbsent(userId, key ->
                friendshipRepository.findOwnerFriends(key, 0, 0)
        );
        return copyFriendships(friendships);
    }

    @Override
    public List<FriendRequest> listIncomingRequests(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        List<FriendRequest> requests = incomingRequestCache.computeIfAbsent(userId, key ->
                friendRequestRepository.findIncoming(key, List.of(HandleResultEnum.PENDING.getCode()), 0, 0)
        );
        return copyRequests(requests);
    }

    @Override
    public List<FriendRequest> listOutgoingRequests(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        List<FriendRequest> requests = outgoingRequestCache.computeIfAbsent(userId, key ->
                friendRequestRepository.findOutgoing(key, List.of(HandleResultEnum.PENDING.getCode()), 0, 0)
        );
        return copyRequests(requests);
    }

    @Override
    public FriendRequest sendFriendRequest(String userId, String friendUserId, String requestMessage) {
        if (userId == null || userId.isBlank() || friendUserId == null || friendUserId.isBlank()) {
            throw new IllegalStateException("friend user required");
        }
        if (userId.equals(friendUserId)) {
            throw new IllegalStateException("cannot add self as friend");
        }
        if (friendshipRepository.find(userId, friendUserId) != null) {
            throw new IllegalStateException("friend already added");
        }
        FriendRequest incoming = friendRequestRepository.find(friendUserId, userId);
        if (incoming != null && incoming.isPending()) {
            throw new IllegalStateException("incoming friend request pending; accept instead");
        }
        FriendRequest existing = friendRequestRepository.find(userId, friendUserId);
        FriendRequest result;
        if (existing == null || !existing.isPending()) {
            FriendRequest request = new FriendRequest();
            request.setFromUserId(userId);
            request.setToUserId(friendUserId);
            request.setReqMsg(requestMessage);
            request.setHandleResult(HandleResultEnum.PENDING);
            long now = System.currentTimeMillis();
            request.setCreateTime(now);
            request.setUpdatedAt(now);
            friendRequestRepository.update(request);
            friendRealtimeNotifier.friendRequestCreated(userId, friendUserId);
            result = request;
        } else {
            result = existing;
        }
        evictRequestCaches(userId, friendUserId);
        return copyRequest(result);
    }

    @Override
    public Friendship acceptFriendRequest(String userId, String friendUserId) {
        FriendRequest incoming = friendRequestRepository.find(friendUserId, userId);
        if (incoming == null || !incoming.isPending()) {
            throw new IllegalStateException("friend request not found");
        }
        incoming.setHandleResult(HandleResultEnum.ACCEPTED);
        incoming.setUpdatedAt(System.currentTimeMillis());
        friendRequestRepository.update(incoming);
        long       now  = System.currentTimeMillis();
        Friendship left = new Friendship();
        left.setUserId(userId);
        left.setFriendId(friendUserId);
        left.setCreatedAt(now);
        Friendship right = new Friendship();
        right.setUserId(friendUserId);
        right.setFriendId(userId);
        right.setCreatedAt(now);
        friendshipRepository.saveAll(List.of(left, right));
        friendRealtimeNotifier.friendRequestAccepted(userId, friendUserId);
        evictFriendshipCaches(Set.of(userId, friendUserId), List.of(left, right));
        evictRequestCaches(userId, friendUserId);
        return copyFriendship(left);
    }

    @Override
    public FriendRequest rejectFriendRequest(String userId, String friendUserId) {
        FriendRequest incoming = friendRequestRepository.find(friendUserId, userId);
        if (incoming == null || !incoming.isPending()) {
            throw new IllegalStateException("friend request not found");
        }
        long now = System.currentTimeMillis();
        friendRequestRepository.updateFields(
                friendUserId,
                userId,
                Map.of(
                        "handleResult", HandleResultEnum.REJECTED.getCode(),
                        "updatedAt", now
                )
        );
        incoming.setHandleResult(HandleResultEnum.REJECTED);
        incoming.setUpdatedAt(now);
        friendRealtimeNotifier.friendRequestRejected(userId, friendUserId);
        evictRequestCaches(userId, friendUserId);
        return copyRequest(incoming);
    }

    @Override
    public FriendRequest cancelFriendRequest(String userId, String friendUserId) {
        FriendRequest outgoing = friendRequestRepository.find(userId, friendUserId);
        if (outgoing == null || !outgoing.isPending()) {
            throw new IllegalStateException("friend request not found");
        }
        long now = System.currentTimeMillis();
        friendRequestRepository.updateFields(
                userId,
                friendUserId,
                Map.of(
                        "handleResult", HandleResultEnum.REJECTED.getCode(),
                        "updatedAt", now
                )
        );
        outgoing.setHandleResult(HandleResultEnum.REJECTED);
        outgoing.setUpdatedAt(now);
        friendRealtimeNotifier.friendRequestCancelled(userId, friendUserId);
        evictRequestCaches(userId, friendUserId);
        return copyRequest(outgoing);
    }

    @Override
    public boolean areAcceptedFriends(String userId, String friendUserId) {
        if (userId == null || friendUserId == null) {
            return false;
        }
        return getFriendship(userId, friendUserId) != null;
    }

    @Override
    public boolean isBlocked(String userId, String targetUserId) {
        if (userId == null || targetUserId == null) {
            return false;
        }
        return blacklistRepository.isBlocked(userId, targetUserId);
    }

    @Override
    public void blockUser(String userId, String targetUserId) {
        if (userId == null || userId.isBlank() || targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("userId and targetUserId required");
        }
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("cannot block self");
        }
        blacklistRepository.blockUser(userId, targetUserId);
        friendRealtimeNotifier.blackAdded(userId, targetUserId);
    }

    @Override
    public void unblockUser(String userId, String targetUserId) {
        if (userId == null || targetUserId == null) {
            return;
        }
        blacklistRepository.unblockUser(userId, targetUserId);
        friendRealtimeNotifier.blackDeleted(userId, targetUserId);
    }

    @Override
    public List<String> listBlockedUserIds(String userId) {
        if (userId == null) {
            return List.of();
        }
        return blacklistRepository.listBlockedUserIds(userId);
    }

    /**
     * 读取单条好友关系，优先命中缓存。
     */
    private Friendship getFriendship(String userId, String friendUserId) {
        String key = friendshipKey(userId, friendUserId);
        Friendship friendship = friendshipCache.computeIfAbsent(
                key,
                ignored -> friendshipRepository.find(userId, friendUserId)
        );
        return copyFriendship(friendship);
    }

    /**
     * 好友关系发生变化后，失效双方相关缓存。
     */
    private void evictFriendshipCaches(Set<String> userIds, List<Friendship> friendships) {
        friendListCache.removeAll(userIds);
        Set<String> detailKeys = friendships.stream()
                .filter(friendship -> friendship != null)
                .map(friendship -> friendshipKey(friendship.getUserId(), friendship.getFriendId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!detailKeys.isEmpty()) {
            friendshipCache.removeAll(detailKeys);
        }
    }

    /**
     * 好友申请变化后，失效双方的来向/去向申请列表缓存。
     */
    private void evictRequestCaches(String userId, String friendUserId) {
        incomingRequestCache.remove(userId);
        outgoingRequestCache.remove(userId);
        incomingRequestCache.remove(friendUserId);
        outgoingRequestCache.remove(friendUserId);
    }

    private List<Friendship> copyFriendships(List<Friendship> friendships) {
        if (friendships == null || friendships.isEmpty()) {
            return List.of();
        }
        return friendships.stream()
                .map(this::copyFriendship)
                .toList();
    }

    private Friendship copyFriendship(Friendship friendship) {
        if (friendship == null) {
            return null;
        }
        Friendship copy = new Friendship();
        copy.setId(friendship.getId());
        copy.setUserId(friendship.getUserId());
        copy.setFriendId(friendship.getFriendId());
        copy.setRemark(friendship.getRemark());
        copy.setAddSource(friendship.getAddSource());
        copy.setOperatorId(friendship.getOperatorId());
        copy.setPinned(friendship.isPinned());
        copy.setEx(friendship.getEx());
        copy.setCreatedAt(friendship.getCreatedAt());
        return copy;
    }

    private List<FriendRequest> copyRequests(List<FriendRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(this::copyRequest)
                .toList();
    }

    private FriendRequest copyRequest(FriendRequest request) {
        if (request == null) {
            return null;
        }
        FriendRequest copy = new FriendRequest();
        copy.setId(request.getId());
        copy.setFromUserId(request.getFromUserId());
        copy.setToUserId(request.getToUserId());
        copy.setHandleResult(request.getHandleResult());
        copy.setReqMsg(request.getReqMsg());
        copy.setHandlerUserId(request.getHandlerUserId());
        copy.setHandleMsg(request.getHandleMsg());
        copy.setHandleTime(request.getHandleTime());
        copy.setEx(request.getEx());
        copy.setCreateTime(request.getCreateTime());
        copy.setUpdatedAt(request.getUpdatedAt());
        return copy;
    }
}
