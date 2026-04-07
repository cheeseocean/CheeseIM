package com.cheeseocean.im.common.core.queue;

/**
 * @author xxxcrel
 * @date 2026/4/5 22:01
 */
public interface QueueProducer<T> {

    /**
     * 发送数据到队列
     */
    void publish(String key, T data);
}
