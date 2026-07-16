package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postmaster.config.UserMaxSeqPersistenceWriterProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.cheeseocean.im.common.core.metrics.ImMetrics;

/**
 * user maxSeq 异步写入 MongoDB 的写后缓冲。
 *
 * <p>消息主链路先写入 Redis 热状态，MongoDB 按批异步收敛，
 * 以便断线重连和多端恢复时能够获得稳定的持久化位点。
 */
@Component
public class UserMaxSeqPersistenceWriter {

    private static final Logger log = CommonLoggers.POSTMASTER;

    private static final int  DRAIN_BATCH_SIZE = 200;
    private static final long POLL_TIMEOUT_MS  = 1000;

    private static final int MAX_RETRY_ATTEMPTS = 3;

    record UserMaxSeqEntry(String userId, String conversationId, long maxSeq, int attempts) {}

    public record WriterStats(long accepted, long overflowFallbacks, long retryScheduled, long exhaustedFailures) {}

    private final UserConversationSyncPointRepository syncPointRepository;
    private final List<LinkedBlockingQueue<UserMaxSeqEntry>> queues;
    private final List<LinkedBlockingQueue<UserMaxSeqEntry>> fallbackQueues;
    private final List<Thread> drainThreads;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong overflowFallbacks = new AtomicLong();
    private final AtomicLong retryScheduled = new AtomicLong();
    private final AtomicLong exhaustedFailures = new AtomicLong();
    private volatile boolean running = true;

    public UserMaxSeqPersistenceWriter(UserConversationSyncPointRepository syncPointRepository,
                                       UserMaxSeqPersistenceWriterProperties properties) {
        this(syncPointRepository,
                properties.getWorkerCount(),
                properties.getQueueCapacityPerWorker(),
                true);
    }

