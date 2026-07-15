package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import com.cheeseocean.im.business.config.ReadSeqPersistenceWriterProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

    record ReadSeqEntry(String userId, String conversationId, long readSeq) {}

    private final UserConversationSyncPointRepository offsetRepository;
    private final UserConversationRepository stateRepository;
    private final List<LinkedBlockingQueue<ReadSeqEntry>> queues;
    private final List<Thread> drainThreads;
    private volatile boolean running = true;

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
        this.drainThreads = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            LinkedBlockingQueue<ReadSeqEntry> queue = new LinkedBlockingQueue<>(queueCapacityPerWorker);
            queues.add(queue);
            Thread drainThread = new Thread(() -> drainLoop(queue), "read-seq-drain-" + i);
            drainThread.setDaemon(true);
            drainThreads.add(drainThread);
            if (startWorkers) {
                drainThread.start();
            }
        }
    }

    /**
     * 将一条 readSeq 更新入队。队列已满时丢弃并打印警告——Redis 已持有权威值。
     */
    public void enqueue(String userId, String conversationId, long readSeq) {
        LinkedBlockingQueue<ReadSeqEntry> queue = queues.get(bucketIndex(userId));
        boolean offered = queue.offer(new ReadSeqEntry(userId, conversationId, readSeq));
        if (!offered) {
            log.warn("ReadSeqPersistenceWriter 分桶队列已满，丢弃 readSeq 持久化：userId={} convId={}",
                    userId, conversationId);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        for (Thread drainThread : drainThreads) {
            drainThread.interrupt();
        }
        List<ReadSeqEntry> remaining = new ArrayList<>();
        for (LinkedBlockingQueue<ReadSeqEntry> queue : queues) {
            queue.drainTo(remaining);
        }
        if (!remaining.isEmpty()) {
            persist(remaining);
        }
    }

    // ── 内部实现 ──────────────────────────────────────────────────────────────

    private void drainLoop(LinkedBlockingQueue<ReadSeqEntry> queue) {
        while (running) {
            try {
                ReadSeqEntry first = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                List<ReadSeqEntry> batch = new ArrayList<>(DRAIN_BATCH_SIZE);
                batch.add(first);
                queue.drainTo(batch, DRAIN_BATCH_SIZE - 1);
                persist(batch);
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

    private void persist(List<ReadSeqEntry> entries) {
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
                log.error("readSeq 持久化失败：userId={} convId={} seq={}",
                        e.userId(), e.conversationId(), e.readSeq(), ex);
            }
        }
    }
}
