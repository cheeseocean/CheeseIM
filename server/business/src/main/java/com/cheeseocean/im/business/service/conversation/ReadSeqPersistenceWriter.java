package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import com.cheeseocean.im.business.config.ReadSeqPersistenceWriterProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.cheeseocean.im.common.core.metrics.ImMetrics;

/**
 * readSeq 异步写入 MongoDB 的写后缓冲（write-behind buffer）。
 *
 * <p>流程：
 * <ol>
 *   <li>{@link ConversationSyncServiceImpl#markRead} 调用 {@link #enqueue} 入队。</li>
 *   <li>后台 drain 线程按 userId 分桶并行写入，同桶内按 (userId, conversationId) 聚合取最大 readSeq。</li>
 *   <li>将 readSeq 写入 {@link UserConversationSyncPointRepository}（轻量偏移量表）。</li>
 *   <li>同时重置 {@link UserConversationRepository} 的未读计数为 0。</li>
 * </ol>
 *
 * <p>Redis 在热路径同步更新（即时可见）；MongoDB 在此异步更新（持久化保障）。
 */
@Component
public class ReadSeqPersistenceWriter {

    private static final Logger log = CommonLoggers.SOCIAL;

    private static final int  DRAIN_BATCH_SIZE = 100;
    private static final long POLL_TIMEOUT_MS  = 1000;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SHUTDOWN_JOIN_TIMEOUT_MS = 30_000L;

    record ReadSeqEntry(String userId, String conversationId, long readSeq, int attempts, long queuedAtMillis) {
        ReadSeqEntry(String userId, String conversationId, long readSeq, int attempts) {
            this(userId, conversationId, readSeq, attempts, System.currentTimeMillis());
        }
    }

    public record WriterStats(long accepted, long overflowFallbacks, long retryScheduled, long exhaustedFailures) {}

    private final UserConversationSyncPointRepository offsetRepository;
    private final UserConversationRepository stateRepository;
    private final List<LinkedBlockingQueue<ReadSeqEntry>> queues;
    private final List<LinkedBlockingQueue<ReadSeqEntry>> fallbackQueues;
    private final List<Thread> drainThreads;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong overflowFallbacks = new AtomicLong();
    private final AtomicLong retryScheduled = new AtomicLong();
    private final AtomicLong exhaustedFailures = new AtomicLong();
    private final AtomicLong inFlight = new AtomicLong();
    private final AtomicLong inFlightOldestQueuedAtMillis = new AtomicLong();
    private final AtomicLong nextBacklogObservationMillis = new AtomicLong();
    private volatile boolean running = true;

    @Autowired
    public ReadSeqPersistenceWriter(UserConversationSyncPointRepository offsetRepository,
                                    UserConversationRepository stateRepository,
                                    ReadSeqPersistenceWriterProperties properties) {
        this(offsetRepository,
                stateRepository,
                properties.getWorkerCount(),
                properties.getQueueCapacityPerWorker(),
                true);
    }

