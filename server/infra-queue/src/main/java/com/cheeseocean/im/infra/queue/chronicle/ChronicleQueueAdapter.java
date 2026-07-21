package com.cheeseocean.im.infra.queue.chronicle;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.protocol.ProtoHistoryEventMapper;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.protocol.ProtoOfflinePushEventMapper;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.Subscription;
import com.cheeseocean.im.infra.queue.config.QueueProperties;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

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
        long started = ImMetrics.startTimer();
        ExcerptAppender appender = appender(topic);
        try (DocumentContext context = appender.writingDocument()) {
            context.wire().write("key").text(key);
            context.wire().write("payload").bytes(message);
            ImMetrics.queuePublish("chronicle", topic, true, started);
        } catch (Exception e) {
            ImMetrics.queuePublish("chronicle", topic, false, started);
            throw new IllegalStateException("Failed to write Chronicle queue message", e);
        }
    }

    @Override
    public void sendBatch(String topic, List<KeyedMessage<byte[]>> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        ExcerptAppender appender = appender(topic);
        long started = ImMetrics.startTimer();
        try {
            for (KeyedMessage<byte[]> message : messages) {
                if (message == null) {
                    continue;
                }
                try (DocumentContext context = appender.writingDocument()) {
                    context.wire().write("key").text(message.key());
                    context.wire().write("payload").bytes(message.payload());
                }
            }
            ImMetrics.queuePublish("chronicle", topic, true, started);
        } catch (Exception e) {
            ImMetrics.queuePublish("chronicle", topic, false, started);
            throw new IllegalStateException("Failed to write Chronicle queue message batch", e);
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
        poller.submit(() -> pollLoop(topic, tailer, payloadType, handler, running));
        return () -> stopPoller(poller, running, topic, group);
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
        poller.submit(() -> pollLoopKeyed(topic, tailer, payloadType, handler, running));
        return () -> stopPoller(poller, running, topic, group);
    }

    @Override
    public <T> Subscription subscribeBatch(String topic, String group, int concurrency, int batchSize,
                                           long batchIntervalMs, Class<T> payloadType,
                                           QueueMessageHandler<List<KeyedMessage<T>>> handler) {
        ChronicleQueue queue = queue(topic);
        AtomicBoolean running = new AtomicBoolean(true);
        ExcerptTailer tailer = queue.createTailer(group);
        ExecutorService poller = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chronicle-queue-batch-poller-" + topic + "-" + group);
            thread.setDaemon(false);
            return thread;
        });
        poller.submit(() -> {
            while (running.get()) {
                long batchStartIndex = tailer.index();
                List<KeyedMessage<T>> batch = new java.util.ArrayList<>(batchSize);
                List<KeyedMessage<byte[]>> rawBatch = new java.util.ArrayList<>(batchSize);
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(batchIntervalMs);
                while (running.get() && batch.size() < batchSize && (batch.isEmpty() || System.nanoTime() < deadline)) {
                    try (DocumentContext context = tailer.readingDocument()) {
                        if (context.isPresent()) {
                            String key = context.wire().read("key").text();
                            byte[] payload = readPayload(context);
                            batch.add(new KeyedMessage<>(key, deserialize(payloadType, payload)));
                            rawBatch.add(new KeyedMessage<>(key, payload));
                        } else if (batch.isEmpty()) {
                            sleepQuietly(queueProperties.getPollIntervalMillis());
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to read Chronicle queue batch", e);
                    }
                }
                if (!batch.isEmpty()) {
                    try {
                        invokeWithRetry(() -> handler.handle(batch), () -> {
                            for (KeyedMessage<byte[]> message : rawBatch) {
                                send(topic + ".DLT", message.key(), message.payload());
                            }
                        });
                    } catch (Exception e) {
                        logger.error("Failed to supervise Chronicle batch; rewinding", e);
                        tailer.moveToIndex(batchStartIndex);
                        sleepQuietly(queueProperties.getPollIntervalMillis());
                    }
                }
            }
        });
        return () -> stopPoller(poller, running, topic, group);
    }

    private void stopPoller(ExecutorService poller,
                            AtomicBoolean running,
                            String topic,
                            String group) {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        poller.shutdown();
        try {
            if (!poller.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Chronicle queue poller stop timed out, topic={}, group={}", topic, group);
                poller.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            poller.shutdownNow();
        }
    }

    private <T> void pollLoopKeyed(String topic,
                                   ExcerptTailer tailer,
                                   Class<T> payloadType,
                                   QueueMessageHandler<KeyedMessage<T>> handler,
                                   AtomicBoolean running) {
        while (running.get()) {
            boolean consumed = false;
            long index = tailer.index();
            try (DocumentContext context = tailer.readingDocument()) {
                if (context.isPresent()) {
                    consumed = true;
                    String key     = context.wire().read("key").text();
                    byte[] payload = readPayload(context);
                    T value = deserialize(payloadType, payload);
                    invokeWithRetry(() -> handler.handle(new KeyedMessage<>(key, value)),
                            () -> send(topic + ".DLT", key, payload));
                }
            } catch (Exception e) {
                logger.error("Failed to consume Chronicle Queue message; rewinding for supervised retry", e);
                tailer.moveToIndex(index);
                sleepQuietly(queueProperties.getPollIntervalMillis());
            }
            if (!consumed) {
                sleepQuietly(queueProperties.getPollIntervalMillis());
            }
        }
    }

    private <T> void pollLoop(String topic,
                              ExcerptTailer tailer,
                              Class<T> payloadType,
                              QueueMessageHandler<T> handler,
                              AtomicBoolean running) {
        while (running.get()) {
            boolean consumed = false;
            long index = tailer.index();
            try (DocumentContext context = tailer.readingDocument()) {
                if (context.isPresent()) {
                    consumed = true;
                    byte[] payload = readPayload(context);
                    T value = deserialize(payloadType, payload);
                    invokeWithRetry(() -> handler.handle(value),
                            () -> send(topic + ".DLT", "", payload));
                }
            } catch (Exception e) {
                logger.error("Failed to consume Chronicle Queue message; rewinding for supervised retry", e);
                tailer.moveToIndex(index);
                sleepQuietly(queueProperties.getPollIntervalMillis());
            }
            if (!consumed) {
                sleepQuietly(queueProperties.getPollIntervalMillis());
            }
        }
    }

    private void invokeWithRetry(ThrowingRunnable handler, ThrowingRunnable deadLetter) throws Exception {
        Exception lastFailure = null;
        int maxAttempts = queueProperties.getConsumer().getMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                handler.run();
                return;
            } catch (Exception e) {
                lastFailure = e;
                logger.warn("Chronicle handler failed, attempt={}/{}", attempt, maxAttempts, e);
                if (attempt < maxAttempts) {
                    sleepQuietly(queueProperties.getConsumer().getRetryIntervalMillis());
                }
            }
        }
        deadLetter.run();
        logger.error("Chronicle handler exhausted retries; message moved to DLT", lastFailure);
    }

    private byte[] readPayload(DocumentContext context) {
        byte[] payload = context.wire().read("payload").bytes();
        return payload == null ? new byte[0] : payload;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(Class<T> payloadType, byte[] payload) throws Exception {
        if (payloadType == byte[].class) {
            return (T) payload;
        }
        if (payloadType == Message.class) {
            return (T) ProtoMessageMapper.fromProto(
                    com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(payload));
        }
        if (payloadType == HistoryEvent.class) {
            return (T) ProtoHistoryEventMapper.fromProto(
                    com.cheeseocean.im.common.api.protocol.proto.ProtoHistoryEvent.parseFrom(payload));
        }
        if (payloadType == OfflinePushEvent.class) {
            return (T) ProtoOfflinePushEventMapper.fromProto(
                    com.cheeseocean.im.common.api.protocol.proto.ProtoOfflinePushEvent.parseFrom(payload));
        }
        return objectMapper.readValue(payload, payloadType);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
