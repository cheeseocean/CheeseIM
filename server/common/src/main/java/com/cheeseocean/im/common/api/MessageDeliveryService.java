package com.cheeseocean.im.common.api;

import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.dto.DeliveryResult;

public interface MessageDeliveryService {

    /**
     * Accepts a message for delivery. During the rebuild this may represent ingress acceptance
     * rather than a fully completed synchronous delivery.
     */
    DeliveryResult deliver(DeliveryCommand command);

    /**
     * Applies a legacy per-message acknowledgement while receipt-event based flows are introduced.
     */
    DeliveryResult ack(DeliveryAck ack);
}
