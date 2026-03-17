package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.DeliveryTask;
import com.cheeseocean.im.common.entity.StoredMessage;
import org.springframework.stereotype.Component;

@Component
public class DeliveryStateMachine {

    public DeliveryTask persisted(String deviceId, StoredMessage storedMessage) {
        DeliveryTask task = DeliveryTask.newTask(storedMessage.getServerMsgId(), storedMessage.getReceiverId(), deviceId);
        task.moveTo(DeliveryState.PERSISTED);
        return task;
    }

    public DeliveryTask afterGatewayAttempt(DeliveryTask task, GatewayPushResult result) {
        DeliveryTask next = copyOf(task);
        if (result != null && !result.getDeliveredDeviceIds().isEmpty()) {
            next.moveTo(DeliveryState.ONLINE_CONFIRMED);
            return next;
        }
        next.moveTo(DeliveryState.INBOXED);
        return next;
    }

    public DeliveryTask pushTriggered(DeliveryTask task) {
        DeliveryTask next = copyOf(task);
        next.moveTo(DeliveryState.PUSH_TRIGGERED);
        return next;
    }

    private DeliveryTask copyOf(DeliveryTask task) {
        DeliveryTask copy = DeliveryTask.newTask(task.getServerMsgId(), task.getReceiverId(), task.getDeviceId());
        copy.moveTo(task.getState());
        return copy;
    }
}
