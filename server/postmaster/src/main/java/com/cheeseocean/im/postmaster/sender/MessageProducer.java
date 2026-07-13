package com.cheeseocean.im.postmaster.sender;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.protocol.proto.ProtoMessage;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.queue.QueueProducer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    /** 批量发布普通会话或通知消息，调用方负责提供稳定的 partition key。 */
    public void publishBatch(List<KeyedMessage<Message>> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<KeyedMessage<byte[]>> payloads = new ArrayList<>(messages.size());
        for (KeyedMessage<Message> message : messages) {
            if (message != null && message.payload() != null) {
                payloads.add(new KeyedMessage<>(message.key(),
                        ProtoMessageMapper.toProto(message.payload()).toByteArray()));
            }
        }
        queueAdapter.sendBatch(TopicNames.DELIVERY, payloads);
    }

    /**
     * 批量完成普通群写扩散。
     *
     * <p>每条消息只转换一次基础 protobuf；随后对一个成员切片重写 receiverId，并通过队列批量接口提交。
     * 这样避免原实现为每个成员重复执行完整 Message → ProtoMessage 转换。
     */
    public void publishForTargets(List<Message> templates, List<KeyedMessage<String>> targets) {
        if (templates == null || templates.isEmpty() || targets == null || targets.isEmpty()) {
            return;
        }
        List<ProtoMessage> protoTemplates = templates.stream()
                .filter(java.util.Objects::nonNull)
                .map(ProtoMessageMapper::toProto)
                .toList();
        for (ProtoMessage template : protoTemplates) {
            List<KeyedMessage<byte[]>> payloads = new ArrayList<>(targets.size());
            for (KeyedMessage<String> target : targets) {
                if (target == null) {
                    continue;
                }
                ProtoMessage copy = template.toBuilder()
                        .setReceiverId(target.payload() == null ? "" : target.payload())
                        .build();
                payloads.add(new KeyedMessage<>(target.key(), copy.toByteArray()));
            }
            queueAdapter.sendBatch(TopicNames.DELIVERY, payloads);
        }
    }
}
