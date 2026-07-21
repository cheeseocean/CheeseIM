package com.cheeseocean.im.postoffice.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.enums.NodeDeliveryOutcomeCode;
import com.cheeseocean.im.common.api.event.NodeDeliveryOutcome;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 节点在线投递结果生产者。
 *
 * <p>broker ACK 返回后节点消息才能 ACK；发布失败必须让节点 processing claim 保持可恢复。</p>
 */
@Component
public class NodeDeliveryOutcomeProducer {

    private final QueueAdapter queueAdapter;
    private final ObjectMapper objectMapper;

    public NodeDeliveryOutcomeProducer(QueueAdapter queueAdapter, ObjectMapper objectMapper) {
        this.queueAdapter = queueAdapter;
        this.objectMapper = objectMapper;
    }

    public void publish(String gatewayNode,
                        DispatchMessageReq request,
                        NodeDeliveryOutcomeCode code,
                        DispatchMessageResp response,
                        String failureReason) {
        String deliveryId = deliveryId(request);
        if (deliveryId == null || request == null || request.getUserId() == null || code == null) {
            throw new IllegalArgumentException("node delivery outcome identity required");
        }
        NodeDeliveryOutcome outcome = new NodeDeliveryOutcome();
        outcome.setDeliveryId(deliveryId);
        outcome.setUserId(request.getUserId());
        outcome.setGatewayNode(gatewayNode);
        outcome.setOutcomeCode(code);
        outcome.setResults(response == null ? null : response.getResults());
        outcome.setFailureReason(failureReason);
        outcome.setOccurredAt(System.currentTimeMillis());
        try {
            queueAdapter.send(
                    TopicNames.DELIVERY_OUTCOME,
                    deliveryId + ":" + request.getUserId(),
                    objectMapper.writeValueAsBytes(outcome));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize node delivery outcome", exception);
        }
    }

    private String deliveryId(DispatchMessageReq request) {
        if (request == null || request.getPayload() == null) {
            return null;
        }
        if (request.getPayload().getDeliveryId() != null
                && !request.getPayload().getDeliveryId().isBlank()) {
            return request.getPayload().getDeliveryId();
        }
        return request.getPayload().getMsg() == null
                ? null
                : request.getPayload().getMsg().getServerMsgId();
    }
}