    UserMaxSeqPersistenceWriter(UserConversationSyncPointRepository syncPointRepository,
                                int workerCount,
                                int queueCapacityPerWorker,
                                boolean startWorkers) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (queueCapacityPerWorker <= 0) {
            throw new IllegalArgumentException("queueCapacityPerWorker must be positive");
        }
        this.syncPointRepository = syncPointRepository;
        this.queues = new ArrayList<>(workerCount);
        this.fallbackQueues = new ArrayList<>(workerCount);
        this.drainThreads = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            LinkedBlockingQueue<UserMaxSeqEntry> queue = new LinkedBlockingQueue<>(queueCapacityPerWorker);
            LinkedBlockingQueue<UserMaxSeqEntry> fallbackQueue = new LinkedBlockingQueue<>(queueCapacityPerWorker);
            queues.add(queue);
            fallbackQueues.add(fallbackQueue);
            Thread drainThread = new Thread(() -> drainLoop(queue, fallbackQueue), "user-max-seq-drain-" + i);
            drainThread.setDaemon(true);
            drainThreads.add(drainThread);
            if (startWorkers) {
                drainThread.start();
            }
        }
    }

    /**
     * 将一条 user maxSeq 更新入队；两级有界队列都满时同步落 Mongo，失败明确抛错。
     */
    public void enqueue(String userId, String conversationId, long maxSeq) {
        int bucket = bucketIndex(userId);
        UserMaxSeqEntry entry = new UserMaxSeqEntry(userId, conversationId, maxSeq, 0);
        if (queues.get(bucket).offer(entry)) {
            accepted.incrementAndGet();
            return;
        }
        if (fallbackQueues.get(bucket).offer(entry)) {
            accepted.incrementAndGet();
            overflowFallbacks.incrementAndGet();
            ImMetrics.writer("user_max_seq", "fallback");
            return;
        }
        persistSynchronously(entry);
        accepted.incrementAndGet();
        ImMetrics.writer("user_max_seq", "sync_backpressure");
    }

    public WriterStats stats() {
        return new WriterStats(accepted.get(), overflowFallbacks.get(), retryScheduled.get(), exhaustedFailures.get());
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        for (Thread drainThread : drainThreads) {
            drainThread.interrupt();
        }
        List<UserMaxSeqEntry> remaining = new ArrayList<>();
        for (LinkedBlockingQueue<UserMaxSeqEntry> queue : queues) {
            queue.drainTo(remaining);
        }
        for (LinkedBlockingQueue<UserMaxSeqEntry> fallbackQueue : fallbackQueues) {
            fallbackQueue.drainTo(remaining);
        }
        if (!remaining.isEmpty()) {
            persist(remaining, null);
        }
    }

    private void drainLoop(LinkedBlockingQueue<UserMaxSeqEntry> queue,
                           LinkedBlockingQueue<UserMaxSeqEntry> fallbackQueue) {
        while (running) {
            try {
                UserMaxSeqEntry first = fallbackQueue.poll();
                if (first == null) {
                    first = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
                if (first == null) {
                    continue;
                }
                List<UserMaxSeqEntry> batch = new ArrayList<>(DRAIN_BATCH_SIZE);
                batch.add(first);
                fallbackQueue.drainTo(batch, DRAIN_BATCH_SIZE - 1);
                if (batch.size() < DRAIN_BATCH_SIZE) {
                    queue.drainTo(batch, DRAIN_BATCH_SIZE - batch.size());
                }
                persist(batch, fallbackQueue);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("UserMaxSeqPersistenceWriter drain 异常", e);
            }
        }
    }

    int bucketIndex(String userId) {
        return Math.floorMod(userId.hashCode(), queues.size());
    }

    private void persist(List<UserMaxSeqEntry> entries,
                         LinkedBlockingQueue<UserMaxSeqEntry> fallbackQueue) {
        Map<String, UserMaxSeqEntry> aggregated = new HashMap<>();
        for (UserMaxSeqEntry entry : entries) {
            String key = entry.userId() + ":" + entry.conversationId();
            aggregated.merge(key, entry,
                    (existing, incoming) -> incoming.maxSeq() > existing.maxSeq() ? incoming : existing);
        }
        for (UserMaxSeqEntry entry : aggregated.values()) {
            try {
                syncPointRepository.updateMaxSeq(entry.userId(), entry.conversationId(), entry.maxSeq());
            } catch (Exception ex) {
                if (fallbackQueue != null && entry.attempts() < MAX_RETRY_ATTEMPTS
                        && fallbackQueue.offer(new UserMaxSeqEntry(entry.userId(), entry.conversationId(),
                        entry.maxSeq(), entry.attempts() + 1))) {
                    retryScheduled.incrementAndGet();
                    ImMetrics.writer("user_max_seq", "retry");
                    log.warn("user maxSeq 持久化失败，已进入回退队列：userId={} convId={} seq={} attempt={}",
                            entry.userId(), entry.conversationId(), entry.maxSeq(), entry.attempts() + 1, ex);
                } else if (fallbackQueue != null) {
                    putReliably(fallbackQueue,
                            new UserMaxSeqEntry(entry.userId(), entry.conversationId(), entry.maxSeq(), 0));
                    ImMetrics.writer("user_max_seq", "retry_exhausted_backpressure");
                } else {
                    exhaustedFailures.incrementAndGet();
                    ImMetrics.writer("user_max_seq", "drop");
                    log.error("user maxSeq 持久化最终失败：userId={} convId={} seq={} attempts={}",
                            entry.userId(), entry.conversationId(), entry.maxSeq(), entry.attempts(), ex);
                }
            }
        }
    }

    private void putReliably(LinkedBlockingQueue<UserMaxSeqEntry> queue, UserMaxSeqEntry entry) {
        try {
            queue.put(entry);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("user maxSeq retry interrupted", exception);
        }
    }

    private void persistSynchronously(UserMaxSeqEntry entry) {
        try {
            syncPointRepository.updateMaxSeq(entry.userId(), entry.conversationId(), entry.maxSeq());
        } catch (RuntimeException exception) {
            exhaustedFailures.incrementAndGet();
            ImMetrics.writer("user_max_seq", "sync_failed");
            throw new IllegalStateException("user maxSeq 有界缓冲已满且同步持久化失败", exception);
        }
    }

}
