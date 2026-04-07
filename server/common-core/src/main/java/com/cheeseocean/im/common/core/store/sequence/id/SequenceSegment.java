package com.cheeseocean.im.common.core.store.sequence.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存中的一个号段区间 [start, end]
 * <p>
 * {@code cursor} 初始化为 {@code start - 1}，每次 {@link #next()} 原子递增后返回。
 * 当返回值超过 {@code end} 时，表示该号段已耗尽，需要 refill。
 * <p>
 * 并发语义：{@link #next()} 是无锁操作，多线程安全；
 * 若多个线程同时耗尽号段（cursor 超出 end），多出的 cursor 值被丢弃（产生 ID 空洞，属于预期行为）。
 */
class SequenceSegment {

    /**
     * 当前游标，原子递增
     * 初始值 = start - 1，首次 next() 返回 start
     */
    final AtomicLong cursor;

    /** 号段末尾（含），cursor 超过此值则号段耗尽 */
    final long end;

    /** 号段总容量，用于计算预取阈值 */
    final long size;

    SequenceSegment(long start, long end) {
        if (end < start) {
            throw new IllegalArgumentException(
                    "号段 end(%d) 必须 >= start(%d)".formatted(end, start));
        }
        this.cursor = new AtomicLong(start - 1);
        this.end = end;
        this.size = end - start + 1;
    }

    /**
     * 无锁获取下一个 ID
     * <p>
     * 返回值若 &gt; {@code end}，表示号段耗尽，调用方需进入慢路径 refill。
     */
    long next() {
        return cursor.incrementAndGet();
    }

    /**
     * 估算当前剩余 ID 数量（允许短暂不精确，仅用于预取触发判断）
     */
    long remaining() {
        long r = end - cursor.get();
        return r < 0 ? 0 : r;
    }

    /**
     * 是否需要触发预取
     *
     * @param thresholdRatio 剩余比例阈值，例如 0.20 表示剩余低于 20% 时预取
     */
    boolean needPrefetch(double thresholdRatio) {
        return remaining() < (long) (size * thresholdRatio);
    }
}
