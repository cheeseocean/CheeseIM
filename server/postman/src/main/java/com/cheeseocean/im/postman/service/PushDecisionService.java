package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.enums.DeliveryState;
import com.cheeseocean.im.postman.entity.PushAttempt;
import com.cheeseocean.im.postman.state.PushStateStore;
import org.springframework.stereotype.Service;

@Service
public class PushDecisionService {

    public PushDecision decide(PushStateStore.PushClaim claim) {
        if (claim.deliveryState() == DeliveryState.ONLINE_CONFIRMED || claim.deliveryState() == DeliveryState.READ) {
            return new PushDecision(false, null, "already-confirmed");
        }
        if (claim.duplicateAttempt()) {
            return new PushDecision(false, null, "duplicate-attempt");
        }
        return new PushDecision(claim.claimed(), claim.claimedAttempt(), claim.claimed() ? "created" : "claim-rejected");
    }

    public record PushDecision(boolean shouldPush, PushAttempt attempt, String reason) {
    }
}
