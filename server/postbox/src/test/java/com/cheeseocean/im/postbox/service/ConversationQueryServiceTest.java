package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.core.constants.MessageConstants;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.enums.ConversationKind;
import com.cheeseocean.im.common.core.enums.MessagePreviewType;
import com.cheeseocean.im.common.core.enums.SessionStatus;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageSlot;
import com.cheeseocean.im.postbox.permission.ConversationPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationQueryServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                        mapping("c1:userA:userB", 8L, "s-2", "userA", 1742382300000L),
                        mapping("c1:userA:userB", 7L, "s-1", "userA", 1742382000000L),
                        mapping("c2:crew", 6L, "g-2", "userC", 1742375400000L)
                ));
        when(blockMessageQueryService.findSlot("c1:userA:userB", 8L))
                .thenReturn(message(8L, "s-2", "c1:userA:userB", "userA", "Need the final mockups."));
        when(blockMessageQueryService.findSlot("c2:crew", 6L))
                .thenReturn(message(6L, "g-2", "c2:crew", "userC", "Stand-up moved to 11:30."));
        when(valueOperations.get(RedisKeys.userUnread("userB", "c1:userA:userB"))).thenReturn("1");
        when(valueOperations.get(RedisKeys.userUnread("userB", "c2:crew"))).thenReturn("1");

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 20);

        assertEquals(2, conversations.size());
        assertEquals("c1:userA:userB", conversations.get(0).getConversationId());
        assertEquals("userA", conversations.get(0).getTitle());
        assertEquals("Need the final mockups.", conversations.get(0).getLastMessagePreview());
        assertEquals(1, conversations.get(0).getUnreadCount());
        assertEquals(ConversationKind.DIRECT, conversations.get(0).getKind());

        assertEquals("c2:crew", conversations.get(1).getConversationId());
        assertEquals("crew", conversations.get(1).getTitle());
        assertEquals("Group conversation", conversations.get(1).getSubtitle());
        assertEquals(1, conversations.get(1).getUnreadCount());
        assertEquals(ConversationKind.GROUP, conversations.get(1).getKind());
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
                        mapping("c1:userA:userB", 8L, "s-2", "userA", 1742382300000L)
                ));
        when(permissionService.check(any()))
                .thenReturn(PermissionCheckResult.deny("DENIED", "no access"))
                .thenReturn(PermissionCheckResult.allow());
        when(blockMessageQueryService.findSlot("c1:userA:userB", 8L))
                .thenReturn(message(8L, "s-2", "c1:userA:userB", "userA", "Hello"));

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertIterableEquals(List.of("c1:userA:userB"), conversations.stream().map(ConversationSummaryResponse::getConversationId).toList());
    }

    @Test
    void listConversationsShouldPreferConversationLastMessageSummary() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        when(blockMessageQueryService.findRecentConversationMappings(50))
                .thenReturn(List.of(mapping("c1:userA:userB", 8L, "s-2", "userA", 1742382300000L)));
        when(blockMessageQueryService.findSlot("c1:userA:userB", 8L))
                .thenReturn(message(8L, "s-2", "c1:userA:userB", "userA", "history preview"));
        when(valueOperations.get(RedisKeys.convLastMsg("c1:userA:userB")))
                .thenReturn(summaryJson(7L, "userA", "summary preview", null, null, null, 1742382200000L, false));

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertEquals("summary preview", conversations.get(0).getLastMessagePreview());
        assertEquals(1742382200000L, conversations.get(0).getLastMessageTime());
        assertEquals(false, conversations.get(0).isNotification());
    }

    @Test
    void listConversationsShouldHidePreviewWhenMessageDisablesLastMessage() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        when(blockMessageQueryService.findRecentConversationMappings(50))
                .thenReturn(List.of(mapping("c1:userA:userB", 8L, "s-2", "userA", 1742382300000L)));
        MessageSlot slot = message(8L, "s-2", "c1:userA:userB", "userA", "typing...");
        slot.getOptions().setNeedLastMessage(false);
        when(blockMessageQueryService.findSlot("c1:userA:userB", 8L)).thenReturn(slot);
        when(valueOperations.get(RedisKeys.convLastMsg("c1:userA:userB"))).thenReturn(null);

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertNull(conversations.get(0).getLastMessagePreview());
        assertNull(conversations.get(0).getLastMessageTime());
    }

    @Test
    void listConversationsShouldRecognizeNotificationConversationKind() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        when(blockMessageQueryService.findRecentConversationMappings(50))
                .thenReturn(List.of(mapping("c3:userB", 3L, "n-1", "system", 1742382600000L)));
        when(blockMessageQueryService.findSlot("c3:userB", 3L))
                .thenReturn(message(3L, "n-1", "c3:userB", "system", "Policy updated"));

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertEquals(ConversationKind.NOTIFICATION, conversations.get(0).getKind());
        assertEquals("System notifications", conversations.get(0).getTitle());
        assertEquals("Notification conversation", conversations.get(0).getSubtitle());
        assertEquals(false, conversations.get(0).isNotification());
    }

    @Test
    void listConversationsShouldExposeNotificationFlagFromSummary() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        when(blockMessageQueryService.findRecentConversationMappings(50))
                .thenReturn(List.of(mapping("c3:userB", 3L, "n-1", "system", 1742382600000L)));
        when(blockMessageQueryService.findSlot("c3:userB", 3L))
                .thenReturn(message(3L, "n-1", "c3:userB", "system", "Policy updated"));
        when(valueOperations.get(RedisKeys.convLastMsg("c3:userB")))
                .thenReturn(summaryJson(
                        3L,
                        "system",
                        "Policy updated",
                        MessageConstants.CONTENT_TYPE_SYSTEM_NOTIFY,
                        "系统通知",
                        MessagePreviewType.SYSTEM,
                        1742382600000L,
                        true));

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertEquals(true, conversations.get(0).isNotification());
        assertEquals("系统通知", conversations.get(0).getLastMessagePreview());
        assertEquals(MessagePreviewType.SYSTEM, conversations.get(0).getLastMessagePreviewType());
    }

    @Test
    void listConversationsShouldRenderReadablePreviewForRevokeMessage() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        when(blockMessageQueryService.findRecentConversationMappings(50))
                .thenReturn(List.of(mapping("c1:userA:userB", 9L, "s-9", "userA", 1742382600000L)));
        MessageSlot slot = message(9L, "s-9", "c1:userA:userB", "userA", "{\"raw\":true}");
        slot.setContentType(MessageConstants.CONTENT_TYPE_REVOKE_NOTIFY);
        when(blockMessageQueryService.findSlot("c1:userA:userB", 9L)).thenReturn(slot);

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertEquals("你撤回了一条消息", conversations.get(0).getLastMessagePreview());
        assertEquals(MessagePreviewType.REVOKE, conversations.get(0).getLastMessagePreviewType());
    }

    @Test
    void listConversationsShouldRenderReadablePreviewForReadReceipt() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        when(blockMessageQueryService.findRecentConversationMappings(50))
                .thenReturn(List.of(mapping("c1:userA:userB", 9L, "s-9", "userA", 1742382600000L)));
        MessageSlot slot = message(9L, "s-9", "c1:userA:userB", "userA", "{\"read\":true}");
        slot.setContentType(MessageConstants.CONTENT_TYPE_READ_RECEIPT);
        when(blockMessageQueryService.findSlot("c1:userA:userB", 9L)).thenReturn(slot);

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertEquals("[已读回执]", conversations.get(0).getLastMessagePreview());
        assertEquals(MessagePreviewType.READ_RECEIPT, conversations.get(0).getLastMessagePreviewType());
    }

    @Test
    void listConversationsShouldRenderReadablePreviewForNotificationMessages() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        when(blockMessageQueryService.findRecentConversationMappings(50))
                .thenReturn(List.of(
                        mapping("c3:userB", 9L, "s-9", "system", 1742382600000L),
                        mapping("c3:userB", 8L, "s-8", "system", 1742382500000L)
                ));
        MessageSlot displayNotification = message(9L, "s-9", "c3:userB", "system", "{\"notice\":true}");
        displayNotification.setContentType(MessageConstants.CONTENT_TYPE_SYSTEM_NOTIFY);
        displayNotification.getOptions().setNotification(true);
        when(blockMessageQueryService.findSlot("c3:userB", 9L)).thenReturn(displayNotification);

        ConversationQueryService service = new ConversationQueryService(blockMessageQueryService, redisTemplate, permissionService, new MessagePreviewResolver(), new ConversationPresentationResolver());

        List<ConversationSummaryResponse> conversations = service.listConversations(session("userB"), 1);

        assertEquals(1, conversations.size());
        assertEquals("系统通知", conversations.get(0).getLastMessagePreview());
        assertEquals(MessagePreviewType.SYSTEM, conversations.get(0).getLastMessagePreviewType());
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
        slot.setOptions(new com.cheeseocean.im.common.api.dto.message.MessageOptions());
        return slot;
    }

    private static String summaryJson(Long seq,
                                      String senderId,
                                      String content,
                                      Integer contentType,
                                      String previewText,
                                      MessagePreviewType previewType,
                                      Long sendTime,
                                      boolean notification) {
        try {
            ConversationLastMessageSummary summary = new ConversationLastMessageSummary();
            summary.setSeq(seq);
            summary.setSenderId(senderId);
            summary.setContent(content);
            summary.setContentType(contentType);
            summary.setPreviewText(previewText);
            summary.setPreviewType(previewType);
            summary.setSendTime(sendTime);
            summary.setNotification(notification);
            return OBJECT_MAPPER.writeValueAsString(summary);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
