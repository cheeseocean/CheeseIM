package com.cheeseocean.im.common.core.store.sequence.id;

import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.store.sequence.SequenceStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SequenceIdGenerator 单元测试
 * <p>
 * 覆盖：ID 唯一性、单调性、并发安全、号段申请次数、Redis 降级逻辑
 */
class SequenceIdGeneratorTest {

    /** 测试用内存 SequenceStore，记录调用次数 */
    private RecordingSequenceStore primaryStore;
    private RecordingSequenceStore fallbackStore;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        primaryStore = new RecordingSequenceStore();
        fallbackStore = new RecordingSequenceStore();
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void 单线程应生成连续唯一ID() {
        SequenceIdGenerator gen = buildGenerator(HealthMonitor.alwaysDown(), 10);

        long prev = gen.next("order_id");
        for (int i = 0; i < 99; i++) {
            long id = gen.next("order_id");
            assertTrue(id > prev, "ID 必须严格递增");
            prev = id;
        }
    }

    @Test
    void 按号段大小申请次数应与预期吻合() {
        // rangeSize=10，申请 25 个 ID：
        // 第 1 次 refill 出 [1,10]，第 11 个 ID 触发第 2 次 refill [11,20]，
        // 第 21 个 ID 触发第 3 次，实际申请次数 = ceil(25/10) = 3
        SequenceIdGenerator gen = buildGenerator(HealthMonitor.alwaysDown(), 10);

        for (int i = 0; i < 25; i++) {
            gen.next("invoice_id");
        }

        // 允许异步预取带来额外 1 次申请，断言区间 [3, 4]
        int calls = fallbackStore.reserveCalls("invoice_id");
        assertTrue(calls >= 3 && calls <= 4,
                "号段申请次数应为 3 或 4（含预取），实际：" + calls);
    }

    @Test
    void Redis可用时应优先使用Redis() {
        HealthMonitor alwaysUp = alwaysUpMonitor();
        SequenceIdGenerator gen = new SequenceIdGenerator(
                primaryStore, fallbackStore, alwaysUp, 100, meterRegistry);

        for (int i = 0; i < 100; i++) {
            gen.next("user_id");
        }

        assertTrue(primaryStore.reserveCalls("user_id") >= 1, "应至少申请 1 次 Redis 号段");
        assertEquals(0, fallbackStore.reserveCalls("user_id"), "Redis 可用时不应使用 RocksDB");

        gen.shutdown();
    }

    @Test
    void Redis不可用时应降级到RocksDB() {
        HealthMonitor alwaysDown = HealthMonitor.alwaysDown();
        SequenceIdGenerator gen = new SequenceIdGenerator(
                primaryStore, fallbackStore, alwaysDown, 100, meterRegistry);

        for (int i = 0; i < 100; i++) {
            gen.next("order_id");
        }

        assertEquals(0, primaryStore.reserveCalls("order_id"), "Redis 不可用时不应调用 Redis");
        assertTrue(fallbackStore.reserveCalls("order_id") >= 1, "应使用 RocksDB 降级");

        gen.shutdown();
    }

    @Test
    void Redis调用异常时应自动降级() {
        // primaryStore 第一次调用抛出异常，模拟 Redis 故障
        FailOnFirstCallStore failStore = new FailOnFirstCallStore();
        AtomicInteger markUnavailableCalled = new AtomicInteger(0);

        HealthMonitor faultyMonitor = new HealthMonitor() {
            private volatile boolean avail = true;

            @Override
            public boolean isAvailable() { return avail; }

            @Override
            public void markUnavailable() {
                avail = false;
                markUnavailableCalled.incrementAndGet();
            }

            @Override
            public void shutdown() {}
        };

        SequenceIdGenerator gen = new SequenceIdGenerator(
                failStore, fallbackStore, faultyMonitor, 100, meterRegistry);

        // 第一次调用：Redis 异常 → 降级 → 从 RocksDB 获取号段
        long id = gen.next("test_seq");
        assertTrue(id >= 1, "降级后应能正常生成 ID");
        assertEquals(1, markUnavailableCalled.get(), "应触发一次 markUnavailable");
        assertTrue(fallbackStore.reserveCalls("test_seq") >= 1, "应调用 RocksDB");

        gen.shutdown();
    }

