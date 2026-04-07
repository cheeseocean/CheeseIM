package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.api.protocol.proto.ProtoHistoryEvent;

import java.util.stream.Collectors;

/**
 * {@code HistoryEvent} 与 protobuf 历史事件模型之间的转换器。
 */
public final class ProtoHistoryEventMapper {

    private ProtoHistoryEventMapper() {
    }

    public static ProtoHistoryEvent toProto(HistoryEvent event) {
        ProtoHistoryEvent.Builder builder = ProtoHistoryEvent.newBuilder();
        if (event == null) {
            return builder.build();
        }
        if (event.getConversationId() != null) {
            builder.setConversationId(event.getConversationId());
        }
        if (event.getLastMaxSeq() != null) {
            builder.setLastMaxSeq(event.getLastMaxSeq());
        }
        if (event.getBeginSeq() != null) {
            builder.setBeginSeq(event.getBeginSeq());
        }
        if (event.getEndSeq() != null) {
            builder.setEndSeq(event.getEndSeq());
        }
        if (event.getMessages() != null && !event.getMessages().isEmpty()) {
            builder.addAllMessages(event.getMessages().stream()
                    .map(ProtoMessageMapper::toProto)
                    .collect(Collectors.toList()));
        }
        return builder.build();
    }

    public static HistoryEvent fromProto(ProtoHistoryEvent proto) {
        HistoryEvent event = new HistoryEvent();
        if (proto == null) {
            return event;
        }
        event.setConversationId(emptyToNull(proto.getConversationId()));
        if (proto.getLastMaxSeq() != 0L) {
            event.setLastMaxSeq(proto.getLastMaxSeq());
        }
        if (proto.getBeginSeq() != 0L) {
            event.setBeginSeq(proto.getBeginSeq());
        }
        if (proto.getEndSeq() != 0L) {
            event.setEndSeq(proto.getEndSeq());
        }
        if (proto.getMessagesCount() > 0) {
            event.setMessages(proto.getMessagesList().stream()
                    .map(ProtoMessageMapper::fromProto)
                    .collect(Collectors.toList()));
        }
        return event;
    }

    public static HistoryEvent parse(byte[] body) throws java.io.IOException {
        return fromProto(ProtoHistoryEvent.parseFrom(body));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
