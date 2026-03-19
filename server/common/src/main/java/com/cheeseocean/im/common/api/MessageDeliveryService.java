package com.cheeseocean.im.common.api;

import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.dto.DeliveryResult;

public interface MessageDeliveryService {

    DeliveryResult deliver(DeliveryCommand command);

    DeliveryResult ack(DeliveryAck ack);
}
