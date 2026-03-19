package com.cheeseocean.im.postbox.controller;

import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.HistoryMessageResponse;
import com.cheeseocean.im.postbox.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.postbox.service.HistoryQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/im/conversations")
public class HistoryController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final HistoryQueryService historyQueryService;

    public HistoryController(AccessTokenSessionResolver accessTokenSessionResolver,
                             HistoryQueryService historyQueryService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.historyQueryService = historyQueryService;
    }

    @GetMapping("/{conversationId}/messages")
    public List<HistoryMessageResponse> history(@RequestHeader("Authorization") String authorization,
                                                @PathVariable String conversationId,
                                                @RequestParam(defaultValue = "20") int limit) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return historyQueryService.getConversationMessages(session, conversationId, Math.max(1, Math.min(limit, 100)));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleIllegalState(IllegalStateException e) {
        return Map.of("code", 40301, "message", e.getMessage());
    }
}
