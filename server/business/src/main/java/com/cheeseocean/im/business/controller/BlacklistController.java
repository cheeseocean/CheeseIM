package com.cheeseocean.im.business.controller;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.business.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.business.model.BlacklistActionRequest;
import com.cheeseocean.im.business.service.friend.FriendService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/im/blacklist")
public class BlacklistController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final FriendService friendService;

    public BlacklistController(AccessTokenSessionResolver accessTokenSessionResolver,
                               FriendService friendService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.friendService = friendService;
    }

    /** 查询当前用户的黑名单列表 */
    @GetMapping
    public List<String> list(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return friendService.listBlockedUserIds(session.getUserId());
    }

    /** 将指定用户加入黑名单 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void block(@RequestHeader("Authorization") String authorization,
                      @RequestBody BlacklistActionRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        friendService.blockUser(session.getUserId(), request.getTargetUserId());
    }

    /** 将指定用户移出黑名单 */
    @DeleteMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@RequestHeader("Authorization") String authorization,
                        @PathVariable String targetUserId) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        friendService.unblockUser(session.getUserId(), targetUserId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException e) {
        return Map.of("code", 40001, "message", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalState(IllegalStateException e) {
        return Map.of("code", 40002, "message", e.getMessage());
    }
}
