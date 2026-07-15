package com.cheeseocean.im.apiserver.facade;

import com.cheeseocean.im.apiserver.model.request.HandleFriendRequestRequest;
import com.cheeseocean.im.apiserver.model.request.SendFriendRequestRequest;
import com.cheeseocean.im.apiserver.model.response.FriendRequestResponse;
import com.cheeseocean.im.apiserver.model.response.FriendshipResponse;
import com.cheeseocean.im.common.api.business.domain.FriendRequest;
import com.cheeseocean.im.common.api.business.domain.Friendship;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FriendFacade {

    private final FriendRelationService friendRelationService;

    public FriendFacade(FriendRelationService friendRelationService) {
        this.friendRelationService = friendRelationService;
    }

    public List<FriendshipResponse> listFriends(SessionPrincipal session) {
        return friendRelationService.listFriends(session.getUserId()).stream().map(this::toResponse).toList();
    }

    public List<FriendRequestResponse> listIncomingRequests(SessionPrincipal session) {
        return friendRelationService.listIncomingRequests(session.getUserId()).stream().map(this::toResponse).toList();
    }

    public List<FriendRequestResponse> listOutgoingRequests(SessionPrincipal session) {
        return friendRelationService.listOutgoingRequests(session.getUserId()).stream().map(this::toResponse).toList();
    }

    public FriendRequestResponse sendFriendRequest(SessionPrincipal session, SendFriendRequestRequest request) {
        return toResponse(friendRelationService.sendFriendRequest(
                session.getUserId(),
                request.getFriendUserId(),
                request.getRequestMessage()
        ));
    }

    public FriendshipResponse acceptFriendRequest(SessionPrincipal session, HandleFriendRequestRequest request) {
        return toResponse(friendRelationService.acceptFriendRequest(session.getUserId(), request.getFriendUserId()));
    }

    public FriendRequestResponse rejectFriendRequest(SessionPrincipal session, HandleFriendRequestRequest request) {
        return toResponse(friendRelationService.rejectFriendRequest(session.getUserId(), request.getFriendUserId()));
    }

    public FriendRequestResponse cancelFriendRequest(SessionPrincipal session, HandleFriendRequestRequest request) {
        return toResponse(friendRelationService.cancelFriendRequest(session.getUserId(), request.getFriendUserId()));
    }

    private FriendshipResponse toResponse(Friendship friendship) {
        FriendshipResponse response = new FriendshipResponse();
        response.setId(friendship.getId());
        response.setUserId(friendship.getUserId());
        response.setFriendId(friendship.getFriendId());
        response.setRemark(friendship.getRemark());
        response.setAddSource(friendship.getAddSource());
        response.setOperatorId(friendship.getOperatorId());
        response.setPinned(friendship.isPinned());
        response.setEx(friendship.getEx());
        response.setCreatedAt(friendship.getCreatedAt());
        return response;
    }

    private FriendRequestResponse toResponse(FriendRequest request) {
        FriendRequestResponse response = new FriendRequestResponse();
        response.setFromUserId(request.getFromUserId());
        response.setToUserId(request.getToUserId());
        response.setReqMsg(request.getReqMsg());
        response.setHandleResult(request.getHandleResult().getCode());
        response.setHandleMsg(request.getHandleMsg());
        response.setHandlerUserId(request.getHandlerUserId());
        response.setHandleTime(request.getHandleTime());
        response.setEx(request.getEx());
        response.setCreateTime(request.getCreateTime());
        response.setUpdatedAt(request.getUpdatedAt());
        return response;
    }
}
