package com.cheeseocean.im.common.core.queue.chronicle;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.core.queue.config.QueueProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChronicleQueueAdapterTest {

    @Test
    void shouldDeliverJsonPayloadToSubscriber(@TempDir Path tempDir) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChronicleQueueAdapter adapter = new ChronicleQueueAdapter(objectMapper, queueProperties(tempDir));
        List<String> received = new CopyOnWriteArrayList<>();

        adapter.subscribe("ingress", "g1", 1, DemoPayload.class, payload -> received.add(payload.value()));
        adapter.send("ingress", "key1", objectMapper.writeValueAsBytes(new DemoPayload("ok")));

        awaitAtMost(Duration.ofSeconds(3), () -> assertThat(received).containsExactly("ok"));
    }

    @Test
    void shouldReuseAppenderWithinSameThreadAndTopic(@TempDir Path tempDir) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CountingChronicleQueueAdapter adapter = new CountingChronicleQueueAdapter(objectMapper, queueProperties(tempDir));

        adapter.send("ingress", "key1", objectMapper.writeValueAsBytes(new DemoPayload("one")));
        adapter.send("ingress", "key2", objectMapper.writeValueAsBytes(new DemoPayload("two")));

        assertThat(adapter.createAppenderCalls()).isEqualTo(1);
    }

    @Test
    void shouldDeliverProtoMessagePayloadToSubscriber(@TempDir Path tempDir) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChronicleQueueAdapter adapter = new ChronicleQueueAdapter(objectMapper, queueProperties(tempDir));
        List<Message> received = new CopyOnWriteArrayList<>();

        adapter.subscribe("ingress", "g1", 1, Message.class, received::add);
        Message message = new Message();
        message.setClientMsgId("client-1");
        message.setSenderId("u100");
        message.setReceiverId("u200");
        message.setContent("hello".getBytes());
        adapter.send("ingress", "key1", ProtoMessageMapper.toProto(message).toByteArray());

        awaitAtMost(Duration.ofSeconds(3), () -> {
            assertThat(received).hasSize(1);
            assertThat(received.get(0).getSenderId()).isEqualTo("u100");
        });
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

    private static QueueProperties queueProperties(Path tempDir) {
        QueueProperties queueProperties = new QueueProperties();
        queueProperties.setDataDir(tempDir.resolve("queue").toString());
        return queueProperties;
    }

    record DemoPayload(String value) {
    }

    private static final class CountingChronicleQueueAdapter extends ChronicleQueueAdapter {

        private final AtomicInteger createAppenderCalls = new AtomicInteger();

        private CountingChronicleQueueAdapter(ObjectMapper objectMapper, QueueProperties queueProperties) {
            super(objectMapper, queueProperties);
        }

        @Override
        protected ExcerptAppender createAppender(ChronicleQueue queue) {
            createAppenderCalls.incrementAndGet();
            return super.createAppender(queue);
        }

        private int createAppenderCalls() {
            return createAppenderCalls.get();
        }
    }
}
