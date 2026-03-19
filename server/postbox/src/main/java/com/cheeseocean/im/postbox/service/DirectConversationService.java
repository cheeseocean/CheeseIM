package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.common.utils.ConversationIds;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DirectConversationService {

    private final InboxDocumentRepository inboxDocumentRepository;
    private final MessageDocumentRepository messageDocumentRepository;

    @DubboReference(check = false)
    private FriendRelationService friendRelationService;

    public DirectConversationService(InboxDocumentRepository inboxDocumentRepository,
                                     MessageDocumentRepository messageDocumentRepository) {
        this.inboxDocumentRepository = inboxDocumentRepository;
        this.messageDocumentRepository = messageDocumentRepository;
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

        String conversationId = ConversationIds.direct(session.getUserId(), friendUserId);
        List<InboxDocument> inboxItems = inboxDocumentRepository
                .findByUserIdAndConversationIdOrderBySequenceDesc(session.getUserId(), conversationId);
        InboxDocument latestInbox = inboxItems.isEmpty() ? null : inboxItems.get(0);
        MessageDocument message = latestInbox == null ? null : messageDocumentRepository.findByServerMsgId(latestInbox.getServerMsgId());

        ConversationSummaryResponse response = new ConversationSummaryResponse();
        response.setConversationId(conversationId);
        response.setTitle(friendUserId);
        response.setSubtitle("Direct conversation");
        response.setKind("DIRECT");
        response.setPeerUserId(friendUserId);
        response.setUnreadCount((int) inboxItems.stream().filter(item -> !item.isRead()).count());
        response.setAccentColor(pickAccentColor(conversationId));
        if (message != null) {
            response.setLastMessagePreview(message.getContent() == null || message.getContent().isBlank() ? "Attachment" : message.getContent());
            response.setLastMessageTime(message.getCreatedAt() == null ? System.currentTimeMillis() : message.getCreatedAt().toEpochMilli());
        } else {
            response.setLastMessagePreview("No messages yet");
            response.setLastMessageTime(System.currentTimeMillis());
        }
        return response;
    }

    private String pickAccentColor(String conversationId) {
        List<String> colors = List.of("#6ef1c6", "#79d7ff", "#f8b56a", "#ff8f7a", "#99a8ff", "#8ce0b8");
        return colors.get(Math.floorMod(conversationId.hashCode(), colors.size()));
    }
}
