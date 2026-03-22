package com.cheeseocean.im.authcenter.controller;

import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.model.AddFriendRequest;
import com.cheeseocean.im.authcenter.model.AcceptFriendRequest;
import com.cheeseocean.im.authcenter.service.FriendService;
import com.cheeseocean.im.common.core.auth.FriendRequestSummary;
import com.cheeseocean.im.common.core.auth.FriendSummary;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
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

    private final AccessTokenService accessTokenService;
    private final FriendService friendService;

    public FriendController(AccessTokenService accessTokenService,
                            FriendService friendService) {
        this.accessTokenService = accessTokenService;
        this.friendService = friendService;
    }

    @GetMapping
    public List<FriendSummary> list(@RequestHeader("Authorization") String authorization) {
        String userId = extractUserId(authorization);
        return friendService.listFriends(userId);
    }

    @GetMapping("/requests")
    public List<FriendRequestSummary> requests(@RequestHeader("Authorization") String authorization) {
        String userId = extractUserId(authorization);
        return friendService.listIncomingRequests(userId);
    }

    @PostMapping
    public FriendRequestSummary add(@RequestHeader("Authorization") String authorization,
                                    @RequestBody AddFriendRequest request) {
        String userId = extractUserId(authorization);
        return friendService.sendFriendRequest(userId, request == null ? null : request.getFriendUserId());
    }

    @PostMapping("/accept")
    public FriendSummary accept(@RequestHeader("Authorization") String authorization,
                                @RequestBody AcceptFriendRequest request) {
        String userId = extractUserId(authorization);
        return friendService.acceptFriendRequest(userId, request == null ? null : request.getFriendUserId());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalState(IllegalStateException e) {
        return Map.of("code", 40002, "message", e.getMessage());
    }

    private String extractUserId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new IllegalStateException("access token missing");
        }
        return accessTokenService.validate(authorization.substring("Bearer ".length()).trim()).getUserId();
    }
}
