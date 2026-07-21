package com.cheeseocean.im.common.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CheeseIM 主链路指标统一入口。
 *
 * <p>使用 Micrometer global registry；没有 MeterRegistry 时所有调用安全退化为空操作。
 * 标签值必须来自有限枚举或固定 topic/provider，禁止传 userId、conversationId 等高基数字段。</p>
 */
public final class ImMetrics {

    private static final Map<String, AtomicLong> GAUGES = new ConcurrentHashMap<>();

    private ImMetrics() {
    }

    public static long startTimer() {
        return System.nanoTime();
    }

    public static void queuePublish(String backend, String topic, boolean success, long startedAtNanos) {
        count("cheeseim.queue.publish", "backend", backend, "topic", topic, "result", result(success));
        time("cheeseim.queue.publish.latency", System.nanoTime() - startedAtNanos,
                "backend", backend, "topic", topic);
    }

    public static void ingressBatch(boolean success, int size, long startedAtNanos) {
        count("cheeseim.ingress.batch", "result", result(success));
        count("cheeseim.ingress.messages", Math.max(0, size), "result", result(success));
        time("cheeseim.ingress.batch.latency", System.nanoTime() - startedAtNanos, "result", result(success));
    }

    public static void nodeQueueDepth(String node, String state, long size) {
        String key = node + ':' + state;
        AtomicLong value = GAUGES.computeIfAbsent("node:" + key, ignored -> {
            AtomicLong holder = new AtomicLong();
            Gauge.builder("cheeseim.node.queue.depth", holder, AtomicLong::get)
                    .tag("node", node).tag("state", state).register(Metrics.globalRegistry);
            return holder;
        });
        value.set(Math.max(0, size));
    }

    public static void nodeRetry(String result) {
        count("cheeseim.node.queue.retry", "result", result);
    }

    public static void route(String result) {
        count("cheeseim.online.route.lookup", "result", result);
    }

    public static void dedup(String status) {
        count("cheeseim.delivery.dedup.claim", "status", status);
    }

    public static void readAdvance(String result) {
        count("cheeseim.read.advance", "result", result);
    }

    public static void writer(String writer, String result) {
        count("cheeseim.writer.operation", "writer", writer, "result", result);
    }

    /**
     * 记录 write-behind 当前积压和最老等待时长。
     *
     * <p>writer/state 必须是代码内固定值。传入最老入队 epoch millis，
     * gauge 在 scrape 时动态计算 age，依赖调用卡死后时长仍会继续增长。</p>
     */
    public static void writerBacklog(String writer, String state, long depth, long oldestQueuedAtMillis) {
        setGauge("writer:depth:" + writer + ':' + state,
                "cheeseim.writer.backlog.depth", Math.max(0, depth),
                "writer", writer, "state", state);
        String key = "writer:oldest:" + writer + ':' + state;
        AtomicLong holder = GAUGES.computeIfAbsent(key, ignored -> {
            AtomicLong gauge = new AtomicLong();
            Gauge.builder("cheeseim.writer.backlog.oldest.age.milliseconds", gauge,
                            timestamp -> timestamp.get() <= 0
                                    ? 0D
                                    : (double) Math.max(0L, System.currentTimeMillis() - timestamp.get()))
                    .tags("writer", writer, "state", state)
                    .register(Metrics.globalRegistry);
            return gauge;
        });
        holder.set(Math.max(0, oldestQueuedAtMillis));
    }

    public static void typing(String result) {
        count("cheeseim.typing.signal", "result", result);
    }

    public static void offlinePush(String provider, String result, long startedAtNanos) {
        count("cheeseim.offline.push", "provider", provider, "result", result);
        time("cheeseim.offline.push.latency", System.nanoTime() - startedAtNanos,
                "provider", provider, "result", result);
    }

    /**
     * 记录低频 DLT 运维操作。topic 只能传 {@code TopicNames} 中的固定值。
     */
    public static void dltOperation(
            String operation,
            String topic,
            boolean success,
            long startedAtNanos) {
        count("cheeseim.dlt.operation",
                "operation", operation,
                "topic", topic,
                "result", result(success));
        time("cheeseim.dlt.operation.latency",
                System.nanoTime() - startedAtNanos,
                "operation", operation,
                "topic", topic,
                "result", result(success));
    }

    /**
     * API 入口限流结果。result 仅允许 allowed/rejected/unavailable。
     */
    public static void apiRateLimit(String result) {
        count("cheeseim.api.rate_limit", "result", result);
    }

    private static String result(boolean success) {
        return success ? "success" : "failure";
    }

    private static void count(String name, String... tags) {
        Counter.builder(name).tags(tags).register(Metrics.globalRegistry).increment();
    }

    private static void count(String name, double amount, String... tags) {
        Counter.builder(name).tags(tags).register(Metrics.globalRegistry).increment(amount);
    }

    private static void time(String name, long nanos, String... tags) {
        Timer.builder(name).tags(tags).publishPercentileHistogram()
                .register(Metrics.globalRegistry).record(Math.max(0, nanos), TimeUnit.NANOSECONDS);
    }

    private static void setGauge(String key, String name, long value, String... tags) {
        AtomicLong holder = GAUGES.computeIfAbsent(key, ignored -> {
            AtomicLong gauge = new AtomicLong();
            Gauge.builder(name, gauge, AtomicLong::get)
                    .tags(tags)
                    .register(Metrics.globalRegistry);
            return gauge;
        });
        holder.set(value);
    }
}
