package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.business.domain.User;
import com.cheeseocean.im.common.api.enums.ConversationKind;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.facade.UserServiceFacade;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    @Test
    void listConversationsShouldOnlyReturnCurrentUserConversationsOrderedByUpdatedAt() {
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());
        com.cheeseocean.im.common.api.conversation.ConversationService conversationQueryDubboService = mock(com.cheeseocean.im.common.api.conversation.ConversationService.class);
        when(conversationQueryDubboService.getAllConversations("userB")).thenReturn(List.of(
                conversation("c1:userA:userB", 1, "userA", 200L, 1),
                conversation("c1:userA:userC", 1, "userA", 300L, 5),
                conversation("c2:crew", 2, "crew", 100L, 2)
        ));
        UserServiceFacade userServiceFacade = mock(UserServiceFacade.class);
        User userA = new User();
        userA.setUserId("userA");
        userA.setNickname("Alice");
        when(userServiceFacade.getUserInfo("userA")).thenReturn(userA);

        ConversationService service = new ConversationService(
                permissionService,
                userServiceFacade);
        ReflectionTestUtils.setField(service, "conversationService", conversationQueryDubboService);

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 2);

        assertEquals(2, conversations.size());
        assertIterableEquals(List.of("c1:userA:userC", "c1:userA:userB"),
                conversations.stream().map(ConversationSummaryResponse::getConversationId).toList());
    }

    @Test
    void listConversationsShouldRenderDirectTitleFromTargetUserInfo() {
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());
        com.cheeseocean.im.common.api.conversation.ConversationService conversationQueryDubboService = mock(com.cheeseocean.im.common.api.conversation.ConversationService.class);
        when(conversationQueryDubboService.getAllConversations("userB")).thenReturn(List.of(
                conversation("c1:userA:userB", 1, "userA", 200L, 1)
        ));
        UserServiceFacade userServiceFacade = mock(UserServiceFacade.class);
        User userA = new User();
        userA.setUserId("userA");
        userA.setNickname("Alice");
        when(userServiceFacade.getUserInfo("userA")).thenReturn(userA);

        ConversationService service = new ConversationService(
                permissionService,
                userServiceFacade);
        ReflectionTestUtils.setField(service, "conversationService", conversationQueryDubboService);

        ConversationSummaryResponse response = service.listConversations(session("userB"), 20).get(0);

        assertEquals("Alice", response.getTitle());
        assertEquals("Direct conversation", response.getSubtitle());
        assertEquals(ConversationKind.DIRECT, response.getKind());
        assertNull(response.getLastMessagePreview());
        assertNull(response.getLastMessageTime());
    }

    @Test
    void listConversationsShouldFallbackToTargetIdForGroupTitle() {
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());
        com.cheeseocean.im.common.api.conversation.ConversationService conversationQueryDubboService = mock(com.cheeseocean.im.common.api.conversation.ConversationService.class);
        when(conversationQueryDubboService.getAllConversations("userB")).thenReturn(List.of(
                conversation("c2:crew", 2, "crew", 100L, 2)
        ));

        ConversationService service = new ConversationService(
                permissionService,
                mock(UserServiceFacade.class));
        ReflectionTestUtils.setField(service, "conversationService", conversationQueryDubboService);

        ConversationSummaryResponse response = service.listConversations(session("userB"), 20).get(0);

        assertEquals("crew", response.getTitle());
        assertEquals("Group conversation", response.getSubtitle());
        assertEquals(ConversationKind.GROUP, response.getKind());
        assertNull(response.getLastMessagePreview());
        assertNull(response.getLastMessageTime());
    }

    @Test
    void listConversationsShouldUseFixedNotificationPresentation() {
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());
        com.cheeseocean.im.common.api.conversation.ConversationService conversationQueryDubboService = mock(com.cheeseocean.im.common.api.conversation.ConversationService.class);
        when(conversationQueryDubboService.getAllConversations("userB")).thenReturn(List.of(
                conversation("c3:userB", 3, "system", 260L, 0)
        ));

        ConversationService service = new ConversationService(
                permissionService,
                mock(UserServiceFacade.class));
        ReflectionTestUtils.setField(service, "conversationService", conversationQueryDubboService);

        ConversationSummaryResponse response = service.listConversations(session("userB"), 20).get(0);

        assertEquals("System notifications", response.getTitle());
        assertEquals("Notification conversation", response.getSubtitle());
        assertEquals(ConversationKind.NOTIFICATION, response.getKind());
        assertNull(response.getLastMessagePreview());
        assertNull(response.getLastMessageTime());
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

    private UserConversation conversation(String conversationId,
                                          int conversationType,
                                          String targetId,
                                          long updatedAt,
                                          int unreadCount) {
        UserConversation conversation = new UserConversation();
        conversation.setConversationId(conversationId);
        conversation.setConversationType(conversationType);
        conversation.setTargetId(targetId);
        conversation.setUpdatedAt(updatedAt);
        conversation.setUnreadCount(unreadCount);
        return conversation;
    }
}
