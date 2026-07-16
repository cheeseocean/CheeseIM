package com.cheeseocean.im.postoffice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 提供当前 postoffice 节点的唯一标识。
 *
 * <p>节点 ID 来源（优先级从高到低）：
 * <ol>
 *   <li>{@code cheeseim.postoffice.node-id} 配置项（生产环境建议设为 hostname 或 k8s pod name）</li>
 *   <li>非 cluster 环境自动生成 {@link UUID#randomUUID()}（开发/测试默认）</li>
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

    @Autowired
    public NodeIdentityProvider(@Value("${cheeseim.postoffice.node-id:}") String configuredId,
                                @Value("${spring.profiles.active:}") String activeProfiles,
                                @Value("${cheeseim.runtime.mode:}") String runtimeMode) {
        if (configuredId != null && !configuredId.isBlank()) {
            this.nodeId = configuredId;
            return;
        }
        if (containsClusterProfile(activeProfiles) || "cluster".equalsIgnoreCase(trim(runtimeMode))) {
            throw new IllegalStateException(
                    "cluster 模式必须配置稳定的 cheeseim.postoffice.node-id（CHEESEIM_POSTOFFICE_NODE_ID）");
        }
        this.nodeId = UUID.randomUUID().toString();
    }

    /**
     * 纯单元测试兼容入口；生产 Spring 装配固定使用带 profile 的构造器。
     */
    public NodeIdentityProvider(String configuredId) {
        this(configuredId, "", "");
    }

    NodeIdentityProvider(String configuredId, String activeProfiles) {
        this(configuredId, activeProfiles, "");
    }

    /**
     * 返回当前节点的唯一标识。
     *
     * @return 节点 ID，非空
     */
    public String getNodeId() {
        return nodeId;
    }

    private static boolean containsClusterProfile(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        for (String profile : activeProfiles.split(",")) {
            if ("cluster".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
