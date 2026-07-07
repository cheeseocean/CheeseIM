package com.cheeseocean.im.postman.sender;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.protocol.ProtoOfflinePushEventMapper;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueProducer;
import org.springframework.stereotype.Component;

/**
 * OFFLINE_PUSH 队列生产者：把 {@link OfflinePushEvent} 转成 protobuf 字节后通过
 * {@link QueueAdapter} 投递。这样 Chronicle / Kafka 两种队列后端（见
 * {@code cheeseim.queue.type}）走同一条抽象路径，避免历史上
 * {@code DeliveryEventListener.emitOfflinePushIfNeeded} 直连 {@code KafkaTemplate}
 * 导致切回 Chronicle 时离线推送失效（ASSESSMENT P0-6 修复点）。
 *
 * <p>同时，消费端 {@link com.cheeseocean.im.postman.listener.OfflinePushEventListener}
 * 的 {@code @QueueListener(topic=OFFLINE_PUSH)} 入参声明为 {@code byte[]}，
 * 与本生产者发送的 protobuf 原始字节配对，由
 * {@link ProtoOfflinePushEventMapper#parse(byte[])} 完成反序列化。两端通过
 * {@code QueueAdapter.send(topic, key, bytes)} / {@code QueueAdapter.subscribe(topic, group, ..., byte[].class, ...)}
 * 统一签名，与 postmaster 的 {@code MessageProducer}/{@code HistoryEventProducer}
 * 保持一致。
 */
@Component
public class OfflinePushEventProducer implements QueueProducer<OfflinePushEvent> {

    private final QueueAdapter queueAdapter;

    public OfflinePushEventProducer(QueueAdapter queueAdapter) {
        this.queueAdapter = queueAdapter;
    }

    @Override
    public void publish(String key, OfflinePushEvent data) {
        queueAdapter.send(TopicNames.OFFLINE_PUSH, key, ProtoOfflinePushEventMapper.toProto(data).toByteArray());
    }
}