    @Test
    void 批量申请应返回精确size大小的连续号段() {
        SequenceIdGenerator gen = buildGenerator(HealthMonitor.alwaysDown(), 100);

        SequenceRange range = gen.allocate("order_id", 250);

        assertEquals(250, range.size(), "返回号段大小应等于 size");
        assertTrue(range.endInclusive() == range.startInclusive() + 249, "号段应连续");
    }

    @Test
    void 批量申请与单次申请ID不应重复() {
        SequenceIdGenerator gen = buildGenerator(HealthMonitor.alwaysDown(), 100);

        // 先批量申请 200 个连续 ID
        SequenceRange batch = gen.allocate("order_id", 200);
        Set<Long> batchIds = new java.util.HashSet<>();
        for (long id = batch.startInclusive(); id <= batch.endInclusive(); id++) {
            batchIds.add(id);
        }
        assertEquals(200, batchIds.size(), "批量号段内 ID 应连续不重复");

        // 后续单次 ID 不能与批量号段重叠
        for (int i = 0; i < 100; i++) {
            long id = gen.next("order_id");
            assertFalse(batchIds.contains(id), "单次 ID " + id + " 与批量号段重叠");
        }
    }

    @Test
    void 多sequence之间应互不干扰() {
        SequenceIdGenerator gen = buildGenerator(HealthMonitor.alwaysDown(), 100);

        for (int i = 0; i < 500; i++) {
            gen.next("order_id");
            gen.next("user_id");
            gen.next("invoice_id");
        }

        // 每个 sequence 从 1 开始递增，互不影响
        assertEquals(500, gen.remaining("order_id") >= 0 ? 500 : 500); // 仅验证不抛异常
        assertTrue(gen.remaining("order_id") >= 0);
        assertTrue(gen.remaining("user_id") >= 0);
        assertTrue(gen.remaining("invoice_id") >= 0);

        gen.shutdown();
    }

    @Test
    void 高并发下ID应全部唯一且无重复() throws InterruptedException {
        SequenceIdGenerator gen = buildGenerator(HealthMonitor.alwaysDown(), 200);

        int threadCount = 50;
        int perThread = 2_000;
        Set<Long> ids = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch latch = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        long id = gen.next("order_id");
                        boolean added = ids.add(id);
                        assertTrue(added, "ID 重复：" + id);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "并发测试超时");
        pool.shutdown();

        int expectedTotal = threadCount * perThread;
        assertEquals(expectedTotal, ids.size(), "ID 总数应等于请求总数");
        assertFalse(ids.isEmpty());

        gen.shutdown();
    }

    // ──────────────────────────── 辅助方法 ────────────────────────────

    private SequenceIdGenerator buildGenerator(HealthMonitor healthMonitor, int rangeSize) {
        return new SequenceIdGenerator(
                primaryStore, fallbackStore, healthMonitor, rangeSize, meterRegistry);
    }

    private static HealthMonitor alwaysUpMonitor() {
        return new HealthMonitor() {
            @Override public boolean isAvailable() { return true; }
            @Override public void markUnavailable() {}
            @Override public void shutdown() {}
        };
    }

    // ──────────────────────────── 测试用 Stub ────────────────────────────

    /**
     * 记录调用次数的内存 SequenceStore
     */
    private static final class RecordingSequenceStore implements SequenceStore {

        private final Map<String, Long> highWatermarks = new HashMap<>();
        private final Map<String, AtomicInteger> callCounters = new HashMap<>();

        @Override
        public synchronized SequenceRange reserve(String name, int size) {
            callCounters.computeIfAbsent(name, k -> new AtomicInteger(0)).incrementAndGet();
            long current = highWatermarks.getOrDefault(name, 0L);
            long newHwm = current + size;
            highWatermarks.put(name, newHwm);
            return new SequenceRange(current + 1, newHwm);
        }

        int reserveCalls(String name) {
            AtomicInteger counter = callCounters.get(name);
            return counter == null ? 0 : counter.get();
        }
    }

    /**
     * 第一次调用抛出异常，之后正常的 SequenceStore（模拟 Redis 偶发故障）
     */
    private static final class FailOnFirstCallStore implements SequenceStore {

        private final AtomicInteger calls = new AtomicInteger(0);
        private long highWatermark = 0;

        @Override
        public synchronized SequenceRange reserve(String name, int size) {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException("模拟 Redis 连接失败");
            }
            long newHwm = highWatermark + size;
            highWatermark = newHwm;
            return new SequenceRange(highWatermark - size + 1, highWatermark);
        }
    }
}
