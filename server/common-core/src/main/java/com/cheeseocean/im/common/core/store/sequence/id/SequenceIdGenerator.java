package com.cheeseocean.im.common.core.store.sequence.id;

import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import com.cheeseocean.im.common.core.store.sequence.SequenceStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 高性能业务 Sequence ID 生成器
 *
 * <h3>两级分配架构</h3>
 * <ul>
 *   <li>主路径：Redis {@code INCRBY}，原子申请号段，毫秒级延迟</li>
 *   <li>降级路径：RocksDB 本地持久化 high watermark，Redis 故障时自动切换</li>
 * </ul>
 *
 * <h3>号段预分配</h3>
 * 持久化层仅保存 high watermark，本地内存维护号段区间 [start, end]。
 * 每次 ID 生成仅做原子递增，每 {@code rangeSize} 次请求才向持久化层申请一次号段。
 *
 * <h3>并发模型</h3>
 * <ul>
 *   <li>快速路径：{@link java.util.concurrent.atomic.AtomicLong#incrementAndGet()} 无锁</li>
 *   <li>慢路径：per-sequence {@link java.util.concurrent.locks.ReentrantLock} 保护号段切换</li>
 *   <li>预取：{@code CAS} 保护的后台线程，剩余低于 20% 时异步触发</li>
 * </ul>
 *
 * <h3>崩溃语义</h3>
 * crash 后允许 ID 空洞，但 ID 永不重复、单调递增。
 */
public class SequenceIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SequenceIdGenerator.class);

    /**
     * 预取触发阈值：当前号段剩余比例低于此值时异步申请下一号段
     */
    private static final double PREFETCH_THRESHOLD = 0.20;

    /** 主路径分配器（Redis） */
    private final SequenceStore primaryStore;

    /** 降级路径分配器（RocksDB） */
    private final SequenceStore fallbackStore;

    /** Redis 健康检测与降级控制 */
    private final HealthMonitor healthMonitor;

    /** 默认号段大小，可在 {@link #next(String, int)} 中按需覆盖 */
    private final int defaultRangeSize;

    /**
     * 每个 sequence 的内存状态索引
     * key = sequence 名称，value = 号段状态
     */
    private final ConcurrentHashMap<String, SequenceState> states = new ConcurrentHashMap<>();

    /**
     * 异步预取线程池
     * 守护线程，不阻塞 JVM 退出
     */
    private final ExecutorService prefetchExecutor;

    private final MeterRegistry meterRegistry;

    /**
     * 构造高性能 ID 生成器（Redis 主路径 + RocksDB 降级）
     *
     * @param primaryStore   主路径分配器（Redis）
     * @param fallbackStore  降级路径分配器（RocksDB）
     * @param healthMonitor  Redis 健康监测器
     * @param defaultRangeSize 默认号段大小
     * @param meterRegistry  Micrometer 指标注册表
     */
    public SequenceIdGenerator(
            SequenceStore primaryStore,
            SequenceStore fallbackStore,
            HealthMonitor healthMonitor,
            int defaultRangeSize,
            MeterRegistry meterRegistry) {
        this.primaryStore = Objects.requireNonNull(primaryStore, "primaryStore");
        this.fallbackStore = Objects.requireNonNull(fallbackStore, "fallbackStore");
        this.healthMonitor = Objects.requireNonNull(healthMonitor, "healthMonitor");
        if (defaultRangeSize <= 0) {
            throw new IllegalArgumentException("defaultRangeSize 必须为正数");
        }
        this.defaultRangeSize = defaultRangeSize;
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.prefetchExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "seqgen-prefetch");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 为指定业务序列生成下一个唯一递增 ID（使用默认号段大小）
     * <p>
     * 多线程并发调用安全，快速路径为无锁操作。
     *
     * @param name 序列名称，例如 {@code "order_id"}、{@code "user_id"}
     * @return 唯一递增 ID
     */
    public long next(String name) {
        Objects.requireNonNull(name, "name");
        SequenceState state = states.computeIfAbsent(name, k -> new SequenceState());
        return nextFromState(name, state, defaultRangeSize);
    }

    /**
     * 为指定业务序列生成下一个唯一递增 ID（自定义号段大小）
     *
     * @param name      序列名称
     * @param rangeSize 单次向持久化层申请的号段大小
     * @return 唯一递增 ID
     */
    public long next(String name, int rangeSize) {
        Objects.requireNonNull(name, "name");
        if (rangeSize <= 0) throw new IllegalArgumentException("rangeSize 必须为正数");
        SequenceState state = states.computeIfAbsent(name, k -> new SequenceState());
        return nextFromState(name, state, rangeSize);
    }

    /**
     * 为指定业务序列批量申请 {@code size} 个连续唯一递增 ID
     * <p>
     * 直接向持久化层（Redis / RocksDB）申请 {@code size} 大小的号段，
     * 返回一个连续区间 {@code [start, start + size - 1]}，单次持久化层往返。
     * <p>
     * 与 {@link #next(String)} 的区别：此方法绕过本地号段缓存，
     * 每次调用都产生一次持久化层写入；适用于批量导入等低频大量预分配场景。
     *
     * @param name 序列名称，例如 {@code "order_id"}
     * @param size 需要申请的 ID 总数，必须为正数
     * @return 覆盖恰好 size 个连续 ID 的号段
     */
    public SequenceRange allocate(String name, int size) {
        Objects.requireNonNull(name, "name");
        if (size <= 0) throw new IllegalArgumentException("size 必须为正数");
        SequenceRange range = allocateRange(name, size);
        countRefill(name);
        meterRegistry.counter("sequence_allocations_total",
                Tags.of("sequence", name)).increment(size);
        return range;
    }

    /**
     * 返回指定 sequence 当前号段的剩余 ID 数量（用于监控/运维）
     *
     * @param name 序列名称
     * @return 剩余数量，-1 表示尚未初始化
     */
    public long remaining(String name) {
        SequenceState state = states.get(name);
        if (state == null || state.current == null) {
            return -1;
        }
        return state.current.remaining();
    }

    /**
     * 停止后台预取线程和 Redis 健康检测线程
     */
    public void shutdown() {
        prefetchExecutor.shutdownNow();
        healthMonitor.shutdown();
    }

    // ──────────────────────────── 内部核心逻辑 ────────────────────────────

    /**
     * ID 生成主流程
     * <ol>
     *   <li>快速路径：从当前号段原子递增取 ID（无锁）</li>
     *   <li>慢路径：号段耗尽或首次使用时，加锁 refill</li>
     * </ol>
     */
    private long nextFromState(String name, SequenceState state, int rangeSize) {
        // ── 快速路径：当前号段仍有余量 ──
        SequenceSegment current = state.current;
        if (current != null) {
            long id = current.next();
            if (id <= current.end) {
                countAllocation(name);
                // 剩余不足时异步预取（不阻塞当前请求）
                maybeAsyncPrefetch(name, state, current, rangeSize);
                return id;
            }
        }

        // ── 慢路径：号段耗尽，需要切换或申请 ──
        // 将已耗尽的号段传入，用于 double-check 时判断是否有其他线程已完成 refill
        return refill(name, state, current, rangeSize);
    }

    /**
     * 慢路径：加 per-sequence 锁，切换预取号段或同步申请新号段
     * <p>
     * {@code exhausted} 为调用方已确认耗尽的号段，用于 double-check 时与 {@code state.current}
     * 做引用比较：若不同，说明其他线程已完成 refill，直接从新号段取 ID。
     * 此设计避免了对已耗尽号段再次调用 {@code next()} 造成游标无谓递增。
     */
    private long refill(String name, SequenceState state, SequenceSegment exhausted, int rangeSize) {
        state.lock.lock();
        try {
            // double-check：若锁等待期间其他线程已完成 refill，直接从新号段取 ID
            SequenceSegment nowCurrent = state.current;
            if (nowCurrent != null && nowCurrent != exhausted) {
                long id = nowCurrent.next();
                if (id <= nowCurrent.end) {
                    countAllocation(name);
                    return id;
                }
                // 新号段也已被并发线程耗尽，继续向下处理
            }

            // 优先消费预取号段，实现零延迟切换
            if (state.next != null) {
                state.current = state.next;
                state.next = null;
                // 不在此处重置 prefetching，由预取任务的 finally 块统一负责重置，
                // 避免与正在运行的预取任务产生竞争

                long id = state.current.next();
                if (id <= state.current.end) {
                    countAllocation(name);
                    maybeAsyncPrefetch(name, state, state.current, rangeSize);
                    return id;
                }
            }

            // 同步申请新号段（频率 = QPS / rangeSize，正常极低）
            SequenceRange range = allocateRange(name, rangeSize);
            state.current = new SequenceSegment(range.startInclusive(), range.endInclusive());
            countRefill(name);
            log.debug("[SeqIdGen] sequence={} 同步申请新号段 [{}, {}]",
                    name, range.startInclusive(), range.endInclusive());

            // 新段首个 ID，一定成功
            long id = state.current.next();
            countAllocation(name);
            return id;

        } finally {
            state.lock.unlock();
        }
    }

    /**
     * 异步预取：当剩余低于 20% 时，后台申请下一号段存入 {@code state.next}
     * <p>
     * 使用 CAS 保证每个 sequence 同时只有一个预取任务在运行。
     * <p>
     * <b>关键校验</b>：预取到的 range 必须严格在 {@code state.current.end} 之后。
     * 若在预取进行中发生了同步 refill（慢路径），同步 refill 会先拿到较低的号段，
     * 预取拿到的号段反而更低，此时丢弃预取结果（产生 ID 空洞，属预期行为），
     * 防止 {@code state.next} 存入比 {@code state.current} 更早的号段导致 ID 倒退。
     */
    private void maybeAsyncPrefetch(String name, SequenceState state,
                                    SequenceSegment current, int rangeSize) {
        // 快速判断：未达到预取阈值则直接返回
        if (!current.needPrefetch(PREFETCH_THRESHOLD)) {
            return;
        }
        // CAS 防重：仅允许一个预取任务
        if (!state.prefetching.compareAndSet(false, true)) {
            return;
        }
        prefetchExecutor.submit(() -> {
            try {
                SequenceRange range = allocateRange(name, rangeSize);
                state.lock.lock();
                try {
                    // 验证预取 range 严格在当前号段之后，防止因并发 refill 导致的号段倒退
                    long currentEnd = state.current != null ? state.current.end : 0L;
                    if (state.next == null && range.startInclusive() > currentEnd) {
                        state.next = new SequenceSegment(range.startInclusive(), range.endInclusive());
                        countRefill(name);
                        log.debug("[SeqIdGen] sequence={} 预取号段 [{}, {}]",
                                name, range.startInclusive(), range.endInclusive());
                    }
                    // 否则丢弃此次预取，防止 ID 倒退（预取 range 对应的 ID 成为空洞）
                } finally {
                    state.lock.unlock();
                }
            } catch (Exception e) {
                log.warn("[SeqIdGen] sequence={} 预取号段失败，将在下次 refill 重试: {}",
                        name, e.getMessage());
            } finally {
                // 无论成功或失败，始终重置标志，允许后续预取
                state.prefetching.set(false);
            }
        });
    }

    /**
     * 按优先级选择分配器申请号段
     * <p>
     * 主路径（Redis）可用时优先使用；失败后立即降级并通知健康监测器；
     * RocksDB 作为最终兜底。
     */
    private SequenceRange allocateRange(String name, int rangeSize) {
        if (healthMonitor.isAvailable()) {
            try {
                return primaryStore.reserve(name, rangeSize);
            } catch (Exception e) {
                log.warn("[SeqIdGen] Redis 号段申请失败，降级至 RocksDB: {}", e.getMessage());
                healthMonitor.markUnavailable();
                countFailover();
            }
        }
        // RocksDB 降级路径
        return fallbackStore.reserve(name, rangeSize);
    }

    // ──────────────────────────── Micrometer 指标 ────────────────────────────

    /**
     * 计数 ID 分配次数（sequence 维度）
     */
    private void countAllocation(String name) {
        meterRegistry.counter("sequence_allocations_total",
                Tags.of("sequence", name)).increment();
    }

    /**
     * 计数号段补充次数（sequence 维度）
     */
    private void countRefill(String name) {
        meterRegistry.counter("sequence_range_refill_total",
                Tags.of("sequence", name)).increment();
    }

    /**
     * 计数 Redis 降级次数（全局）
     */
    private void countFailover() {
        meterRegistry.counter("redis_failover_count").increment();
    }
}
