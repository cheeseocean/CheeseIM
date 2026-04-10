package com.cheeseocean.im.apiserver.facade;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.api.HistoryMessageResponse;
import com.cheeseocean.im.postbox.service.HistoryQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话 HTTP facade，负责处理登录态解析和 HTTP 查询编排。
 */
@Service
public class ConversationFacade {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final ConversationService conversationService;
    private final com.cheeseocean.im.postbox.service.ConversationService conversationCardService;
    private final HistoryQueryService historyQueryService;

    public ConversationFacade(AccessTokenSessionResolver accessTokenSessionResolver,
                              ConversationService conversationService,
                              com.cheeseocean.im.postbox.service.ConversationService conversationCardService,
                              HistoryQueryService historyQueryService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.conversationService = conversationService;
        this.conversationCardService = conversationCardService;
        this.historyQueryService = historyQueryService;
    }

    public List<ConversationSummaryResponse> listConversations(String authorization, int limit) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationCardService.listConversations(session, limit);
    }

    public List<UserConversation> getAllConversations(String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationService.getAllConversations(session.getUserId());
    }

    public UserConversation getConversation(String authorization, String conversationId) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationService.getConversation(session.getUserId(), conversationId);
    }

    public List<UserConversation> getConversations(String authorization, List<String> conversationIds) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationService.getConversations(session.getUserId(), conversationIds);
    }

    public List<String> getConversationIds(String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationService.getConversationIds(session.getUserId());
    }

    public long getConversationIdsHash(String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationService.getConversationIdsHash(session.getUserId());
    }

    public List<String> getNotNotifyConversationIds(String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationService.getNotNotifyConversationIds(session.getUserId());
    }

    public List<String> getPinnedConversationIds(String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return conversationService.getPinnedConversationIds(session.getUserId());
    }

    public void setConversations(String authorization, SetConversationRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        conversationService.setConversations(List.of(session.getUserId()), request);
    }

    public List<HistoryMessageResponse> getConversationMessages(String authorization,
                                                                String conversationId,
                                                                int limit) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        return historyQueryService.getConversationMessages(session, conversationId, limit);
    }
}
