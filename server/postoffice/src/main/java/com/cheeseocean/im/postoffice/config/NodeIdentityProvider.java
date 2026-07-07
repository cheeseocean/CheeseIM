package com.cheeseocean.im.postoffice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 提供当前 postoffice 节点的唯一标识。
 *
 * <p>节点 ID 来源（优先级从高到低）：
 * <ol>
 *   <li>{@code cheeseim.postoffice.node-id} 配置项（生产环境建议设为 hostname 或 k8s pod name）</li>
 *   <li>自动生成 {@link UUID#randomUUID()}（开发/测试默认）</li>
 * </ol>
 *
 * <p>该 ID 会写入 {@code RouteSnapshot.gatewayNode}，供 postman 按节点路由在线投递请求，
 * 解决跨节点在线投递失效问题（ASSESSMENT P0-1）。
 *
 * <p>all-in-one 模式下所有模块共享同一个 JVM，只有一个 postoffice 实例，因此自动 UUID 即可满足需求。
 *
 * @author xxxcrel
 */
@Component
public class NodeIdentityProvider {

    private final String nodeId;

    public NodeIdentityProvider(@Value("${cheeseim.postoffice.node-id:}") String configuredId) {
        this.nodeId = (configuredId != null && !configuredId.isBlank())
                ? configuredId
                : UUID.randomUUID().toString();
    }

    /**
     * 返回当前节点的唯一标识。
     *
     * @return 节点 ID，非空
     */
    public String getNodeId() {
        return nodeId;
    }
}
