package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.facade.ConversationFacade;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.api.HistoryMessageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话 HTTP 入口。
 */
@RestController
@RequestMapping("/api/im/conversations")
public class ConversationController {

    private final ConversationFacade conversationFacade;

    public ConversationController(ConversationFacade conversationFacade) {
        this.conversationFacade = conversationFacade;
    }

    @GetMapping
    public List<ConversationSummaryResponse> list(@RequestHeader("Authorization") String authorization,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return conversationFacade.listConversations(authorization, limit);
    }

    @GetMapping("/all")
    public List<UserConversation> all(@RequestHeader("Authorization") String authorization) {
        return conversationFacade.getAllConversations(authorization);
    }

    @GetMapping("/{conversationId}")
    public UserConversation getConversation(@RequestHeader("Authorization") String authorization,
                                            @PathVariable String conversationId) {
        return conversationFacade.getConversation(authorization, conversationId);
    }

    @GetMapping("/batch")
    public List<UserConversation> getConversations(@RequestHeader("Authorization") String authorization,
                                                   @RequestParam List<String> conversationIds) {
        return conversationFacade.getConversations(authorization, conversationIds);
    }

    @GetMapping("/ids")
    public List<String> getConversationIds(@RequestHeader("Authorization") String authorization) {
        return conversationFacade.getConversationIds(authorization);
    }

    @GetMapping("/ids/hash")
    public long getConversationIdsHash(@RequestHeader("Authorization") String authorization) {
        return conversationFacade.getConversationIdsHash(authorization);
    }

    @GetMapping("/not-notify")
    public List<String> getNotNotifyConversationIds(@RequestHeader("Authorization") String authorization) {
        return conversationFacade.getNotNotifyConversationIds(authorization);
    }

    @GetMapping("/pinned")
    public List<String> getPinnedConversationIds(@RequestHeader("Authorization") String authorization) {
        return conversationFacade.getPinnedConversationIds(authorization);
    }

    @PutMapping
    public void setConversations(@RequestHeader("Authorization") String authorization,
                                 @RequestBody SetConversationRequest request) {
        conversationFacade.setConversations(authorization, request);
    }

    @GetMapping("/{conversationId}/messages")
    public List<HistoryMessageResponse> messages(@RequestHeader("Authorization") String authorization,
                                                 @PathVariable String conversationId,
                                                 @RequestParam(defaultValue = "50") int limit) {
        return conversationFacade.getConversationMessages(authorization, conversationId, limit);
    }
}
