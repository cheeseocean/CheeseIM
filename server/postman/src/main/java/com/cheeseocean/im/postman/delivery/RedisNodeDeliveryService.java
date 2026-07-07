package com.cheeseocean.im.postman.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.rpc.NodeDeliveryService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis LIST 的节点投递服务实现。
 *
 * <p>将 {@link DispatchMessageReq} 序列化为 JSON，通过 LPUSH 推入目标 postoffice
 * 节点的投递队列（key = {@code delivery:node:{gatewayNode}}），由目标节点的
 * {@code NodeDeliveryPoller} 通过 BRPOP 消费并本地投递。
 *
 * <p>仅在 Redis 可用时装配（{@link ConditionalOnBean @ConditionalOnBean(StringRedisTemplate.class)}）。
 * 当 {@code StringRedisTemplate} 不存在（如无 Redis 的纯单机测试环境）时，
 * 该 bean 不会被创建，调用方应通过降级路径使用直接 Dubbo 调用。
 *
 * <p>设计要点：
 * <ul>
 *   <li>入队失败（Redis 异常 / gatewayNode 为空）返回 {@code false}，让调用方走离线推送兜底</li>
 *   <li>不等待远端投递结果——路由表已证明用户在线，信任远端节点会完成投递</li>
 *   <li>JSON 序列化复用项目已有的 {@link ObjectMapper}，与 {@code RouteSnapshot} 序列化风格一致</li>
 * </ul>
 *
 * @author xxxcrel
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisNodeDeliveryService implements NodeDeliveryService {

    private static final Logger log = CommonLoggers.POSTMAN;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisNodeDeliveryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean deliver(String gatewayNode, DispatchMessageReq req) {
        if (gatewayNode == null || gatewayNode.isBlank()) {
            log.warn("NodeDeliveryService: gatewayNode is null or blank, cannot route delivery for userId={}",
                    req.getUserId());
            return false;
        }
        if (req == null || req.getUserId() == null) {
            log.warn("NodeDeliveryService: invalid dispatch req");
            return false;
        }

        String queueKey = RedisKeys.deliveryNodeQueue(gatewayNode);
        try {
            String json = objectMapper.writeValueAsString(req);
            redisTemplate.opsForList().leftPush(queueKey, json);
            log.debug("Enqueued delivery to nodeId={}, userId={}, queueKey={}", gatewayNode, req.getUserId(), queueKey);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DispatchMessageReq for userId={}, nodeId={}", req.getUserId(), gatewayNode, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to enqueue delivery to nodeId={}, userId={}", gatewayNode, req.getUserId(), e);
            return false;
        }
    }
}
