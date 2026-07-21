package com.cheeseocean.im.common.core.store.sequence.conversation;

import com.cheeseocean.im.common.core.store.config.StateStoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ConversationSeqAllocatorConfigurerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFailFastWhenClusterModeHasNoRedis() {
        ConversationSeqAllocatorConfigurer configurer = new ConversationSeqAllocatorConfigurer();
        ConversationSeqAllocatorProperties properties = new ConversationSeqAllocatorProperties();
        properties.setDeploymentMode(ConversationSeqDeploymentMode.CLUSTER);
        StateStoreProperties stateStoreProperties = new StateStoreProperties();
        stateStoreProperties.setDataDir(tempDir.toString());

        assertThrows(IllegalStateException.class,
                () -> configurer.conversationSeqCacheStore(null, stateStoreProperties, properties));
    }

    @Test
    void shouldUseRocksDbCacheStoreInStandaloneModeWithoutRedis() {
        ConversationSeqAllocatorConfigurer configurer = new ConversationSeqAllocatorConfigurer();
        ConversationSeqAllocatorProperties properties = new ConversationSeqAllocatorProperties();
        properties.setDeploymentMode(ConversationSeqDeploymentMode.STANDALONE);
        StateStoreProperties stateStoreProperties = new StateStoreProperties();
        stateStoreProperties.setDataDir(tempDir.toString());

        ConversationSeqCacheStore store =
                configurer.conversationSeqCacheStore(null, stateStoreProperties, properties);

        assertInstanceOf(
                com.cheeseocean.im.common.core.store.sequence.conversation.rocksdb.RocksDbConversationSeqCacheStore.class,
                store
        );
    }

    @Test
    void shouldUseRedisCacheStoreWhenRedisIsPresent() {
        ConversationSeqAllocatorConfigurer configurer = new ConversationSeqAllocatorConfigurer();
        ConversationSeqAllocatorProperties properties = new ConversationSeqAllocatorProperties();
        StateStoreProperties stateStoreProperties = new StateStoreProperties();
        stateStoreProperties.setDataDir(tempDir.toString());

        ConversationSeqCacheStore store =
                configurer.conversationSeqCacheStore(mock(StringRedisTemplate.class), stateStoreProperties, properties);

        assertInstanceOf(
                com.cheeseocean.im.common.core.store.sequence.conversation.redis.RedisConversationSeqCacheStore.class,
                store
        );
    }
}
