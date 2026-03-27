package com.cheeseocean.im.social.service;

import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.social.repository.ConversationStore;
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
 * 流程：
 *   1. {@link ConversationSyncServiceImpl#markRead} 调用 {@link #enqueue} 入队。
 *   2. 后台 drain 线程按 (userId, conversationId) 聚合取最大 readSeq，
 *      再通过 {@link ConversationStore#clearUnread} 批量刷入 MongoDB。
 *
 * Redis 在热路径同步更新（即时可见）；MongoDB 在此异步更新（持久化保障）。
 */
@Component
public class ReadSeqPersistenceWriter {

    private static final Logger log = CommonLoggers.SOCIAL;

    private static final int  QUEUE_CAPACITY   = 1000;
    private static final int  DRAIN_BATCH_SIZE = 100;
    private static final long POLL_TIMEOUT_MS  = 1000;

    record ReadSeqEntry(String userId, String conversationId, long readSeq) {}

    private final ConversationStore conversationStore;
    private final LinkedBlockingQueue<ReadSeqEntry> queue;
    private final Thread drainThread;
    private volatile boolean running = true;

    public ReadSeqPersistenceWriter(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
        this.queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.drainThread = new Thread(this::drainLoop, "read-seq-drain");
        this.drainThread.setDaemon(true);
        this.drainThread.start();
    }

    /**
     * 将一条 readSeq 更新入队。队列已满时丢弃并打印警告——Redis 已持有权威值。
     */
    public void enqueue(String userId, String conversationId, long readSeq) {
        boolean offered = queue.offer(new ReadSeqEntry(userId, conversationId, readSeq));
        if (!offered) {
            log.warn("ReadSeqPersistenceWriter 队列已满，丢弃 readSeq 持久化：userId={} convId={}",
                    userId, conversationId);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        drainThread.interrupt();
        List<ReadSeqEntry> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            persist(remaining);
        }
    }

    // ── 内部实现 ──────────────────────────────────────────────────────────────

    private void drainLoop() {
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

    private void persist(List<ReadSeqEntry> entries) {
        Map<String, ReadSeqEntry> aggregated = new HashMap<>();
        for (ReadSeqEntry e : entries) {
            String key = e.userId() + ":" + e.conversationId();
            aggregated.merge(key, e,
                    (existing, incoming) -> incoming.readSeq() > existing.readSeq() ? incoming : existing);
        }
        for (ReadSeqEntry e : aggregated.values()) {
            try {
                conversationStore.clearUnread(e.userId(), e.conversationId(), e.readSeq());
            } catch (Exception ex) {
                log.error("readSeq 持久化失败：userId={} convId={} seq={}",
                        e.userId(), e.conversationId(), e.readSeq(), ex);
            }
        }
    }
}
