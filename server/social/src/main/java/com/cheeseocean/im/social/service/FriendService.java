package com.cheeseocean.im.social.service;

import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.core.auth.FriendRequestSummary;
import com.cheeseocean.im.common.core.auth.FriendSummary;
import com.cheeseocean.im.social.repository.FriendRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@DubboService
public class FriendService implements FriendRelationService {

    private final FriendRepository friendRepository;
    private final FriendRealtimeNotifier friendRealtimeNotifier;

    public FriendService(FriendRepository friendRepository,
                         FriendRealtimeNotifier friendRealtimeNotifier) {
        this.friendRepository = friendRepository;
        this.friendRealtimeNotifier = friendRealtimeNotifier;
    }

    @Override
    public List<FriendSummary> listFriends(String userId) {
        return friendRepository.listFriendIds(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<FriendRequestSummary> listIncomingRequests(String userId) {
        return friendRepository.listIncomingRequests(userId).stream()
                .map(record -> toRequestSummary(record.getFromUserId(), "incoming", "pending", record.getRequestMessage()))
                .toList();
    }

    @Override
    public List<FriendRequestSummary> listOutgoingRequests(String userId) {
        return friendRepository.listOutgoingRequests(userId).stream()
                .map(record -> toRequestSummary(record.getToUserId(), "outgoing", "pending", record.getRequestMessage()))
                .toList();
    }

    @Override
    public FriendRequestSummary sendFriendRequest(String userId, String friendUserId, String requestMessage) {
        if (userId == null || userId.isBlank() || friendUserId == null || friendUserId.isBlank()) {
            throw new IllegalStateException("friend user required");
        }
        if (userId.equals(friendUserId)) {
            throw new IllegalStateException("cannot add self as friend");
        }
        if (friendRepository.areAcceptedFriends(userId, friendUserId)) {
            throw new IllegalStateException("friend already added");
        }
        if (friendRepository.hasIncomingRequest(userId, friendUserId)) {
            throw new IllegalStateException("incoming friend request pending; accept instead");
        }
        FriendRepository.FriendRequestRecord existing = friendRepository.getPendingRequest(userId, friendUserId);
        if (existing == null) {
            friendRepository.savePendingRequest(userId, friendUserId, requestMessage);
            friendRealtimeNotifier.friendRequestCreated(userId, friendUserId);
        } else {
            requestMessage = existing.getRequestMessage();
        }
        return toRequestSummary(friendUserId, "outgoing", "pending", requestMessage);
    }

    @Override
    public FriendSummary acceptFriendRequest(String userId, String friendUserId) {
        if (!friendRepository.hasIncomingRequest(userId, friendUserId)) {
            throw new IllegalStateException("friend request not found");
        }
        friendRepository.acceptFriendPair(userId, friendUserId);
        friendRealtimeNotifier.friendRequestAccepted(userId, friendUserId);
        return toSummary(friendUserId);
    }

    @Override
    public FriendRequestSummary rejectFriendRequest(String userId, String friendUserId) {
        if (!friendRepository.hasIncomingRequest(userId, friendUserId)) {
            throw new IllegalStateException("friend request not found");
        }
        friendRepository.rejectPendingRequest(friendUserId, userId);
        friendRealtimeNotifier.friendRequestRejected(userId, friendUserId);
        return toRequestSummary(friendUserId, "incoming", "rejected", null);
    }

    @Override
    public FriendRequestSummary cancelFriendRequest(String userId, String friendUserId) {
        if (!friendRepository.hasOutgoingRequest(userId, friendUserId)) {
            throw new IllegalStateException("friend request not found");
        }
        friendRepository.cancelPendingRequest(userId, friendUserId);
        friendRealtimeNotifier.friendRequestCancelled(userId, friendUserId);
        return toRequestSummary(friendUserId, "outgoing", "cancelled", null);
    }

    @Override
    public boolean areAcceptedFriends(String userId, String friendUserId) {
        if (userId == null || friendUserId == null) {
            return false;
        }
        return friendRepository.areAcceptedFriends(userId, friendUserId);
    }

    private FriendSummary toSummary(String userId) {
        FriendSummary summary = new FriendSummary();
        summary.setUserId(userId);
        summary.setDisplayName(deriveDisplayName(userId));
        summary.setAvatarSeed(deriveAvatarSeed(userId));
        return summary;
    }

    private FriendRequestSummary toRequestSummary(String userId,
                                                  String direction,
                                                  String status,
                                                  String requestMessage) {
        FriendRequestSummary summary = new FriendRequestSummary();
        summary.setUserId(userId);
        summary.setDisplayName(deriveDisplayName(userId));
        summary.setAvatarSeed(deriveAvatarSeed(userId));
        summary.setDirection(direction);
        summary.setStatus(status);
        summary.setRequestMessage(requestMessage);
        return summary;
    }

    private String deriveDisplayName(String account) {
        String[] parts = account.split("@", 2);
        String local = parts[0];
        return java.util.Arrays.stream(local.split("[._-]"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(account);
    }

    private String deriveAvatarSeed(String account) {
        String[] parts = account.split("@", 2);
        String local = parts[0];
        String seed = java.util.Arrays.stream(local.split("[._-]"))
                .filter(part -> !part.isBlank())
                .limit(2)
                .map(part -> String.valueOf(Character.toUpperCase(part.charAt(0))))
                .reduce("", String::concat);
        return seed.isBlank() ? "IM" : seed;
    }
}
