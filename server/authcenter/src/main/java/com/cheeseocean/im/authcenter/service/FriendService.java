package com.cheeseocean.im.authcenter.service;

import com.cheeseocean.im.authcenter.repository.FriendRepository;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.model.auth.FriendRequestSummary;
import com.cheeseocean.im.common.model.auth.FriendSummary;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@DubboService
public class FriendService implements FriendRelationService {

    private final FriendRepository friendRepository;

    public FriendService(FriendRepository friendRepository) {
        this.friendRepository = friendRepository;
    }

    @Override
    public List<FriendSummary> listFriends(String userId) {
        return friendRepository.listFriendIds(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<FriendRequestSummary> listIncomingRequests(String userId) {
        return friendRepository.listIncomingRequestIds(userId).stream()
                .map(this::toRequestSummary)
                .toList();
    }

    @Override
    public FriendRequestSummary sendFriendRequest(String userId, String friendUserId) {
        if (userId == null || userId.isBlank() || friendUserId == null || friendUserId.isBlank()) {
            throw new IllegalStateException("friend user required");
        }
        if (userId.equals(friendUserId)) {
            throw new IllegalStateException("cannot add self as friend");
        }
        if (friendRepository.areAcceptedFriends(userId, friendUserId)) {
            throw new IllegalStateException("friend already added");
        }
        friendRepository.addRequest(userId, friendUserId);
        return toRequestSummary(userId);
    }

    @Override
    public FriendSummary acceptFriendRequest(String userId, String friendUserId) {
        if (!friendRepository.hasIncomingRequest(userId, friendUserId)) {
            throw new IllegalStateException("friend request not found");
        }
        friendRepository.acceptFriendPair(userId, friendUserId);
        return toSummary(friendUserId);
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

    private FriendRequestSummary toRequestSummary(String userId) {
        FriendRequestSummary summary = new FriendRequestSummary();
        summary.setUserId(userId);
        summary.setDisplayName(deriveDisplayName(userId));
        summary.setAvatarSeed(deriveAvatarSeed(userId));
        summary.setStatus("PENDING");
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
