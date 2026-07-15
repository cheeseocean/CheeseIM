package com.cheeseocean.im.business.service.friend;

import com.cheeseocean.im.common.api.business.domain.FriendRequest;
import com.cheeseocean.im.common.api.business.domain.Friendship;
import com.cheeseocean.im.common.api.enums.HandleResultEnum;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.core.cache.CacheRegion;
import com.cheeseocean.im.common.core.cache.CacheStore;
import com.cheeseocean.im.common.core.business.repository.BlacklistRepository;
import com.cheeseocean.im.common.core.business.repository.FriendRequestRepository;
import com.cheeseocean.im.common.core.business.repository.FriendshipRepository;
import com.cheeseocean.im.common.core.business.transaction.PersistenceTransactionExecutor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 好友关系服务实现。
 *
 * <p>通过好友关系、好友申请、黑名单三个仓储完成好友域业务逻辑，
 * 并通过 {@link FriendRealtimeNotifier} 向对端推送实时通知。
 * 好友列表和好友申请列表采用类型化远端缓存做读穿透，
 * 写路径统一在仓储提交后失效关联缓存。
 */
@Service
@DubboService
public class FriendRelationServiceImpl implements FriendRelationService {

    private static final Duration FRIEND_CACHE_TTL = Duration.ofMinutes(10);

    private final FriendshipRepository               friendshipRepository;
    private final FriendRequestRepository            friendRequestRepository;
    private final BlacklistRepository                blacklistRepository;
    private final FriendRealtimeNotifier             friendRealtimeNotifier;
    private final PersistenceTransactionExecutor     persistenceTransactionExecutor;
    private final CacheRegion<Friendship> friendshipCache;
    private final CacheRegion<List<Friendship>> friendListCache;
    private final CacheRegion<List<FriendRequest>> incomingRequestCache;
    private final CacheRegion<List<FriendRequest>> outgoingRequestCache;

    public FriendRelationServiceImpl(FriendshipRepository friendshipRepository,
                                     FriendRequestRepository friendRequestRepository,
                                     BlacklistRepository blacklistRepository,
                                     FriendRealtimeNotifier friendRealtimeNotifier,
                                     PersistenceTransactionExecutor persistenceTransactionExecutor,
                                     CacheStore cacheStore) {
        this.friendshipRepository = friendshipRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.blacklistRepository = blacklistRepository;
        this.friendRealtimeNotifier = friendRealtimeNotifier;
        this.persistenceTransactionExecutor = persistenceTransactionExecutor;
        this.friendshipCache = cacheStore.region("business:friend:detail:", Friendship.class, FRIEND_CACHE_TTL);
        this.friendListCache = cacheStore.listRegion("business:friend:list:", Friendship.class, FRIEND_CACHE_TTL);
        this.incomingRequestCache = cacheStore.listRegion("business:friend:incoming:", FriendRequest.class, FRIEND_CACHE_TTL);
        this.outgoingRequestCache = cacheStore.listRegion("business:friend:outgoing:", FriendRequest.class, FRIEND_CACHE_TTL);
    }

    private static String friendshipKey(String userId, String friendUserId) {
        return userId + ":" + friendUserId;
    }

    @Override
    public List<Friendship> listFriends(String userId) {
        if (!StringUtils.hasText(userId)) {
            return new ArrayList<>();
        }
        List<Friendship> friendships = friendListCache.getOrLoad(userId, () ->
                friendshipRepository.findOwnerFriends(userId, 0, 0)
        );
        return copyFriendships(friendships);
    }

    @Override
    public List<FriendRequest> listIncomingRequests(String userId) {
        if (!StringUtils.hasText(userId)) {
            return new ArrayList<>();
        }
        List<Integer> pendingResults = new ArrayList<>();
        pendingResults.add(HandleResultEnum.PENDING.getCode());
        List<FriendRequest> requests = incomingRequestCache.getOrLoad(userId, () ->
                friendRequestRepository.findIncoming(userId, pendingResults, 0, 0)
        );
        return copyRequests(requests);
    }