    ReadSeqPersistenceWriter(UserConversationSyncPointRepository offsetRepository,
                             UserConversationRepository stateRepository,
                             int workerCount,
                             int queueCapacityPerWorker,
                             boolean startWorkers) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (queueCapacityPerWorker <= 0) {
            throw new IllegalArgumentException("queueCapacityPerWorker must be positive");
        }
        this.offsetRepository = offsetRepository;
        this.stateRepository = stateRepository;
        this.queues = new ArrayList<>(workerCount);
        this.fallbackQueues = new ArrayList<>(workerCount);
        this.drainThreads = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            LinkedBlockingQueue<ReadSeqEntry> queue = new LinkedBlockingQueue<>(queueCapacityPerWorker);
            LinkedBlockingQueue<ReadSeqEntry> fallbackQueue = new LinkedBlockingQueue<>(queueCapacityPerWorker);
            queues.add(queue);
            fallbackQueues.add(fallbackQueue);
            Thread drainThread = new Thread(() -> drainLoop(queue, fallbackQueue), "read-seq-drain-" + i);
            drainThread.setDaemon(true);
            drainThreads.add(drainThread);
            if (startWorkers) {
                drainThread.start();
            }
        }
    }

    /**
     * 将一条 readSeq 更新入队。两级有界队列都满时同步落 Mongo，失败则明确抛错由请求重试。
     */
    public void enqueue(String userId, String conversationId, long readSeq) {
        int bucket = bucketIndex(userId);
        ReadSeqEntry entry = new ReadSeqEntry(userId, conversationId, readSeq, 0);
        if (queues.get(bucket).offer(entry)) {
            accepted.incrementAndGet();
            observeBacklog(false);
            return;
        }
        if (fallbackQueues.get(bucket).offer(entry)) {
            accepted.incrementAndGet();
            overflowFallbacks.incrementAndGet();
            ImMetrics.writer("read_seq", "fallback");
            observeBacklog(false);
            return;
        }
        persistSynchronously(entry);
        accepted.incrementAndGet();
        ImMetrics.writer("read_seq", "sync_backpressure");
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
        awaitDrainThreads();
        List<ReadSeqEntry> remaining = new ArrayList<>();
        for (LinkedBlockingQueue<ReadSeqEntry> queue : queues) {
            queue.drainTo(remaining);
        }
        for (LinkedBlockingQueue<ReadSeqEntry> fallbackQueue : fallbackQueues) {
            fallbackQueue.drainTo(remaining);
        }
        if (!remaining.isEmpty()) {
            beginInFlight(remaining);
            try {
                persist(remaining, null);
            } finally {
                endInFlight(remaining.size());
            }
        }
        observeBacklog(true);
    }

    private void awaitDrainThreads() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_JOIN_TIMEOUT_MS);
        for (Thread drainThread : drainThreads) {
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMillis <= 0) break;
            try {
                drainThread.join(remainingMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                ImMetrics.writer("read_seq", "shutdown_interrupted");
                return;
            }
        }
        if (drainThreads.stream().anyMatch(Thread::isAlive)) {
            ImMetrics.writer("read_seq", "shutdown_timeout");
            log.error("readSeq 停机等待 drain 超时，仍有持久化线程未退出");
        }
    }

    // ── 内部实现 ──────────────────────────────────────────────────────────────

    private void drainLoop(LinkedBlockingQueue<ReadSeqEntry> queue,
                           LinkedBlockingQueue<ReadSeqEntry> fallbackQueue) {
        while (running) {
            try {
                ReadSeqEntry first = fallbackQueue.poll();
                if (first == null) {
                    first = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
                if (first == null) continue;
                List<ReadSeqEntry> batch = new ArrayList<>(DRAIN_BATCH_SIZE);
                batch.add(first);
                fallbackQueue.drainTo(batch, DRAIN_BATCH_SIZE - 1);
                if (batch.size() < DRAIN_BATCH_SIZE) {
                    queue.drainTo(batch, DRAIN_BATCH_SIZE - batch.size());
                }
                beginInFlight(batch);
                try {
                    persist(batch, fallbackQueue);
                } finally {
                    endInFlight(batch.size());
                    observeBacklog(true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("ReadSeqPersistenceWriter drain 异常", e);
            }
        }
    }

    int bucketIndex(String userId) {
        return Math.floorMod(userId.hashCode(), queues.size());
    }

    private void persist(List<ReadSeqEntry> entries, LinkedBlockingQueue<ReadSeqEntry> fallbackQueue) {
        // 按 (userId, conversationId) 聚合，取最大 readSeq
        Map<String, ReadSeqEntry> aggregated = new HashMap<>();
        for (ReadSeqEntry e : entries) {
            String key = e.userId() + ":" + e.conversationId();
            aggregated.merge(key, e,
                    (existing, incoming) -> incoming.readSeq() > existing.readSeq() ? incoming : existing);
        }
        for (ReadSeqEntry e : aggregated.values()) {
            try {
                // 更新偏移量表的 readSeq
                offsetRepository.updateReadSeq(e.userId(), e.conversationId(), e.readSeq());
                // 重置业务表的未读计数
                Map<String, Object> fields = new HashMap<>();
                fields.put("unreadCount", 0);
                stateRepository.updateFields(e.userId(), e.conversationId(), fields);
            } catch (Exception ex) {
                if (fallbackQueue != null && e.attempts() < MAX_RETRY_ATTEMPTS
                        && fallbackQueue.offer(new ReadSeqEntry(
                        e.userId(), e.conversationId(), e.readSeq(), e.attempts() + 1, e.queuedAtMillis()))) {
                    retryScheduled.incrementAndGet();
                    ImMetrics.writer("read_seq", "retry");
                    log.warn("readSeq 持久化失败，已进入回退队列：userId={} convId={} seq={} attempt={}",
                            e.userId(), e.conversationId(), e.readSeq(), e.attempts() + 1, ex);
                } else if (fallbackQueue != null) {
                    putReliably(fallbackQueue,
                            new ReadSeqEntry(e.userId(), e.conversationId(), e.readSeq(), 0, e.queuedAtMillis()),
                            "readSeq retry");
                    ImMetrics.writer("read_seq", "retry_exhausted_backpressure");
                } else {
                    exhaustedFailures.incrementAndGet();
                    ImMetrics.writer("read_seq", "shutdown_drop");
                    log.error("readSeq 持久化最终失败：userId={} convId={} seq={} attempts={}",
                            e.userId(), e.conversationId(), e.readSeq(), e.attempts(), ex);
                }
            }
        }
    }

    private void putReliably(LinkedBlockingQueue<ReadSeqEntry> queue, ReadSeqEntry entry, String operation) {
        try {
            queue.put(entry);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + " interrupted", exception);
        }
    }

    private void persistSynchronously(ReadSeqEntry entry) {
        try {
            offsetRepository.updateReadSeq(entry.userId(), entry.conversationId(), entry.readSeq());
            Map<String, Object> fields = new HashMap<>();
            fields.put("unreadCount", 0);
            stateRepository.updateFields(entry.userId(), entry.conversationId(), fields);
        } catch (RuntimeException exception) {
            exhaustedFailures.incrementAndGet();
            ImMetrics.writer("read_seq", "sync_failed");
            throw new IllegalStateException("readSeq 有界缓冲已满且同步持久化失败", exception);
        }
    }

    private void beginInFlight(List<ReadSeqEntry> entries) {
        long oldestQueuedAt = entries.stream()
                .mapToLong(ReadSeqEntry::queuedAtMillis)
                .min()
                .orElse(0L);
        inFlight.addAndGet(entries.size());
        inFlightOldestQueuedAtMillis.updateAndGet(existing ->
                existing <= 0 ? oldestQueuedAt : Math.min(existing, oldestQueuedAt));
        ImMetrics.writerBacklog("read_seq", "inflight", inFlight.get(), inFlightOldestQueuedAtMillis.get());
    }

    private void endInFlight(int count) {
        long remaining = inFlight.addAndGet(-count);
        if (remaining <= 0) {
            inFlight.set(0);
            inFlightOldestQueuedAtMillis.set(0);
        }
        ImMetrics.writerBacklog("read_seq", "inflight", inFlight.get(), inFlightOldestQueuedAtMillis.get());
    }

    private void observeBacklog(boolean force) {
        long now = System.currentTimeMillis();
        if (!force) {
            long next = nextBacklogObservationMillis.get();
            if (now < next || !nextBacklogObservationMillis.compareAndSet(next, now + 1_000L)) {
                return;
            }
        } else {
            nextBacklogObservationMillis.set(now + 1_000L);
        }
        long depth = 0;
        long oldestQueuedAt = Long.MAX_VALUE;
        for (LinkedBlockingQueue<ReadSeqEntry> queue : queues) {
            depth += queue.size();
            ReadSeqEntry head = queue.peek();
            if (head != null) oldestQueuedAt = Math.min(oldestQueuedAt, head.queuedAtMillis());
        }
        for (LinkedBlockingQueue<ReadSeqEntry> queue : fallbackQueues) {
            depth += queue.size();
            ReadSeqEntry head = queue.peek();
            if (head != null) oldestQueuedAt = Math.min(oldestQueuedAt, head.queuedAtMillis());
        }
        ImMetrics.writerBacklog("read_seq", "queued", depth,
                oldestQueuedAt == Long.MAX_VALUE ? 0L : oldestQueuedAt);
    }

}
