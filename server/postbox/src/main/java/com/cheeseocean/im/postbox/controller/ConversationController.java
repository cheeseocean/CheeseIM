package com.cheeseocean.im.postbox.controller;

import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.postbox.service.ConversationQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/im/conversations")
public class ConversationController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final ConversationQueryService conversationQueryService;

    public ConversationController(AccessTokenSessionResolver accessTokenSessionResolver,
                                  ConversationQueryService conversationQueryService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.conversationQueryService = conversationQueryService;
    }

    @GetMapping
    public List<ConversationSummaryResponse> conversations(@RequestHeader("Authorization") String authorization,
                                                           @RequestParam(defaultValue = "20") int limit) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationQueryService.listConversations(session, Math.max(1, Math.min(limit, 100)));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleIllegalState(IllegalStateException e) {
        return Map.of("code", 40301, "message", e.getMessage());
    }
}
