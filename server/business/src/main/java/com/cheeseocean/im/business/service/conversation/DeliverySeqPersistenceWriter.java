package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.core.business.repository.DeviceConversationDeliveryRepository;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** deliveredSeq 有界批量 write-behind；批内按用户、设备、会话聚合最大水位。 */
@Component
public class DeliverySeqPersistenceWriter {
    private static final Logger log = CommonLoggers.SOCIAL;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    record Entry(String userId, String deviceId, String conversationId, long seq, int attempts) {}
    private final DeviceConversationDeliveryRepository repository;
    private final LinkedBlockingQueue<Entry> queue;
    private final LinkedBlockingQueue<Entry> fallbackQueue;
    private final Thread worker;
    private volatile boolean running = true;

    public DeliverySeqPersistenceWriter(DeviceConversationDeliveryRepository repository) {
        this(repository, true);
    }

    DeliverySeqPersistenceWriter(DeviceConversationDeliveryRepository repository, boolean startWorker) {
        this(repository, 20_000, startWorker);
    }

    DeliverySeqPersistenceWriter(DeviceConversationDeliveryRepository repository, int queueCapacity, boolean startWorker) {
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be positive");
        this.repository = repository;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.fallbackQueue = new LinkedBlockingQueue<>(queueCapacity);
        worker = new Thread(this::drainLoop, "delivery-seq-drain");
        worker.setDaemon(true);
        if (startWorker) worker.start();
    }

    public boolean enqueue(String userId, String deviceId, String conversationId, long seq) {
        Entry entry = new Entry(userId, deviceId, conversationId, seq, 0);
        if (queue.offer(entry)) return true;
        if (fallbackQueue.offer(entry)) {
            ImMetrics.writer("delivery_seq", "fallback");
            return true;
        }
        persistSynchronously(entry);
        ImMetrics.writer("delivery_seq", "sync_backpressure");
        return true;
    }

    private void drainLoop() {
        while (running) {
            try {
                Entry first = fallbackQueue.poll();
                if (first == null) first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;
                List<Entry> entries = new ArrayList<>(500);
                entries.add(first);
                fallbackQueue.drainTo(entries, 499);
                if (entries.size() < 500) queue.drainTo(entries, 500 - entries.size());
                persist(entries);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException exception) {
                log.error("deliveredSeq write-behind drain 异常", exception);
            }
        }
    }

    void persist(List<Entry> entries) {
        Map<String, Entry> maxByDeviceConversation = new HashMap<>();
        for (Entry entry : entries) {
            String key = entry.userId() + '\0' + entry.deviceId() + '\0' + entry.conversationId();
            maxByDeviceConversation.merge(key, entry, (left, right) -> left.seq() >= right.seq() ? left : right);
        }
        for (Entry entry : maxByDeviceConversation.values()) {
            try {
                repository.updateDeliveredSeq(entry.userId(), entry.deviceId(), entry.conversationId(), entry.seq());
            } catch (RuntimeException exception) {
                if (entry.attempts() < MAX_RETRY_ATTEMPTS && fallbackQueue.offer(new Entry(
                        entry.userId(), entry.deviceId(), entry.conversationId(), entry.seq(), entry.attempts() + 1))) {
                    ImMetrics.writer("delivery_seq", "retry");
                    log.warn("deliveredSeq 持久化失败，已进入重试队列：userId={} deviceId={} convId={} attempt={}",
                            entry.userId(), entry.deviceId(), entry.conversationId(), entry.attempts() + 1, exception);
                } else {
                    putReliably(new Entry(entry.userId(), entry.deviceId(), entry.conversationId(), entry.seq(), 0));
                    ImMetrics.writer("delivery_seq", "retry_exhausted_backpressure");
                }
            }
        }
    }

    int pendingRetryCount() {
        return fallbackQueue.size();
    }

    private void putReliably(Entry entry) {
        try {
            fallbackQueue.put(entry);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("deliveredSeq retry interrupted", exception);
        }
    }

    private void persistSynchronously(Entry entry) {
        try {
            repository.updateDeliveredSeq(entry.userId(), entry.deviceId(), entry.conversationId(), entry.seq());
        } catch (RuntimeException exception) {
            ImMetrics.writer("delivery_seq", "sync_failed");
            throw new IllegalStateException("deliveredSeq 有界缓冲已满且同步持久化失败", exception);
        }
    }


    @PreDestroy
    public void shutdown() {
        running = false;
        worker.interrupt();
        List<Entry> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        fallbackQueue.drainTo(remaining);
        if (!remaining.isEmpty()) persist(remaining);
    }
}