    @Override
    public List<FriendRequest> listOutgoingRequests(String userId) {
        if (!StringUtils.hasText(userId)) {
            return new ArrayList<>();
        }
        List<Integer> pendingResults = new ArrayList<>();
        pendingResults.add(HandleResultEnum.PENDING.getCode());
        List<FriendRequest> requests = outgoingRequestCache.getOrLoad(userId, () ->
                friendRequestRepository.findOutgoing(userId, pendingResults, 0, 0)
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
            friendRealtimeNotifier.friendRequestCreated(userId, friendUserId, requestMessage);
            result = request;
        } else {
            result = existing;
        }
        evictRequestCaches(userId, friendUserId);
        return copyRequest(result);
    }

    @Override
    public Friendship acceptFriendRequest(String userId, String friendUserId) {
        long       now  = System.currentTimeMillis();
        Friendship left = new Friendship();
        left.setUserId(userId);
        left.setFriendId(friendUserId);
        left.setCreatedAt(now);
        Friendship right = new Friendship();
        right.setUserId(friendUserId);
        right.setFriendId(userId);
        right.setCreatedAt(now);
        List<Friendship> friendships = new ArrayList<>();
        friendships.add(left);
        friendships.add(right);
        persistenceTransactionExecutor.execute(() -> {
            FriendRequest incoming = friendRequestRepository.find(friendUserId, userId);
            if (incoming == null || !incoming.isPending()) {
                throw new IllegalStateException("friend request not found");
            }
            incoming.setHandleResult(HandleResultEnum.ACCEPTED);
            incoming.setUpdatedAt(now);
            friendRequestRepository.update(incoming);
            friendshipRepository.saveAll(friendships);
        });

        // 通知和缓存失效只允许发生在数据库事务提交之后，避免回滚时暴露未生效关系。
        friendRealtimeNotifier.friendRequestAccepted(userId, friendUserId);
        Set<String> userIds = new LinkedHashSet<>();
        userIds.add(userId);
        userIds.add(friendUserId);
        evictFriendshipCaches(userIds, friendships);
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
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("handleResult", HandleResultEnum.REJECTED.getCode());
        fields.put("updatedAt", now);
        friendRequestRepository.updateFields(friendUserId, userId, fields);
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
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("handleResult", HandleResultEnum.REJECTED.getCode());
        fields.put("updatedAt", now);
        friendRequestRepository.updateFields(userId, friendUserId, fields);
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
            return new ArrayList<>();
        }
        return blacklistRepository.listBlockedUserIds(userId);
    }

    /**
     * 读取单条好友关系，优先命中缓存。
     */
    private Friendship getFriendship(String userId, String friendUserId) {
        String key = friendshipKey(userId, friendUserId);
        Friendship friendship = friendshipCache.getOrLoad(
                key,
                () -> friendshipRepository.find(userId, friendUserId)
        );
        return copyFriendship(friendship);
    }

    /**
     * 好友关系发生变化后，失效双方相关缓存。
     */
    private void evictFriendshipCaches(Set<String> userIds, List<Friendship> friendships) {
        friendListCache.evictAll(userIds);
        Set<String> detailKeys = friendships.stream()
                .filter(Objects::nonNull)
                .map(friendship -> friendshipKey(friendship.getUserId(), friendship.getFriendId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!detailKeys.isEmpty()) {
            friendshipCache.evictAll(detailKeys);
        }
    }

    /**
     * 好友申请变化后，失效双方的来向/去向申请列表缓存。
     */
    private void evictRequestCaches(String userId, String friendUserId) {
        incomingRequestCache.evict(userId);
        outgoingRequestCache.evict(userId);
        incomingRequestCache.evict(friendUserId);
        outgoingRequestCache.evict(friendUserId);
    }

    private List<Friendship> copyFriendships(List<Friendship> friendships) {
        if (friendships == null || friendships.isEmpty()) {
            return new ArrayList<>();
        }
        return friendships.stream()
                .map(this::copyFriendship)
                .collect(Collectors.toCollection(ArrayList::new));
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
            return new ArrayList<>();
        }
        return requests.stream()
                .map(this::copyRequest)
                .collect(Collectors.toCollection(ArrayList::new));
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
