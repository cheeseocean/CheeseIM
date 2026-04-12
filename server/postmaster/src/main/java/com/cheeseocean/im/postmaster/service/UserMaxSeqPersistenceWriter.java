package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
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
 * user maxSeq 异步写入 MongoDB 的写后缓冲。
 *
 * <p>消息主链路先写入 Redis 热状态，MongoDB 按批异步收敛，
 * 以便断线重连和多端恢复时能够获得稳定的持久化位点。
 */
@Component
public class UserMaxSeqPersistenceWriter {

    private static final Logger log = CommonLoggers.POSTMASTER;

    private static final int  QUEUE_CAPACITY   = 2000;
    private static final int  DRAIN_BATCH_SIZE = 200;
    private static final long POLL_TIMEOUT_MS  = 1000;

    record UserMaxSeqEntry(String userId, String conversationId, long maxSeq) {}

    private final UserConversationSyncPointRepository syncPointRepository;
    private final LinkedBlockingQueue<UserMaxSeqEntry> queue;
    private final Thread drainThread;
    private volatile boolean running = true;

    public UserMaxSeqPersistenceWriter(UserConversationSyncPointRepository syncPointRepository) {
        this.syncPointRepository = syncPointRepository;
        this.queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.drainThread = new Thread(this::drainLoop, "user-max-seq-drain");
        this.drainThread.setDaemon(true);
        this.drainThread.start();
    }

    /**
     * 将一条 user maxSeq 更新入队；队列已满时丢弃，由 Redis 热状态继续承担短期权威值。
     */
    public void enqueue(String userId, String conversationId, long maxSeq) {
        boolean offered = queue.offer(new UserMaxSeqEntry(userId, conversationId, maxSeq));
        if (!offered) {
            log.warn("UserMaxSeqPersistenceWriter 队列已满，丢弃持久化：userId={} convId={}", userId, conversationId);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        drainThread.interrupt();
        List<UserMaxSeqEntry> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            persist(remaining);
        }
    }

    private void drainLoop() {
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
