package com.cheeseocean.im.common.dto;

import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.DeliveryTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryContractTest {

    @Test
    void deliveryTaskShouldStartAtInitState() {
        DeliveryTask task = DeliveryTask.newTask("msg-1", "u2", "ios-1");
        assertEquals(DeliveryState.INIT, task.getState());
    }

    @Test
    void deliveryCommandShouldRequireClientMsgIdConversationIdAndSender() {
        assertThrows(IllegalArgumentException.class, () ->
                DeliveryCommand.builder().senderId("u1").build());
    }
}
