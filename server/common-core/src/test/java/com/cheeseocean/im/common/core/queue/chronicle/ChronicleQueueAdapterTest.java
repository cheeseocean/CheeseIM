package com.cheeseocean.im.common.core.queue.chronicle;

import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ChronicleQueueAdapterTest {

    @Test
    void shouldDeliverJsonPayloadToSubscriber() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChronicleQueueAdapter adapter = new ChronicleQueueAdapter(objectMapper);
        List<String> received = new CopyOnWriteArrayList<>();

        adapter.subscribe("ingress", "g1", 1, DemoPayload.class, payload -> received.add(payload.value()));
        adapter.send("ingress", "key1", new DemoPayload("ok"));

        awaitAtMost(Duration.ofSeconds(3), () -> assertThat(received).containsExactly("ok"));
    }

    private static void awaitAtMost(Duration duration, AssertionRunnable assertion) throws Exception {
        long deadline = System.nanoTime() + duration.toNanos();
        AssertionError lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(10);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        assertion.run();
    }

    @FunctionalInterface
    interface AssertionRunnable {
        void run() throws Exception;
    }

    record DemoPayload(String value) {
    }
}
