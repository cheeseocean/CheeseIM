package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.facade.ConversationFacade;
import com.cheeseocean.im.apiserver.model.request.BatchGetConversationsRequest;
import com.cheeseocean.im.apiserver.model.request.AckReadSeqRequest;
import com.cheeseocean.im.apiserver.model.request.GetConversationRequest;
import com.cheeseocean.im.apiserver.model.request.ListConversationMessagesRequest;
import com.cheeseocean.im.apiserver.model.request.ListConversationsRequest;
import com.cheeseocean.im.apiserver.model.request.PullMessagesRequest;
import com.cheeseocean.im.apiserver.model.request.SetConversationsRequest;
import com.cheeseocean.im.apiserver.model.response.ConversationIdsHashResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationIdsResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationIncrementalSyncResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationMaxSeqResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationReadSnapshotResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationResponse;
import com.cheeseocean.im.apiserver.model.response.HistoryMessageResponse;
import com.cheeseocean.im.apiserver.model.response.PullMessagesResponse;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public List<ConversationResponse> list(SessionPrincipal session, ListConversationsRequest request) {
        return conversationFacade.listConversations(session, request);
    }

    @GetMapping("/all")
    public List<ConversationResponse> all(SessionPrincipal session) {
        return conversationFacade.getAllConversations(session);
    }

    @GetMapping("/{conversationId}")
    public ConversationResponse getConversation(SessionPrincipal session,
                                                @PathVariable String conversationId) {
        GetConversationRequest request = new GetConversationRequest();
        request.setConversationId(conversationId);
        return conversationFacade.getConversation(session, request);
    }

    @GetMapping("/batch")
    public List<ConversationResponse> getConversations(SessionPrincipal session,
                                                       @RequestParam List<String> conversationIds) {
        BatchGetConversationsRequest request = new BatchGetConversationsRequest();
        request.setConversationIds(conversationIds);
        return conversationFacade.getConversations(session, request);
    }

    @GetMapping("/ids")
    public ConversationIdsResponse getConversationIds(SessionPrincipal session) {
        return conversationFacade.getConversationIds(session);
    }

    @GetMapping("/ids/hash")
    public ConversationIdsHashResponse getConversationIdsHash(SessionPrincipal session) {
        return conversationFacade.getConversationIdsHash(session);
    }

    @GetMapping("/sync/incremental")
    public ConversationIncrementalSyncResponse syncConversations(SessionPrincipal session,
                                                                 @RequestParam(required = false) String versionId,
                                                                 @RequestParam(defaultValue = "0") long version,
                                                                 @RequestParam(defaultValue = "0") long idHash) {
        return conversationFacade.syncConversations(session, versionId, version, idHash);
    }

    @GetMapping("/max-seqs")
    public List<ConversationMaxSeqResponse> getConversationMaxSeqs(SessionPrincipal session,
                                                                   @RequestParam(required = false) List<String> conversationIds) {
        return conversationFacade.getConversationMaxSeqs(session, conversationIds);
    }

    @GetMapping("/read-snapshots")
    public List<ConversationReadSnapshotResponse> getConversationReadSnapshots(SessionPrincipal session,
                                                                               @RequestParam(required = false) List<String> conversationIds) {
        return conversationFacade.getConversationReadSnapshots(session, conversationIds);
    }

    @GetMapping("/not-notify")
    public ConversationIdsResponse getNotNotifyConversationIds(SessionPrincipal session) {
        return conversationFacade.getNotNotifyConversationIds(session);
    }

    @GetMapping("/pinned")
    public ConversationIdsResponse getPinnedConversationIds(SessionPrincipal session) {
        return conversationFacade.getPinnedConversationIds(session);
    }

    @PutMapping
    public void setConversations(SessionPrincipal session,
                                 @RequestBody SetConversationRequest payload) {
        SetConversationsRequest request = new SetConversationsRequest();
        request.setPayload(payload);
        conversationFacade.setConversations(session, request);
    }

    @DeleteMapping("/{conversationId}")
    public void deleteConversation(SessionPrincipal session,
                                   @PathVariable String conversationId) {
        conversationFacade.deleteConversation(session, conversationId);
    }

    @PostMapping("/sync/pull")
    public PullMessagesResponse pullMessages(SessionPrincipal session,
                                             @RequestBody PullMessagesRequest request) {
        return conversationFacade.pullMessages(session, request);
    }

    @PutMapping("/{conversationId}/read-seq")
    public void ackReadSeq(SessionPrincipal session,
                           @PathVariable String conversationId,
                           @RequestBody AckReadSeqRequest request) {
        conversationFacade.ackReadSeq(session, conversationId, request);
    }

    @GetMapping("/{conversationId}/messages")
    public List<HistoryMessageResponse> messages(SessionPrincipal session,
                                                 @PathVariable String conversationId,
                                                 @RequestParam(defaultValue = "50") int limit) {
        ListConversationMessagesRequest request = new ListConversationMessagesRequest();
        request.setConversationId(conversationId);
        request.setLimit(limit);
        return conversationFacade.getConversationMessages(session, request);
    }
}
