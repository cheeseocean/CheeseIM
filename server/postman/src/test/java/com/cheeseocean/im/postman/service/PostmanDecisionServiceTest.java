package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.enums.DeliveryState;
import com.cheeseocean.im.postman.state.PushStateStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostmanDecisionServiceTest {

    @Test
    void shouldNotPushWhenAnotherDeviceAlreadyConfirmedReceipt() {
        PushDecisionService service = new PushDecisionService();
        PushDecisionService.PushDecision decision = service.decide(
                new PushStateStore.PushClaim(null, DeliveryState.ONLINE_CONFIRMED, false));

        assertFalse(decision.shouldPush());
    }

    @Test
    void sameServerMsgIdShouldNotCreateDuplicatePushAttempt() {
        PushDecisionService service = new PushDecisionService();
        PushDecisionService.PushDecision decision = service.decide(
                new PushStateStore.PushClaim(null, DeliveryState.INBOXED, true));

        assertFalse(decision.shouldPush());
    }
}
