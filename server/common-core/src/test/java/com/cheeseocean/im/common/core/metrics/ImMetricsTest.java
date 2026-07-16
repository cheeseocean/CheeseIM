package com.cheeseocean.im.common.core.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImMetricsTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
        registry.close();
    }

    @Test
    void shouldRecordBoundedCountersTimersAndGauge() {
        long started = ImMetrics.startTimer();
        ImMetrics.queuePublish("kafka", "ingress", true, started);
        ImMetrics.nodeQueueDepth("node-a", "ready", 7);
        ImMetrics.typing("suppressed");

        assertEquals(1, registry.get("cheeseim.queue.publish")
                .tags("backend", "kafka", "topic", "ingress", "result", "success").counter().count());
        assertNotNull(registry.get("cheeseim.queue.publish.latency").timer());
        assertEquals(7, registry.get("cheeseim.node.queue.depth")
                .tags("node", "node-a", "state", "ready").gauge().value());
        assertEquals(1, registry.get("cheeseim.typing.signal").tag("result", "suppressed").counter().count());
    }
}
