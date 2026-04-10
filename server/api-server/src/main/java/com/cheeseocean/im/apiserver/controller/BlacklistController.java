package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.business.model.BlacklistActionRequest;
import com.cheeseocean.im.business.service.friend.FriendRelationServiceImpl;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/im/blacklist")
public class BlacklistController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final FriendRelationServiceImpl friendRelationServiceImpl;

    public BlacklistController(AccessTokenSessionResolver accessTokenSessionResolver,
                               FriendRelationServiceImpl friendRelationServiceImpl) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.friendRelationServiceImpl = friendRelationServiceImpl;
    }

    @GetMapping
    public List<String> list(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendRelationServiceImpl.listBlockedUserIds(session.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void block(@RequestHeader("Authorization") String authorization,
                      @RequestBody BlacklistActionRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        friendRelationServiceImpl.blockUser(session.getUserId(), request.getTargetUserId());
    }

    @DeleteMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@RequestHeader("Authorization") String authorization,
                        @PathVariable String targetUserId) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        friendRelationServiceImpl.unblockUser(session.getUserId(), targetUserId);
    }
}
