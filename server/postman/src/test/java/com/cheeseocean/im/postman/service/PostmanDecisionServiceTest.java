package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.push.OfflinePushReq;
import com.cheeseocean.im.common.core.enums.DeliveryState;
import com.cheeseocean.im.postman.entity.PushAttempt;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostmanDecisionServiceTest {

    @Test
    void shouldNotPushWhenAnotherDeviceAlreadyConfirmedReceipt() {
        PushDecisionService service = new PushDecisionService();
        OfflinePushReq message = new OfflinePushReq();
        message.setServerMsgId("s-1");
        message.setUserId("userB");

        PushDecisionService.PushDecision decision = service.decide("userB", message, DeliveryState.ONLINE_CONFIRMED, Optional.empty());

        assertFalse(decision.shouldPush());
    }

    @Test
    void sameServerMsgIdShouldNotCreateDuplicatePushAttempt() {
        PushDecisionService service = new PushDecisionService();
        OfflinePushReq message = new OfflinePushReq();
        message.setServerMsgId("s-2");
        message.setUserId("userB");

        PushAttempt existing = new PushAttempt("s-2", "userB");
        PushDecisionService.PushDecision decision = service.decide("userB", message, DeliveryState.INBOXED, Optional.of(existing));

        assertFalse(decision.shouldPush());
    }
}
