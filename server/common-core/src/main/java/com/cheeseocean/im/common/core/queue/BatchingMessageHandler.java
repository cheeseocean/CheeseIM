package com.cheeseocean.im.common.core.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BatchingMessageHandler<T> implements QueueMessageHandler<KeyedMessage<T>> {

    private final int                               batchSize;
    private final Consumer<List<T>>                 delegate;
    private final ConcurrentLinkedQueue<KeyedMessage<T>> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean                     flushing   = new AtomicBoolean(false);
    private final ScheduledExecutorService          scheduler;
    private final ExecutorService                   workers;

    public BatchingMessageHandler(int batchSize, long batchIntervalMs, int concurrency, Consumer<List<T>> delegate) {
        this.batchSize = batchSize;
        this.delegate  = delegate;
        this.workers   = Executors.newFixedThreadPool(Math.max(1, concurrency), r -> {
            Thread t = new Thread(r, "batch-queue-worker");
            t.setDaemon(false);
            return t;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-queue-flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, batchIntervalMs, batchIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void handle(KeyedMessage<T> message) {
        buffer.offer(message);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    private void flush() {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            List<KeyedMessage<T>> batch = new ArrayList<>();
            KeyedMessage<T> msg;
            while ((msg = buffer.poll()) != null) {
                batch.add(msg);
            }
            if (batch.isEmpty()) {
                return;
            }
            batch.stream()
                    .collect(Collectors.groupingBy(
                            KeyedMessage::key,
                            Collectors.mapping(KeyedMessage::payload, Collectors.toList())
                    ))
                    .values()
                    .forEach(list -> workers.submit(() -> delegate.accept(list)));
        } finally {
            flushing.set(false);
        }
    }

    public void stop() {
        scheduler.shutdownNow();
        workers.shutdownNow();
    }
}
