package com.cheeseocean.im.postoffice.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 单元测试覆盖 {@link NodeIdentityProvider} 的节点 ID 生成逻辑。
 */
class NodeIdentityProviderTest {

    @Test
    void shouldUseConfiguredNodeIdWhenSet() {
        NodeIdentityProvider provider = new NodeIdentityProvider("my-custom-node");
        assertEquals("my-custom-node", provider.getNodeId());
    }

    @Test
    void shouldGenerateUuidWhenConfiguredIdIsNull() {
        NodeIdentityProvider provider = new NodeIdentityProvider(null);
        String nodeId = provider.getNodeId();
        assertNotNull(nodeId);
        assertFalse(nodeId.isBlank());
        // 验证是合法 UUID 格式
        UUID.fromString(nodeId); // throws if not valid UUID
    }

    @Test
    void shouldGenerateUuidWhenConfiguredIdIsBlank() {
        NodeIdentityProvider provider = new NodeIdentityProvider("");
        String nodeId = provider.getNodeId();
        assertNotNull(nodeId);
        assertFalse(nodeId.isBlank());
    }

    @Test
    void shouldReturnConsistentNodeId() {
        NodeIdentityProvider provider = new NodeIdentityProvider("consistent-node");
        assertEquals(provider.getNodeId(), provider.getNodeId());
        assertEquals(provider.getNodeId(), provider.getNodeId());
    }
}
