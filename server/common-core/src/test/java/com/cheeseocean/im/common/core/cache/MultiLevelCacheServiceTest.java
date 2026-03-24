package com.cheeseocean.im.common.core.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class MultiLevelCacheServiceTest {

    @Test
    void shouldLoadFromL2AndRefillL1() {
        FakeL2CacheAdapter l2CacheAdapter = new FakeL2CacheAdapter(Map.of("k1", new DemoValue("v1")));
        MultiLevelCacheService service = new MultiLevelCacheService(Caffeine.newBuilder().build(), l2CacheAdapter);

        DemoValue value = service.getOrLoad("k1", DemoValue.class, Duration.ofMinutes(5), () -> {
            throw new AssertionError("loader should not run");
        });

        assertThat(value.value()).isEqualTo("v1");
        assertThat(service.peekL1("k1", DemoValue.class)).contains(value);
    }

    @Test
    void shouldLoadThroughLoaderAndWriteBothLevels() {
        FakeL2CacheAdapter l2CacheAdapter = new FakeL2CacheAdapter(Map.of());
        MultiLevelCacheService service = new MultiLevelCacheService(Caffeine.newBuilder().build(), l2CacheAdapter);

        DemoValue value = service.getOrLoad("k2", DemoValue.class, Duration.ofMinutes(5), () -> new DemoValue("v2"));

        assertThat(value.value()).isEqualTo("v2");
        assertThat(service.peekL1("k2", DemoValue.class)).contains(value);
        assertThat(l2CacheAdapter.get("k2", DemoValue.class)).isEqualTo(value);
    }

    private static final class FakeL2CacheAdapter implements L2CacheAdapter {
        private final Map<String, Object> values = new ConcurrentHashMap<>();

        private FakeL2CacheAdapter(Map<String, Object> initialValues) {
            values.putAll(initialValues);
        }

        @Override
        public <T> T get(String key, Class<T> type) {
            Object value = values.get(key);
            return type.isInstance(value) ? type.cast(value) : null;
        }

        @Override
        public void put(String key, Object value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public void evict(String key) {
            values.remove(key);
        }
    }

    record DemoValue(String value) {
    }
}
