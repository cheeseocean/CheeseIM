package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postmaster.config.UserMaxSeqPersistenceWriterProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

    record UserMaxSeqEntry(String userId, String conversationId, long maxSeq) {}

    private final UserConversationSyncPointRepository syncPointRepository;
    private final List<LinkedBlockingQueue<UserMaxSeqEntry>> queues;
    private final List<Thread> drainThreads;
    private volatile boolean running = true;

    @Autowired
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
        this.drainThreads = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            LinkedBlockingQueue<UserMaxSeqEntry> queue = new LinkedBlockingQueue<>(queueCapacityPerWorker);
            queues.add(queue);
            Thread drainThread = new Thread(() -> drainLoop(queue), "user-max-seq-drain-" + i);
            drainThread.setDaemon(true);
            drainThreads.add(drainThread);
            if (startWorkers) {
                drainThread.start();
            }
        }
    }

    /**
     * 将一条 user maxSeq 更新入队；队列已满时丢弃，由 Redis 热状态继续承担短期权威值。
     */
    public void enqueue(String userId, String conversationId, long maxSeq) {
        LinkedBlockingQueue<UserMaxSeqEntry> queue = queues.get(bucketIndex(userId));
        boolean offered = queue.offer(new UserMaxSeqEntry(userId, conversationId, maxSeq));
        if (!offered) {
            log.warn("UserMaxSeqPersistenceWriter 分桶队列已满，丢弃持久化：userId={} convId={}", userId, conversationId);
        }
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
        if (!remaining.isEmpty()) {
            persist(remaining);
        }
    }

    private void drainLoop(LinkedBlockingQueue<UserMaxSeqEntry> queue) {
        while (running) {
            try {
                UserMaxSeqEntry first = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                List<UserMaxSeqEntry> batch = new ArrayList<>(DRAIN_BATCH_SIZE);
                batch.add(first);
                queue.drainTo(batch, DRAIN_BATCH_SIZE - 1);
                persist(batch);
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

    private void persist(List<UserMaxSeqEntry> entries) {
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
                log.error("user maxSeq 持久化失败：userId={} convId={} seq={}",
                        entry.userId(), entry.conversationId(), entry.maxSeq(), ex);
            }
        }
    }
}
