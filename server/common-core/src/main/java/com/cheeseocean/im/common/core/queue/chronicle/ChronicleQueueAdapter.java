package com.cheeseocean.im.common.core.queue.chronicle;

import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.Subscription;
import com.cheeseocean.im.common.core.queue.config.QueueProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChronicleQueueAdapter implements QueueAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ChronicleQueueAdapter.class);

    private final ObjectMapper                              objectMapper;
    private final QueueProperties                           queueProperties;
    private final Map<String, ChronicleQueue>               queues    = new ConcurrentHashMap<>();
    private final Map<String, ThreadLocal<ExcerptAppender>> appenders = new ConcurrentHashMap<>();

    public ChronicleQueueAdapter(ObjectMapper objectMapper) {
        this(objectMapper, new QueueProperties());
    }

    public ChronicleQueueAdapter(ObjectMapper objectMapper, QueueProperties queueProperties) {
        this.objectMapper = objectMapper;
        this.queueProperties = queueProperties;
    }

    @Override
    public void send(String topic, String key, byte[] message) {
        ExcerptAppender appender = appender(topic);
        try (DocumentContext context = appender.writingDocument()) {
            context.wire().write("key").text(key);
            context.wire().write("payload").bytes(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write Chronicle queue message", e);
        }
    }

    @Override
    public <T> Subscription subscribe(String topic, String group, int concurrency, Class<T> payloadType, QueueMessageHandler<T> handler) {
        ChronicleQueue queue   = queue(topic);
        AtomicBoolean  running = new AtomicBoolean(true);
        ExcerptTailer  tailer  = queue.createTailer(group);
        ExecutorService poller = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chronicle-queue-poller-" + topic + "-" + group);
            thread.setDaemon(false);
            return thread;
        });
        ExecutorService workers = Executors.newFixedThreadPool(Math.max(1, concurrency), runnable -> {
            Thread thread = new Thread(runnable, "chronicle-queue-worker-" + topic + "-" + group);
            thread.setDaemon(false);
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

    private ExcerptAppender appender(String topic) {
        return appenders.computeIfAbsent(topic, ignored ->
                ThreadLocal.withInitial(() -> createAppender(queue(topic)))).get();
    }

    protected ExcerptAppender createAppender(ChronicleQueue queue) {
        return queue.createAppender();
    }

    @Override
    public <T> Subscription subscribeKeyed(String topic, String group, int concurrency, Class<T> payloadType, QueueMessageHandler<KeyedMessage<T>> handler) {
        ChronicleQueue    queue   = queue(topic);
        AtomicBoolean     running = new AtomicBoolean(true);
        ExcerptTailer     tailer  = queue.createTailer(group);
        ExecutorService   poller  = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "chronicle-queue-poller-" + topic + "-" + group);
            thread.setDaemon(false);
            return thread;
        });
        ExecutorService workers = Executors.newFixedThreadPool(Math.max(1, concurrency), r -> {
            Thread thread = new Thread(r, "chronicle-queue-worker-" + topic + "-" + group);
            thread.setDaemon(false);
            return thread;
        });
        poller.submit(() -> pollLoopKeyed(tailer, payloadType, handler, workers, running));
        return () -> {
            running.set(false);
            poller.shutdownNow();
            workers.shutdownNow();
        };
    }

    private <T> void pollLoopKeyed(ExcerptTailer tailer,
                                   Class<T> payloadType,
                                   QueueMessageHandler<KeyedMessage<T>> handler,
                                   ExecutorService workers,
                                   AtomicBoolean running) {
        while (running.get()) {
            boolean consumed = false;
            try (DocumentContext context = tailer.readingDocument()) {
                if (context.isPresent()) {
                    consumed = true;
                    String key     = context.wire().read("key").text();
                    String payload = context.wire().read("payload").text();
                    workers.submit(() -> {
                        try {
                            handler.handle(new KeyedMessage<>(key, objectMapper.readValue(payload, payloadType)));
                        } catch (Exception e) {
                            logger.error("Failed to deserialize Chronicle queue message", e);
                            throw new IllegalStateException("Failed to deserialize Chronicle queue message", e);
                        }
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to read Chronicle Queue message", e);
                throw new IllegalStateException("Failed to read Chronicle queue message", e);
            }
            if (!consumed) {
                sleepQuietly(queueProperties.getPollIntervalMillis());
            }
        }
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
                logger.error("Failed to read Chronicle Queue message", e);
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
            logger.error("Failed to read Chronicle Queue message", e);
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
