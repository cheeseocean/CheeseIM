package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.api.protocol.proto.ProtoOfflinePushEvent;

import java.io.IOException;
import java.util.HashMap;

/**
 * {@code OfflinePushEvent} 与 protobuf 离线推送事件模型之间的转换器。
 */
public final class ProtoOfflinePushEventMapper {

    private ProtoOfflinePushEventMapper() {
    }

    public static ProtoOfflinePushEvent toProto(OfflinePushEvent event) {
        ProtoOfflinePushEvent.Builder builder = ProtoOfflinePushEvent.newBuilder();
        if (event == null) {
            return builder.build();
        }
        if (event.getUserId() != null) {
            builder.setUserId(event.getUserId());
        }
        if (event.getConversationId() != null) {
            builder.setConversationId(event.getConversationId());
        }
        if (event.getSeq() != null) {
            builder.setSeq(event.getSeq());
        }
        if (event.getServerMsgId() != null) {
            builder.setServerMsgId(event.getServerMsgId());
        }
        if (event.getSenderId() != null) {
            builder.setSenderId(event.getSenderId());
        }
        if (event.getSessionType() != null) {
            builder.setSessionType(event.getSessionType());
        }
        if (event.getContentType() != null) {
            builder.setContentType(event.getContentType());
        }
        builder.setNotification(event.isNotification());
        if (event.getTitle() != null) {
            builder.setTitle(event.getTitle());
        }
        if (event.getContent() != null) {
            builder.setContent(event.getContent());
        }
        if (event.getAttributes() != null && !event.getAttributes().isEmpty()) {
            builder.putAllAttributes(event.getAttributes());
        }
        return builder.build();
    }

    public static OfflinePushEvent fromProto(ProtoOfflinePushEvent proto) {
        OfflinePushEvent event = new OfflinePushEvent();
        if (proto == null) {
            return event;
        }
        event.setUserId(emptyToNull(proto.getUserId()));
        event.setConversationId(emptyToNull(proto.getConversationId()));
        if (proto.getSeq() != 0L) {
            event.setSeq(proto.getSeq());
        }
        event.setServerMsgId(emptyToNull(proto.getServerMsgId()));
        event.setSenderId(emptyToNull(proto.getSenderId()));
        if (proto.getSessionType() != 0) {
            event.setSessionType(proto.getSessionType());
        }
        if (proto.getContentType() != 0) {
            event.setContentType(proto.getContentType());
        }
        event.setNotification(proto.getNotification());
        event.setTitle(emptyToNull(proto.getTitle()));
        event.setContent(emptyToNull(proto.getContent()));
        if (!proto.getAttributesMap().isEmpty()) {
            event.setAttributes(new HashMap<>(proto.getAttributesMap()));
        }
        return event;
    }

    public static OfflinePushEvent parse(byte[] body) throws IOException {
        return fromProto(ProtoOfflinePushEvent.parseFrom(body));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
