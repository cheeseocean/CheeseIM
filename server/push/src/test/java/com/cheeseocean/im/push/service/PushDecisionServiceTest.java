package com.cheeseocean.im.push.service;

import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.push.entity.PushAttempt;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushDecisionServiceTest {

    @Test
    void shouldNotPushWhenAnotherDeviceAlreadyConfirmedReceipt() {
        PushDecisionService service = new PushDecisionService();
        MessageProto message = new MessageProto();
        message.setServerMsgId("s-1");
        message.setReceiverId("userB");

        PushDecisionService.PushDecision decision = service.decide("userB", message, DeliveryState.ONLINE_CONFIRMED, Optional.empty());

        assertFalse(decision.shouldPush());
    }

    @Test
    void sameServerMsgIdShouldNotCreateDuplicatePushAttempt() {
        PushDecisionService service = new PushDecisionService();
        MessageProto message = new MessageProto();
        message.setServerMsgId("s-2");
        message.setReceiverId("userB");

        PushAttempt existing = new PushAttempt("s-2", "userB");
        PushDecisionService.PushDecision decision = service.decide("userB", message, DeliveryState.INBOXED, Optional.of(existing));

        assertFalse(decision.shouldPush());
    }
}
