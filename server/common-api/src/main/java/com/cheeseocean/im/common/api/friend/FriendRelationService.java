package com.cheeseocean.im.common.api.friend;

import com.cheeseocean.im.common.api.dto.user.FriendRequestSummary;
import com.cheeseocean.im.common.api.dto.user.FriendSummary;

import java.util.List;

public interface FriendRelationService {

    List<FriendSummary> listFriends(String userId);

    List<FriendRequestSummary> listIncomingRequests(String userId);

    List<FriendRequestSummary> listOutgoingRequests(String userId);

    FriendRequestSummary sendFriendRequest(String userId, String friendUserId, String requestMessage);

    FriendSummary acceptFriendRequest(String userId, String friendUserId);

    FriendRequestSummary rejectFriendRequest(String userId, String friendUserId);

    FriendRequestSummary cancelFriendRequest(String userId, String friendUserId);

    boolean areAcceptedFriends(String userId, String friendUserId);

    /**
     * Returns true if targetUserId has blocked userId (i.e. userId is on targetUserId's blacklist).
     * Used in sendMessage to decide whether to drop the message before delivery.
     */
    boolean isBlocked(String userId, String targetUserId);

    void blockUser(String userId, String targetUserId);

    void unblockUser(String userId, String targetUserId);

    java.util.List<String> listBlockedUserIds(String userId);
}
