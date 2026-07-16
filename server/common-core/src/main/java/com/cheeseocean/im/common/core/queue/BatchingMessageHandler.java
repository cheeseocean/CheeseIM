package com.cheeseocean.im.common.core.queue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 有界、带背压的批量消息处理器。
 *
 * <p>{@link #handle(KeyedMessage)} 只有在消息所属批次处理成功后才返回。队列消费者因此不会在业务处理前
 * 提交 offset；缓冲区满时调用线程会阻塞形成背压，而不是把消息继续堆积到无界 JVM 内存。</p>
 */
public class BatchingMessageHandler<T> implements QueueMessageHandler<KeyedMessage<T>> {

    private final int batchSize;
    private final long batchIntervalMs;
    private final Consumer<List<T>> delegate;
    private final ArrayBlockingQueue<PendingMessage<T>> buffer;
    private final ExecutorService coordinator;
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public BatchingMessageHandler(int batchSize, long batchIntervalMs, int concurrency, Consumer<List<T>> delegate) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        if (batchIntervalMs <= 0) {
            throw new IllegalArgumentException("batchIntervalMs must be greater than zero");
        }
        int workerCount = Math.max(1, concurrency);
        this.batchSize = batchSize;
        this.batchIntervalMs = batchIntervalMs;
        this.delegate = delegate;
        this.buffer = new ArrayBlockingQueue<>(Math.max(batchSize, batchSize * workerCount * 2));
        this.workers = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, "batch-queue-worker");
            thread.setDaemon(false);
            return thread;
        });
        this.coordinator = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "batch-queue-coordinator");
            thread.setDaemon(false);
            return thread;
        });
        coordinator.execute(this::runCoordinator);
    }

    @Override
    public void handle(KeyedMessage<T> message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (!running.get()) {
            throw new IllegalStateException("Batching message handler has stopped");
        }

        PendingMessage<T> pending = new PendingMessage<>(message, new CompletableFuture<>());
        try {
            buffer.put(pending);
            pending.completion().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for queue batch", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Queue batch processing failed", cause);
        }
    }

    private void runCoordinator() {
        while (running.get() || !buffer.isEmpty()) {
            try {
                PendingMessage<T> first = buffer.poll(100, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                List<PendingMessage<T>> batch = new ArrayList<>(batchSize);
                batch.add(first);
                long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(batchIntervalMs);
                while (batch.size() < batchSize) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        break;
                    }
                    PendingMessage<T> next = buffer.poll(remainingNanos, TimeUnit.NANOSECONDS);
                    if (next == null) {
                        break;
                    }
                    batch.add(next);
                }
                processBatch(batch);
            } catch (InterruptedException e) {
                if (running.get()) {
                    Thread.currentThread().interrupt();
                    failBuffered(e);
                    return;
                }
            }
        }
    }

    private void processBatch(List<PendingMessage<T>> batch) {
        Map<String, List<T>> groupedPayloads = new LinkedHashMap<>();
        for (PendingMessage<T> pending : batch) {
            groupedPayloads.computeIfAbsent(pending.message().key(), ignored -> new ArrayList<>())
                    .add(pending.message().payload());
        }

        try {
            CompletableFuture<?>[] tasks = groupedPayloads.values().stream()
                    .map(payloads -> CompletableFuture.runAsync(() -> delegate.accept(payloads), workers))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(tasks).join();
            batch.forEach(pending -> pending.completion().complete(null));
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            batch.forEach(pending -> pending.completion().completeExceptionally(cause));
        } catch (RuntimeException e) {
            batch.forEach(pending -> pending.completion().completeExceptionally(e));
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        coordinator.shutdown();
        try {
            if (!coordinator.awaitTermination(30, TimeUnit.SECONDS)) {
                coordinator.shutdownNow();
                failBuffered(new IllegalStateException("Batching message handler stop timed out"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            coordinator.shutdownNow();
            failBuffered(e);
        }
        // coordinator 退出前会等待已提交的 worker future；此时所有 handle completion 均已完成。
        workers.shutdown();
        try {
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }

    private void failBuffered(Throwable cause) {
        List<PendingMessage<T>> pending = new ArrayList<>();
        buffer.drainTo(pending);
        pending.forEach(message -> message.completion().completeExceptionally(cause));
    }

    private record PendingMessage<T>(KeyedMessage<T> message, CompletableFuture<Void> completion) {
    }
}
