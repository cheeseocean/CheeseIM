package com.cheeseocean.im.postoffice.delivery;

import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.dedup.DeliveryDedupStore;
import io.netty.channel.ChannelFuture;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 超出同步响应期限的 ChannelFuture 终态收口器。
 *
 * <p>Netty listener 只投递一个轻量任务，Redis commit/abort 始终在专用有界线程池执行，
 * 禁止阻塞 EventLoop。</p>
 */
@Component
public class DeliveryWriteFinalizer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWriteFinalizer.class);

    private final ConnectionManager connectionManager;
    private final ThreadPoolExecutor executor;

    public DeliveryWriteFinalizer(ConnectionManager connectionManager,
                                  ServerProperties serverProperties) {
        this.connectionManager = connectionManager;
        ServerProperties.DeliveryConfig config = serverProperties.getDelivery();
        this.executor = new ThreadPoolExecutor(
                Math.max(1, config.getCompletionThreads()),
                Math.max(1, config.getCompletionThreads()),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, config.getCompletionQueueCapacity())),
                new CompletionThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * future 完成后异步提交或释放 claim。
     *
     * <p>队列已满时不在 EventLoop 降级执行 Redis；claim 依赖短 TTL 自动恢复。</p>
     */
    public void finalizeWhenComplete(ChannelFuture writeFuture, DeliveryDedupStore.Claim claim) {
        writeFuture.addListener(completed -> {
            try {
                executor.execute(() -> finalizeClaim(writeFuture, claim));
            } catch (RejectedExecutionException exception) {
                log.error("Delivery completion queue full, claim will recover by TTL, key={}", claim.key());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private void finalizeClaim(ChannelFuture writeFuture, DeliveryDedupStore.Claim claim) {
        if (writeFuture.isSuccess()) {
            if (!connectionManager.commitDelivery(claim)) {
                log.error("Failed to commit delayed delivery claim, key={}", claim.key());
            }
            return;
        }
        if (!connectionManager.abortDelivery(claim)) {
            log.warn("Failed to abort delayed delivery claim, key={}", claim.key());
        }
    }

    private static final class CompletionThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "postoffice-delivery-finalizer-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
