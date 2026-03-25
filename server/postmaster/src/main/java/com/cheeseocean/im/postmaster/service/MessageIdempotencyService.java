package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.DeliveryResult;
import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.enums.DeliveryState;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class MessageIdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final MultiLevelCacheService cacheService;

    public MessageIdempotencyService(MultiLevelCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public Optional<DeliveryResult> findExisting(String senderId, String conversationId, String clientMsgId) {
        String raw = cacheService.getOrLoad(
                RedisKeys.postmanIdem(conversationId, clientMsgId),
                String.class,
                TTL,
                () -> null
        );
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\|", -1);
        DeliveryResult result = new DeliveryResult();
        result.setSuccess(true);
        result.setServerMsgId(parts[0]);
        if (parts.length == 2) {
            result.setStatus(parts[1]);
            result.setState(DeliveryState.valueOf(parts[1]));
            return Optional.of(result);
        }
        if (parts.length > 1 && !parts[1].isBlank()) {
            result.setStatus(parts[1]);
        }
        if (parts.length > 2 && !parts[2].isBlank()) {
            result.setConversationSeq(Long.parseLong(parts[2]));
        }
        if (parts.length > 3 && !parts[3].isBlank()) {
            result.setState(DeliveryState.valueOf(parts[3]));
        } else if (result.getStatus() != null && !result.getStatus().isBlank()) {
            result.setState(DeliveryState.valueOf(result.getStatus()));
        }
        return Optional.of(result);
    }

    public void remember(String senderId, String conversationId, String clientMsgId, DeliveryResult result) {
        String status = result.getStatus() == null ? "" : result.getStatus();
        String conversationSeq = result.getConversationSeq() == null ? "" : String.valueOf(result.getConversationSeq());
        String state = result.getState() == null ? "" : result.getState().name();
        String value = String.join("|", result.getServerMsgId(), status, conversationSeq, state);
        cacheService.put(RedisKeys.postmanIdem(conversationId, clientMsgId), value, TTL);
    }
}
