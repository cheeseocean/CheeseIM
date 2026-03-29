package com.cheeseocean.im.business.controller;

import com.cheeseocean.im.common.core.auth.FriendRequestSummary;
import com.cheeseocean.im.common.core.auth.FriendSummary;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.business.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.business.model.AddFriendRequest;
import com.cheeseocean.im.business.model.FriendRequestActionRequest;
import com.cheeseocean.im.business.service.friend.FriendService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/im/friends")
public class FriendController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final FriendService friendService;

    public FriendController(AccessTokenSessionResolver accessTokenSessionResolver,
                            FriendService friendService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.friendService = friendService;
    }

    @GetMapping
    public List<FriendSummary> list(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendService.listFriends(session.getUserId());
    }

    @GetMapping("/requests/incoming")
    public List<FriendRequestSummary> incoming(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendService.listIncomingRequests(session.getUserId());
    }

    @GetMapping("/requests/outgoing")
    public List<FriendRequestSummary> outgoing(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendService.listOutgoingRequests(session.getUserId());
    }

    @PostMapping("/requests")
    public FriendRequestSummary add(@RequestHeader("Authorization") String authorization,
                                    @RequestBody AddFriendRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendService.sendFriendRequest(
                session.getUserId(),
                request == null ? null : request.getFriendUserId(),
                request == null ? null : request.getRequestMessage()
        );
    }

    @PostMapping("/requests/accept")
    public FriendSummary accept(@RequestHeader("Authorization") String authorization,
                                @RequestBody FriendRequestActionRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendService.acceptFriendRequest(
                session.getUserId(),
                request == null ? null : request.getFriendUserId()
        );
    }

    @PostMapping("/requests/reject")
    public FriendRequestSummary reject(@RequestHeader("Authorization") String authorization,
                                       @RequestBody FriendRequestActionRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendService.rejectFriendRequest(
                session.getUserId(),
                request == null ? null : request.getFriendUserId()
        );
    }

    @PostMapping("/requests/cancel")
    public FriendRequestSummary cancel(@RequestHeader("Authorization") String authorization,
                                       @RequestBody FriendRequestActionRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendService.cancelFriendRequest(
                session.getUserId(),
                request == null ? null : request.getFriendUserId()
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalState(IllegalStateException e) {
        return Map.of("code", 40002, "message", e.getMessage());
    }
}
