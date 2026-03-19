package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.enums.SessionStatus;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.permission.ConversationPermissionService;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationQueryServiceTest {

    @Test
    void listConversationsShouldAggregateLatestMessageAndUnreadCounts() {
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        InboxDocument latestSingle = inbox("userB", "s-2", "single:userA:userB", 8L, false, "2026-03-19T11:05:00Z");
        InboxDocument olderSingle = inbox("userB", "s-1", "single:userA:userB", 7L, true, "2026-03-19T11:00:00Z");
        InboxDocument latestGroup = inbox("userB", "g-2", "group:crew", 6L, false, "2026-03-19T10:30:00Z");
        when(inboxRepository.findByUserIdOrderBySequenceDesc("userB"))
                .thenReturn(List.of(latestSingle, olderSingle, latestGroup));

        MessageDocument singleMessage = message("s-2", "single:userA:userB", "userA", "Need the final mockups.");
        singleMessage.setCreatedAt(Instant.parse("2026-03-19T11:05:00Z"));
        MessageDocument groupMessage = message("g-2", "group:crew", "userC", "Stand-up moved to 11:30.");
        groupMessage.setCreatedAt(Instant.parse("2026-03-19T10:30:00Z"));
        when(messageRepository.findAllById(List.of("s-2", "g-2"))).thenReturn(List.of(singleMessage, groupMessage));

        ConversationQueryService service = new ConversationQueryService(inboxRepository, messageRepository, permissionService);

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 20);

        assertEquals(2, conversations.size());
        assertEquals("single:userA:userB", conversations.get(0).getConversationId());
        assertEquals("userA", conversations.get(0).getTitle());
        assertEquals("Need the final mockups.", conversations.get(0).getLastMessagePreview());
        assertEquals(1, conversations.get(0).getUnreadCount());
        assertEquals("DIRECT", conversations.get(0).getKind());

        assertEquals("group:crew", conversations.get(1).getConversationId());
        assertEquals("crew", conversations.get(1).getTitle());
        assertEquals("Group conversation", conversations.get(1).getSubtitle());
        assertEquals(1, conversations.get(1).getUnreadCount());
        assertEquals("GROUP", conversations.get(1).getKind());
    }

    @Test
    void listConversationsShouldSkipDeniedConversationsAndRespectLimit() {
        InboxDocumentRepository inboxRepository = mock(InboxDocumentRepository.class);
        MessageDocumentRepository messageRepository = mock(MessageDocumentRepository.class);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);

        InboxDocument denied = inbox("userB", "s-1", "channel:ops", 9L, false, "2026-03-19T11:10:00Z");
        InboxDocument allowed = inbox("userB", "s-2", "single:userA:userB", 8L, false, "2026-03-19T11:05:00Z");
        when(inboxRepository.findByUserIdOrderBySequenceDesc("userB"))
                .thenReturn(List.of(denied, allowed));
        when(permissionService.check(any()))
                .thenReturn(PermissionCheckResult.deny("DENIED", "no access"))
                .thenReturn(PermissionCheckResult.allow());
        when(messageRepository.findAllById(List.of("s-2"))).thenReturn(List.of(message("s-2", "single:userA:userB", "userA", "Hello")));

        ConversationQueryService service = new ConversationQueryService(inboxRepository, messageRepository, permissionService);

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertIterableEquals(List.of("single:userA:userB"), conversations.stream().map(ConversationSummaryResponse::getConversationId).toList());
    }

    private SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setTenantId("tenant_01");
        session.setSessionId("sess_01");
        session.setDeviceId("dev_01");
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    private InboxDocument inbox(String userId, String serverMsgId, String conversationId, long sequence, boolean read, String createdAt) {
        InboxDocument inbox = new InboxDocument();
        inbox.setUserId(userId);
        inbox.setServerMsgId(serverMsgId);
        inbox.setConversationId(conversationId);
        inbox.setSequence(sequence);
        inbox.setRead(read);
        inbox.setCreatedAt(Instant.parse(createdAt));
        return inbox;
    }

    private MessageDocument message(String serverMsgId, String conversationId, String senderId, String content) {
        MessageDocument document = new MessageDocument();
        document.setServerMsgId(serverMsgId);
        document.setConversationId(conversationId);
        document.setSenderId(senderId);
        document.setContent(content);
        return document;
    }
}
