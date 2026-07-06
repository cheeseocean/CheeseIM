package com.cheeseocean.im.postmaster.sender;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.protocol.proto.ProtoMessage;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueProducer;
import org.springframework.stereotype.Component;

/**
 * DELIVERY 队列生产者。
 *
 * <p>{@link #publish(String, Message)} 保持单聊/通知的"原消息直发"语义；
 * {@link #publishForMember(String, Message, String)} 用于群写扩散，
 * 通过 protobuf builder 重写 {@code receiverId}，避免 Java 侧深拷贝 {@link Message}。
 */
@Component
public class MessageProducer implements QueueProducer<Message> {

    private final QueueAdapter queueAdapter;

    public MessageProducer(QueueAdapter queueAdapter) {
        this.queueAdapter = queueAdapter;
    }

    @Override
    public void publish(String key, Message data) {
        queueAdapter.send(TopicNames.DELIVERY, key, ProtoMessageMapper.toProto(data).toByteArray());
    }

    /**
     * 群写扩散专用：以 {@code template} 为模板，在 protobuf 序列化阶段将 {@code receiverId}
     * 替换为指定成员，从而在不修改原 {@link Message} 引用的前提下产出 N 份独立 DeliveryEvent。
     *
     * @param key       DELIVERY 队列 partition key，建议使用 {@code g:{groupId}:{memberId}}
     * @param template  群消息模板（保留原 {@code chatType=GROUP} / {@code groupId} / {@code seq}）
     * @param receiverId 当前 DeliveryEvent 的收件人 userId
     */
    public void publishForMember(String key, Message template, String receiverId) {
        ProtoMessage proto = ProtoMessageMapper.toProto(template);
        ProtoMessage copy = proto.toBuilder().setReceiverId(receiverId == null ? "" : receiverId).build();
        queueAdapter.send(TopicNames.DELIVERY, key, copy.toByteArray());
    }
}