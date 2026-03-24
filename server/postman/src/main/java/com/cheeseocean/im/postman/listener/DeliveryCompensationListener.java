package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.dto.message.DeliveryTask;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.postman.service.DeliveryCompensationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "cheeseim.delivery.compensation.listener",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DeliveryCompensationListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCompensationListener.class);

    private final DeliveryCompensationService deliveryCompensationService;

    public DeliveryCompensationListener(DeliveryCompensationService deliveryCompensationService) {
        this.deliveryCompensationService = deliveryCompensationService;
    }

    @QueueListener(topic = TopicNames.RETRY, group = "postman-delivery-compensation", concurrency = 1)
    public void onCompensation(DeliveryTask task) {
        try {
            deliveryCompensationService.replay(task);
        } catch (Exception e) {
            log.error("Failed to replay delivery compensation payload: {}", task, e);
        }
    }
}
