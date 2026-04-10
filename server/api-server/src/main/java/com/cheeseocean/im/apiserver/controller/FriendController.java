package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.business.model.AddFriendRequest;
import com.cheeseocean.im.business.model.FriendRequestActionRequest;
import com.cheeseocean.im.business.service.friend.FriendRelationServiceImpl;
import com.cheeseocean.im.common.api.business.domain.FriendRequest;
import com.cheeseocean.im.common.api.business.domain.Friendship;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/im/friends")
public class FriendController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final FriendRelationServiceImpl friendRelationServiceImpl;

    public FriendController(AccessTokenSessionResolver accessTokenSessionResolver,
                            FriendRelationServiceImpl friendRelationServiceImpl) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.friendRelationServiceImpl = friendRelationServiceImpl;
    }

    @GetMapping
    public List<Friendship> list(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendRelationServiceImpl.listFriends(session.getUserId());
    }

    @GetMapping("/requests/incoming")
    public List<FriendRequest> incoming(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendRelationServiceImpl.listIncomingRequests(session.getUserId());
    }

    @GetMapping("/requests/outgoing")
    public List<FriendRequest> outgoing(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendRelationServiceImpl.listOutgoingRequests(session.getUserId());
    }

    @PostMapping("/requests")
    public FriendRequest add(@RequestHeader("Authorization") String authorization,
                             @RequestBody AddFriendRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendRelationServiceImpl.sendFriendRequest(
                session.getUserId(),
                request == null ? null : request.getFriendUserId(),
                request == null ? null : request.getRequestMessage()
        );
    }

    @PostMapping("/requests/accept")
    public Friendship accept(@RequestHeader("Authorization") String authorization,
                             @RequestBody FriendRequestActionRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendRelationServiceImpl.acceptFriendRequest(
                session.getUserId(),
                request == null ? null : request.getFriendUserId()
        );
    }

    @PostMapping("/requests/reject")
    public FriendRequest reject(@RequestHeader("Authorization") String authorization,
                                @RequestBody FriendRequestActionRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendRelationServiceImpl.rejectFriendRequest(
                session.getUserId(),
                request == null ? null : request.getFriendUserId()
        );
    }

    @PostMapping("/requests/cancel")
    public FriendRequest cancel(@RequestHeader("Authorization") String authorization,
                                @RequestBody FriendRequestActionRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendRelationServiceImpl.cancelFriendRequest(
                session.getUserId(),
                request == null ? null : request.getFriendUserId()
        );
    }
}
