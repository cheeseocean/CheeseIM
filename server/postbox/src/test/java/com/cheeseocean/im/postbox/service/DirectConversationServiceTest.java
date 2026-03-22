package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.enums.ConversationKind;
import com.cheeseocean.im.common.core.enums.SessionStatus;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectConversationServiceTest {

    @Test
    void startConversationShouldUseRedisAndBlockHistoryState() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeys.convMaxSeq("c1:userA:userB"))).thenReturn("8");
        when(valueOperations.get(RedisKeys.userUnread("userA", "c1:userA:userB"))).thenReturn("2");

        MessageSlot slot = new MessageSlot();
        slot.setSeq(8L);
        slot.setContent("hello");
        slot.setSendTime(123L);
        when(blockMessageQueryService.findSlot("c1:userA:userB", 8L)).thenReturn(slot);

        DirectConversationService service = new DirectConversationService(blockMessageQueryService, redisTemplate, new ConversationPresentationResolver());
        FriendRelationService friendRelationService = mock(FriendRelationService.class);
        when(friendRelationService.areAcceptedFriends("userA", "userB")).thenReturn(true);
        ReflectionTestUtils.setField(service, "friendRelationService", friendRelationService);

        var response = service.startConversation(session("userA"), "userB");

        assertEquals("c1:userA:userB", response.getConversationId());
        assertEquals("userB", response.getTitle());
        assertEquals("Direct conversation", response.getSubtitle());
        assertEquals(ConversationKind.DIRECT, response.getKind());
        assertEquals(2, response.getUnreadCount());
        assertEquals("hello", response.getLastMessagePreview());
        assertEquals(123L, response.getLastMessageTime());
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setTenantId("tenant_01");
        session.setSessionId("sess_01");
        session.setDeviceId("dev_01");
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }
}
