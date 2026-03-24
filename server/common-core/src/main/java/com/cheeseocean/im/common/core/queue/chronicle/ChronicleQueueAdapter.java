package com.cheeseocean.im.common.core.queue.chronicle;

import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.Subscription;
import com.cheeseocean.im.common.core.queue.config.QueueProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChronicleQueueAdapter implements QueueAdapter {

    private final ObjectMapper objectMapper;
    private final QueueProperties queueProperties;
    private final Map<String, ChronicleQueue> queues = new ConcurrentHashMap<>();

    public ChronicleQueueAdapter(ObjectMapper objectMapper) {
        this(objectMapper, new QueueProperties());
    }

    public ChronicleQueueAdapter(ObjectMapper objectMapper, QueueProperties queueProperties) {
        this.objectMapper = objectMapper;
        this.queueProperties = queueProperties;
    }

    @Override
    public <T> void send(String topic, String key, T message) {
        ChronicleQueue queue = queue(topic);
        ExcerptAppender appender = queue.createAppender();
        try (DocumentContext context = appender.writingDocument()) {
            context.wire().write("key").text(key);
            context.wire().write("payload").text(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write Chronicle queue message", e);
        }
    }

    @Override
    public <T> Subscription subscribe(String topic, String group, int concurrency, Class<T> payloadType, QueueMessageHandler<T> handler) {
        ChronicleQueue queue = queue(topic);
        AtomicBoolean running = new AtomicBoolean(true);
        ExcerptTailer tailer = queue.createTailer(group);
        ExecutorService poller = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chronicle-queue-poller-" + topic + "-" + group);
            thread.setDaemon(true);
            return thread;
        });
        ExecutorService workers = Executors.newFixedThreadPool(Math.max(1, concurrency), runnable -> {
            Thread thread = new Thread(runnable, "chronicle-queue-worker-" + topic + "-" + group);
            thread.setDaemon(true);
            return thread;
        });
        poller.submit(() -> pollLoop(tailer, payloadType, handler, workers, running));
        return () -> {
            running.set(false);
            poller.shutdownNow();
            workers.shutdownNow();
        };
    }

    private ChronicleQueue queue(String topic) {
        return queues.computeIfAbsent(topic, ignored ->
                ChronicleQueue.singleBuilder(queueProperties.topicDir(topic).toFile()).build());
    }

    private <T> void pollLoop(ExcerptTailer tailer,
                              Class<T> payloadType,
                              QueueMessageHandler<T> handler,
                              ExecutorService workers,
                              AtomicBoolean running) {
        while (running.get()) {
            boolean consumed = false;
            try (DocumentContext context = tailer.readingDocument()) {
                if (context.isPresent()) {
                    consumed = true;
                    String payload = context.wire().read("payload").text();
                    workers.submit(() -> invokeHandler(payloadType, handler, payload));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read Chronicle queue message", e);
            }
            if (!consumed) {
                sleepQuietly(queueProperties.getPollIntervalMillis());
            }
        }
    }

    private <T> void invokeHandler(Class<T> payloadType, QueueMessageHandler<T> handler, String payload) {
        try {
            handler.handle(objectMapper.readValue(payload, payloadType));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize Chronicle queue message", e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
