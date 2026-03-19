package com.cheeseocean.im.push.service;

import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.push.entity.PushAttempt;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PushDecisionService {

    public PushDecision decide(String userId, MessageProto message, DeliveryState deliveryState, Optional<PushAttempt> existingAttempt) {
        if (deliveryState == DeliveryState.ONLINE_CONFIRMED || deliveryState == DeliveryState.READ) {
            return new PushDecision(false, null, "already-confirmed");
        }
        if (existingAttempt.isPresent()) {
            return new PushDecision(false, existingAttempt.get(), "duplicate-attempt");
        }
        return new PushDecision(true, new PushAttempt(message.getServerMsgId(), userId), "created");
    }

    public record PushDecision(boolean shouldPush, PushAttempt attempt, String reason) {
    }
}
