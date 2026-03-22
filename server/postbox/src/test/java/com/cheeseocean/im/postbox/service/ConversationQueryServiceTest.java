package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.enums.SessionStatus;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import com.cheeseocean.im.postbox.permission.ConversationPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationQueryServiceTest {

    @Test
    void listConversationsShouldAggregateLatestMessageAndUnreadCounts() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        when(blockMessageQueryService.findRecentConversationMappings(100))
                .thenReturn(List.of(
                        mapping("single:userA:userB", 8L, "s-2", "userA", 1742382300000L),
                        mapping("single:userA:userB", 7L, "s-1", "userA", 1742382000000L),
                        mapping("group:crew", 6L, "g-2", "userC", 1742375400000L)
                ));
        when(blockMessageQueryService.findSlot("single:userA:userB", 8L))
                .thenReturn(message(8L, "s-2", "single:userA:userB", "userA", "Need the final mockups."));
        when(blockMessageQueryService.findSlot("group:crew", 6L))
                .thenReturn(message(6L, "g-2", "group:crew", "userC", "Stand-up moved to 11:30."));
        when(valueOperations.get(RedisKeys.userUnread("userB", "single:userA:userB"))).thenReturn("1");
        when(valueOperations.get(RedisKeys.userUnread("userB", "group:crew"))).thenReturn("1");

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService);

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
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);

        when(blockMessageQueryService.findRecentConversationMappings(50))
                .thenReturn(List.of(
                        mapping("channel:ops", 9L, "s-1", "userC", 1742382600000L),
                        mapping("single:userA:userB", 8L, "s-2", "userA", 1742382300000L)
                ));
        when(permissionService.check(any()))
                .thenReturn(PermissionCheckResult.deny("DENIED", "no access"))
                .thenReturn(PermissionCheckResult.allow());
        when(blockMessageQueryService.findSlot("single:userA:userB", 8L))
                .thenReturn(message(8L, "s-2", "single:userA:userB", "userA", "Hello"));

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService);

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

    private MessageIdMappingDoc mapping(String conversationId, long seq, String serverMsgId, String senderId, long sendTime) {
        MessageIdMappingDoc mapping = new MessageIdMappingDoc();
        mapping.setConversationId(conversationId);
        mapping.setSeq(seq);
        mapping.setServerMsgId(serverMsgId);
        mapping.setSenderId(senderId);
        mapping.setSendTime(sendTime);
        return mapping;
    }

    private MessageSlot message(long seq, String serverMsgId, String conversationId, String senderId, String content) {
        MessageSlot slot = new MessageSlot();
        slot.setSeq(seq);
        slot.setServerMsgId(serverMsgId);
        slot.setSenderId(senderId);
        slot.setContent(content);
        slot.setSendTime(System.currentTimeMillis());
        return slot;
    }
}
