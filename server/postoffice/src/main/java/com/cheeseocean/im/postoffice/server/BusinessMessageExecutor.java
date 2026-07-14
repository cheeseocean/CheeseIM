package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.postoffice.config.ServerProperties;
import io.netty.channel.Channel;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关业务命令执行器。
 *
 * <p>Netty EventLoop 只负责收发和编解码，可能阻塞的业务逻辑按连接哈希投递到固定分片。
 * 单分片只有一个工作线程，因此同一连接的命令保持 FIFO 顺序；队列满时拒绝新任务，由调用方
 * 立即回复 503，避免在内存中无界堆积。</p>
 */
@Component
public class BusinessMessageExecutor {

    private final ThreadPoolExecutor[] executors;

    public BusinessMessageExecutor(ServerProperties serverProperties) {
        ServerProperties.BusinessConfig businessConfig = serverProperties.getBusiness();
        int shardCount = businessConfig.getActualThreads();
        int queueCapacity = Math.max(1, businessConfig.getQueueCapacity());
        int queueCapacityPerShard = Math.max(1, (queueCapacity + shardCount - 1) / shardCount);
        this.executors = new ThreadPoolExecutor[shardCount];

        for (int shard = 0; shard < shardCount; shard++) {
            executors[shard] = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacityPerShard),
                    new BusinessThreadFactory(shard),
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }
    }

    /**
     * 提交连接上的业务命令。
     *
     * @return 分片队列仍有容量时返回 {@code true}；满载或已关闭时返回 {@code false}
     */
    public boolean submit(Channel channel, Runnable task) {
        if (channel == null || task == null) {
            return false;
        }

        try {
            executors[shardIndex(channel)].execute(task);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        for (ThreadPoolExecutor executor : executors) {
            executor.shutdown();
        }
    }

    private int shardIndex(Channel channel) {
        int hash = channel.id().asLongText().hashCode();
        hash ^= hash >>> 16;
        return (hash & Integer.MAX_VALUE) % executors.length;
    }

    private static final class BusinessThreadFactory implements ThreadFactory {

        private final int shard;
        private final AtomicInteger sequence = new AtomicInteger();

        private BusinessThreadFactory(int shard) {
            this.shard = shard;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "postoffice-business-" + shard + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
