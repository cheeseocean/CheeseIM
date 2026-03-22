package com.cheeseocean.im.postbox.controller;

import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.api.DirectConversationRequest;
import com.cheeseocean.im.postbox.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.postbox.service.DirectConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/im/direct-conversations")
public class DirectConversationController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final DirectConversationService directConversationService;

    public DirectConversationController(AccessTokenSessionResolver accessTokenSessionResolver,
                                        DirectConversationService directConversationService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.directConversationService = directConversationService;
    }

    @PostMapping
    public ConversationSummaryResponse start(@RequestHeader("Authorization") String authorization,
                                             @RequestBody DirectConversationRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return directConversationService.startConversation(session, request == null ? null : request.getFriendUserId());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalState(IllegalStateException e) {
        return Map.of("code", 40003, "message", e.getMessage());
    }
}
