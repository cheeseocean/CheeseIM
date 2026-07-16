package com.cheeseocean.im.postoffice.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单元测试覆盖 {@link NodeIdentityProvider} 的节点 ID 生成逻辑。
 */
class NodeIdentityProviderTest {

    @Test
    void shouldUseConfiguredNodeIdWhenSet() {
        NodeIdentityProvider provider = new NodeIdentityProvider("my-custom-node", "cluster");
        assertEquals("my-custom-node", provider.getNodeId());
    }

    @Test
    void shouldGenerateUuidWhenConfiguredIdIsNull() {
        NodeIdentityProvider provider = new NodeIdentityProvider(null, "all");
        String nodeId = provider.getNodeId();
        assertNotNull(nodeId);
        assertFalse(nodeId.isBlank());
        // 验证是合法 UUID 格式
        UUID.fromString(nodeId); // throws if not valid UUID
    }

    @Test
    void shouldGenerateUuidWhenConfiguredIdIsBlank() {
        NodeIdentityProvider provider = new NodeIdentityProvider("", "postoffice");
        String nodeId = provider.getNodeId();
        assertNotNull(nodeId);
        assertFalse(nodeId.isBlank());
    }

    @Test
    void shouldReturnConsistentNodeId() {
        NodeIdentityProvider provider = new NodeIdentityProvider("consistent-node", "postoffice,cluster");
        assertEquals(provider.getNodeId(), provider.getNodeId());
        assertEquals(provider.getNodeId(), provider.getNodeId());
    }

    @Test
    void shouldRejectRandomNodeIdInClusterProfile() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new NodeIdentityProvider(" ", "postoffice, cluster"));

        assertTrue(exception.getMessage().contains("CHEESEIM_POSTOFFICE_NODE_ID"));
    }

    @Test
    void shouldRejectRandomNodeIdInClusterRuntimeMode() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new NodeIdentityProvider(" ", "postoffice", " cluster "));

        assertTrue(exception.getMessage().contains("CHEESEIM_POSTOFFICE_NODE_ID"));
    }
}
