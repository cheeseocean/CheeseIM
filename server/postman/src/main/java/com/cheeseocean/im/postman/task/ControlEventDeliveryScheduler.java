package com.cheeseocean.im.postman.task;

import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * 控制事件 outbox 的可靠在线投递补偿器。
 *
 * <p>扫描仅用于发现候选；每个事件仍先原子 claim，再按已受上限保护的目标用户分片逐一交给跨节点控制通知投递器。
 * 分片事件拥有独立 eventId，失败重试和完成确认不会阻塞同一大群的其他分片。
 * 任何目标未能受理时会有限重试；达到上限后结束在线投递，离线客户端仍通过控制事件游标接口补齐，
 * 避免长期离线用户导致 outbox 热循环。该路径绝不触发离线推送。
 */
@Component
@EnableScheduling
public class ControlEventDeliveryScheduler {

    private static final Logger log = CommonLoggers.POSTMAN;

    private final ConversationControlEventRepository controlEventRepository;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final long claimLeaseMillis;
    private final int maxAttempts;

    @DubboReference(check = false, retries = 0)
    private ControlNotificationDispatcher controlNotificationDispatcher;

    public ControlEventDeliveryScheduler(ConversationControlEventRepository controlEventRepository,
                                         ObjectMapper objectMapper,
                                         @Value("${cheeseim.control-event.delivery.batch-size:100}") int batchSize,
                                         @Value("${cheeseim.control-event.delivery.claim-lease-ms:30000}") long claimLeaseMillis,
                                         @Value("${cheeseim.control-event.delivery.max-attempts:3}") int maxAttempts) {
        this.controlEventRepository = controlEventRepository;
        this.objectMapper = objectMapper;
        this.batchSize = Math.max(1, batchSize);
        this.claimLeaseMillis = Math.max(1_000L, claimLeaseMillis);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${cheeseim.control-event.delivery.poll-interval-ms:1000}")
    public void deliverClaimableEvents() {
        List<ConversationControlEvent> candidates = controlEventRepository.findClaimable(
                batchSize);
        Set<String> claimedEventIds = new HashSet<>();
        for (ConversationControlEvent candidate : candidates) {
            if (candidate == null || candidate.getEventId() == null
                    || !claimedEventIds.add(candidate.getEventId())) {
                continue;
            }
            controlEventRepository.claim(candidate.getEventId(), claimLeaseMillis)
                    .ifPresent(this::deliverClaimedEvent);
        }
    }

    private void deliverClaimedEvent(ConversationControlEvent event) {
        try {
            ServerEnvelope envelope = toEnvelope(event);
            boolean allAccepted = true;
            for (String targetUserId : event.getTargetUserIds()) {
                ControlNotificationReq request = new ControlNotificationReq();
                request.setUserId(targetUserId);
                // 首次直推与 outbox 补偿必须复用同一个幂等键；连接侧的去重键会再包含目标用户/设备，
                // 因此同一控制事件投递给多个目标不会互相抑制。
                request.setDeliveryId(event.getEventId());
                request.setEnvelope(envelope);
                allAccepted &= controlNotificationDispatcher.dispatch(request);
            }
            if ((allAccepted || event.getDeliveryAttempt() >= maxAttempts)
                    && controlEventRepository.markDelivered(event.getEventId(), event.getClaimToken())) {
                log.debug("控制事件结束在线投递: eventId={}, cursor={}, onlineAccepted={}",
                        event.getEventId(), event.getCursor(), allAccepted);
            }
        } catch (Exception exception) {
            log.warn("控制事件投递失败，将在租约到期后重试: eventId={}", event.getEventId(), exception);
        }
    }

    private ServerEnvelope toEnvelope(ConversationControlEvent event) throws Exception {
        CommandType command = switch (event.getType()) {
            case READ_ADVANCED -> CommandType.CHAT_READ;
            case MESSAGE_REVOKED -> CommandType.CHAT_REVOKE;
            // payload 固定为 conversationId/senderId/action/expiresAt，ProtoEnvelopeMapper 会编码为
            // ProtoChatTypingNotify；START 与 STOP 均由 action 与 expiresAt 表达。
            case TYPING_STARTED, TYPING_STOPPED -> CommandType.CHAT_TYPING;
            case DELIVERY_ADVANCED -> CommandType.CHAT_DELIVERY;
        };
        Map<String, Object> payload = event.getPayload() == null || event.getPayload().isBlank()
                ? Map.of()
                : objectMapper.readValue(event.getPayload(), new TypeReference<>() { });
        return ServerEnvelope.of(command, event.getEventId(), payload);
    }
}
