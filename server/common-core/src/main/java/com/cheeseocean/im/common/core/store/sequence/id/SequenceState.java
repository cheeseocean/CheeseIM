package com.cheeseocean.im.common.core.store.sequence.id;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单个业务序列的完整内存状态
 * <p>
 * 维护两个号段槽：
 * <ul>
 *   <li>{@code current} - 当前正在消费的号段，ID 生成从此处取</li>
 *   <li>{@code next} - 后台预取的下一号段，{@code current} 耗尽时零延迟切换</li>
 * </ul>
 * {@code lock} 仅在号段切换或同步申请时持有，正常 ID 生成路径无锁。
 */
class SequenceState {

    /**
     * 当前活跃号段
     * volatile 保证写入后对所有线程立即可见
     */
    volatile SequenceSegment current;

    /**
     * 预取的下一号段，null 表示尚未预取或已消费
     * volatile 保证可见性
     */
    volatile SequenceSegment next;

    /**
     * 号段切换和同步申请的保护锁
     * 仅在慢路径（号段耗尽）时持有，不影响快速路径性能
     */
    final ReentrantLock lock = new ReentrantLock();

    /**
     * 后台预取任务是否正在进行
     * CAS 保证每个 sequence 同时只有一个预取任务
     */
    final AtomicBoolean prefetching = new AtomicBoolean(false);
}
