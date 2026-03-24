package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.constants.MessageDisplayConstants;
import com.cheeseocean.im.common.core.enums.ConversationKind;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DirectConversationService {

    private final BlockMessageQueryService blockMessageQueryService;
    private final ConversationStateStore conversationStateStore;
    private final ConversationPresentationResolver conversationPresentationResolver;

    @DubboReference(check = false)
    private FriendRelationService friendRelationService;

    public DirectConversationService(BlockMessageQueryService blockMessageQueryService,
                                     ConversationStateStore conversationStateStore,
                                     ConversationPresentationResolver conversationPresentationResolver) {
        this.blockMessageQueryService = blockMessageQueryService;
        this.conversationStateStore = conversationStateStore;
        this.conversationPresentationResolver = conversationPresentationResolver;
    }

    public ConversationSummaryResponse startConversation(SessionPrincipal session, String friendUserId) {
        if (friendUserId == null || friendUserId.isBlank()) {
            throw new IllegalStateException("friend user required");
        }
        if (session.getUserId().equals(friendUserId)) {
            throw new IllegalStateException("cannot chat with self");
        }
        if (friendRelationService == null || !friendRelationService.areAcceptedFriends(session.getUserId(), friendUserId)) {
            throw new IllegalStateException("friend relationship required");
        }

        String conversationId = ConversationIdUtil.single(session.getUserId(), friendUserId);
        Long latestSeq = loadLatestSeq(conversationId);
        MessageSlot message = latestSeq == null ? null : blockMessageQueryService.findSlot(conversationId, latestSeq);

        ConversationSummaryResponse response = new ConversationSummaryResponse();
        response.setConversationId(conversationId);
        response.setTitle(conversationPresentationResolver.resolveTitle(conversationId, session.getUserId()));
        response.setSubtitle(conversationPresentationResolver.resolveSubtitle(ConversationKind.DIRECT));
        response.setKind(ConversationKind.DIRECT);
        response.setPeerUserId(friendUserId);
        response.setUnreadCount(loadUnreadCount(session.getUserId(), conversationId));
        response.setAccentColor(pickAccentColor(conversationId));
        if (message != null) {
            response.setLastMessagePreview(message.getContent() == null || message.getContent().isBlank()
                    ? MessageDisplayConstants.PREVIEW_ATTACHMENT
                    : message.getContent());
            response.setLastMessageTime(message.getSendTime() == null ? System.currentTimeMillis() : message.getSendTime());
        } else {
            response.setLastMessagePreview(MessageDisplayConstants.CONVERSATION_PREVIEW_EMPTY);
            response.setLastMessageTime(System.currentTimeMillis());
        }
        return response;
    }

    private String pickAccentColor(String conversationId) {
        List<String> colors = List.of("#6ef1c6", "#79d7ff", "#f8b56a", "#ff8f7a", "#99a8ff", "#8ce0b8");
        return colors.get(Math.floorMod(conversationId.hashCode(), colors.size()));
    }

    private int loadUnreadCount(String userId, String conversationId) {
        return conversationStateStore.getUnread(userId, conversationId);
    }

    private Long loadLatestSeq(String conversationId) {
        return conversationStateStore.getConversationMaxSeq(conversationId);
    }
}
