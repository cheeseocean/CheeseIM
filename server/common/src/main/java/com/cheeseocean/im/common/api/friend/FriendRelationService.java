package com.cheeseocean.im.common.api.friend;

import com.cheeseocean.im.common.model.auth.FriendSummary;
import com.cheeseocean.im.common.model.auth.FriendRequestSummary;

import java.util.List;

public interface FriendRelationService {

    List<FriendSummary> listFriends(String userId);

    List<FriendRequestSummary> listIncomingRequests(String userId);

    FriendRequestSummary sendFriendRequest(String userId, String friendUserId);

    FriendSummary acceptFriendRequest(String userId, String friendUserId);

    boolean areAcceptedFriends(String userId, String friendUserId);
}
