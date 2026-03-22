package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.ConversationSummaryResponse;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DirectConversationService {

    private final BlockMessageQueryService blockMessageQueryService;
    private final StringRedisTemplate redisTemplate;

    @DubboReference(check = false)
    private FriendRelationService friendRelationService;

    public DirectConversationService(BlockMessageQueryService blockMessageQueryService,
                                     StringRedisTemplate redisTemplate) {
        this.blockMessageQueryService = blockMessageQueryService;
        this.redisTemplate = redisTemplate;
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
        response.setTitle(friendUserId);
        response.setSubtitle("Direct conversation");
        response.setKind("DIRECT");
        response.setPeerUserId(friendUserId);
        response.setUnreadCount(loadUnreadCount(session.getUserId(), conversationId));
        response.setAccentColor(pickAccentColor(conversationId));
        if (message != null) {
            response.setLastMessagePreview(message.getContent() == null || message.getContent().isBlank() ? "Attachment" : message.getContent());
            response.setLastMessageTime(message.getSendTime() == null ? System.currentTimeMillis() : message.getSendTime());
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

    private int loadUnreadCount(String userId, String conversationId) {
        String raw = redisTemplate.opsForValue().get(RedisKeys.userUnread(userId, conversationId));
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long loadLatestSeq(String conversationId) {
        String raw = redisTemplate.opsForValue().get(RedisKeys.convMaxSeq(conversationId));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
