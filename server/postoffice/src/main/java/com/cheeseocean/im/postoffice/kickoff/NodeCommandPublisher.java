package com.cheeseocean.im.postoffice.kickoff;

import com.cheeseocean.im.common.api.dto.route.NodeQueueMessage;
import com.cheeseocean.im.common.api.dto.user.KickoffCommand;
import com.cheeseocean.im.common.api.enums.NodeQueueMessageType;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.queue.NodeQueueRedisScripts;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 节点控制命令发布器。
 *
 * <p>复用 postoffice 已有的 per-node Redis LIST，让踢下线这类控制命令可以命中持有连接的网关节点。
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class NodeCommandPublisher {

    private static final Logger log = CommonLoggers.POSTOFFICE;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public NodeCommandPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean publishKickoff(String gatewayNode, KickoffCommand command) {
        if (gatewayNode == null || gatewayNode.isBlank() || command == null) {
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(command);
            String json = objectMapper.writeValueAsString(NodeQueueMessage.of(NodeQueueMessageType.KICKOFF, payload));
            String queueKey = RedisKeys.deliveryNodeQueue(gatewayNode);
            Long accepted = redisTemplate.execute(NodeQueueRedisScripts.ENQUEUE, List.of(queueKey),
                    json, Long.toString(NodeQueueRedisScripts.MAX_QUEUE_SIZE),
                    Long.toString(NodeQueueRedisScripts.QUEUE_TTL_SECONDS));
            return Long.valueOf(1L).equals(accepted);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize kickoff command for nodeId={}, userId={}, sessionId={}",
                    gatewayNode, command.getUserId(), command.getSessionId(), e);
            return false;
        } catch (Exception e) {
            log.error("Failed to enqueue kickoff command to nodeId={}, userId={}, sessionId={}",
                    gatewayNode, command.getUserId(), command.getSessionId(), e);
            return false;
        }
    }
}
