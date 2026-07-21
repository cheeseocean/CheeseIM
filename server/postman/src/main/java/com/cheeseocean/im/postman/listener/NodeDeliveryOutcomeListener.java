package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.enums.NodeDeliveryOutcomeCode;
import com.cheeseocean.im.common.api.enums.OfflinePushTriggerReason;
import com.cheeseocean.im.common.api.event.NodeDeliveryOutcome;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.postman.delivery.OfflinePushCompensationService;
import com.cheeseocean.im.postman.state.NodeDeliveryPendingStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 聚合 postoffice 的节点终态；重复结果按节点覆盖，不使用易重复累加的本地计数器。
 */
@Component
@ConditionalOnBean(NodeDeliveryPendingStore.class)
public class NodeDeliveryOutcomeListener {

    private final ObjectMapper objectMapper;
    private final OfflinePushCompensationService compensationService;

    public NodeDeliveryOutcomeListener(ObjectMapper objectMapper,
                                       OfflinePushCompensationService compensationService) {
        this.objectMapper = objectMapper;
        this.compensationService = compensationService;
    }

    @QueueListener(topic = TopicNames.DELIVERY_OUTCOME, group = "postman-delivery-outcome")
    public void onMessage(byte[] payload) {
        try {
            NodeDeliveryOutcome outcome = objectMapper.readValue(payload, NodeDeliveryOutcome.class);
            NodeDeliveryOutcomeCode code = NodeDeliveryOutcomeCode.fromCode(outcome.getOutcomeCode());
            if (code == null) {
                throw new IllegalArgumentException("Unknown node delivery outcome code");
            }
            compensationService.record(
                    outcome.getDeliveryId(),
                    outcome.getUserId(),
                    outcome.getGatewayNode(),
                    code == NodeDeliveryOutcomeCode.DELIVERED,
                    OfflinePushTriggerReason.NODE_DELIVERY_FAILED);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed node delivery outcome", exception);
        }
    }
}
