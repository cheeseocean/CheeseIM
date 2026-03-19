package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.DeliveryTask;
import com.cheeseocean.im.common.entity.StoredMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryStateMachineTest {

    @Test
    void persistedShouldAdvanceTaskToPersistedState() {
        DeliveryStateMachine stateMachine = new DeliveryStateMachine();
        StoredMessage stored = new StoredMessage();
        stored.setServerMsgId("s-1");
        stored.setReceiverId("userB");

        DeliveryTask task = stateMachine.persisted("ios-1", stored);

        assertEquals(DeliveryState.PERSISTED, task.getState());
        assertEquals("s-1", task.getServerMsgId());
    }

    @Test
    void onlineDeliveryFailureShouldTransitionToInboxedAndPushPending() {
        DeliveryStateMachine stateMachine = new DeliveryStateMachine();
        DeliveryTask task = DeliveryTask.newTask("s-1", "userB", "ios-1");
        task.moveTo(DeliveryState.PERSISTED);

        GatewayPushResult result = new GatewayPushResult();
        result.setReceiverId("userB");
        result.setRouteFound(true);
        result.setFailedDeviceIds(java.util.List.of("ios-1"));

        DeliveryTask inboxed = stateMachine.afterGatewayAttempt(task, result);
        DeliveryTask pushed = stateMachine.pushTriggered(inboxed);

        assertEquals(DeliveryState.INBOXED, inboxed.getState());
        assertEquals(DeliveryState.PUSH_TRIGGERED, pushed.getState());
    }
}
