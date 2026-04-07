package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.protocol.proto.ProtoMessage;
import com.cheeseocean.im.common.api.protocol.proto.ProtoMessageOptions;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessageSource;
import com.cheeseocean.im.common.api.enums.MessageStatus;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.google.protobuf.ByteString;

import java.util.LinkedHashMap;

/**
 * {@code Message} 与 protobuf 消息模型之间的转换器。
 */
public final class ProtoMessageMapper {

    private ProtoMessageMapper() {
    }

    public static ProtoMessage toProto(Message message) {
        ProtoMessage.Builder builder = ProtoMessage.newBuilder();
        if (message == null) {
            return builder.build();
        }
        if (message.getClientMsgId() != null) {
            builder.setClientMsgId(message.getClientMsgId());
        }
        if (message.getServerMsgId() != null) {
            builder.setServerMsgId(message.getServerMsgId());
        }
        if (message.getSenderId() != null) {
            builder.setSenderId(message.getSenderId());
        }
        if (message.getReceiverId() != null) {
            builder.setReceiverId(message.getReceiverId());
        }
        if (message.getGroupId() != null) {
            builder.setGroupId(message.getGroupId());
        }
        if (message.getContent() != null) {
            builder.setContent(ByteString.copyFrom(message.getContent()));
        }
        if (message.getContentType() != null) {
            builder.setContentType(message.getContentType().getCode());
        }
        if (message.getSessionType() != null) {
            builder.setSessionType(message.getSessionType().getCode());
        }
        if (message.getSendTime() != null) {
            builder.setSendTime(message.getSendTime());
        }
        if (message.getCreateTime() != null) {
            builder.setCreateTime(message.getCreateTime());
        }
        if (message.getStatus() != null) {
            builder.setStatus(message.getStatus().getCode());
        }
        if (message.getPlatformType() != null) {
            builder.setPlatformCode(message.getPlatformType().getCode());
        }
        if (message.getAttributes() != null) {
            builder.putAllAttributes(message.getAttributes());
        }
        if (message.getUniqueId() != null) {
            builder.setUniqueId(message.getUniqueId());
        }
        if (message.getSource() != null) {
            builder.setSource(message.getSource().getCode());
        }
        if (message.getOptions() != null) {
            builder.setOptions(toProtoOptions(message.getOptions()));
        }
        return builder.build();
    }

    public static Message fromProto(ProtoMessage proto) {
        Message message = new Message();
        if (proto == null) {
            return message;
        }
        message.setClientMsgId(proto.getClientMsgId());
        message.setServerMsgId(emptyToNull(proto.getServerMsgId()));
        message.setSenderId(emptyToNull(proto.getSenderId()));
        message.setReceiverId(emptyToNull(proto.getReceiverId()));
        message.setGroupId(emptyToNull(proto.getGroupId()));
        message.setContent(proto.getContent().isEmpty() ? null : proto.getContent().toByteArray());
        if (proto.getContentType() != 0) {
            message.setContentType(ContentType.fromCode(proto.getContentType()));
        }
        if (proto.getSessionType() != 0) {
            message.setSessionType(SessionType.fromCode(proto.getSessionType()));
        }
        if (proto.getSendTime() != 0L) {
            message.setSendTime(proto.getSendTime());
        }
        if (proto.getCreateTime() != 0L) {
            message.setCreateTime(proto.getCreateTime());
        }
        if (proto.getStatus() != 0) {
            message.setStatus(MessageStatus.fromCode(proto.getStatus()));
        }
        if (proto.getPlatformCode() != 0) {
            message.setPlatformType(PlatformType.fromCode(proto.getPlatformCode()));
        }
        if (!proto.getAttributesMap().isEmpty()) {
            message.setAttributes(new LinkedHashMap<>(proto.getAttributesMap()));
        }
        message.setUniqueId(emptyToNull(proto.getUniqueId()));
        if (proto.getSource() != 0) {
            message.setSource(MessageSource.fromCode(proto.getSource()));
        }
        if (proto.hasOptions()) {
            message.setOptions(fromProtoOptions(proto.getOptions()));
        }
        return message;
    }

    private static ProtoMessageOptions toProtoOptions(MessageOptions options) {
        ProtoMessageOptions.Builder builder = ProtoMessageOptions.newBuilder();
        if (options.getNeedHistory() != null) {
            builder.setNeedHistory(options.getNeedHistory());
        }
        if (options.getNeedConversation() != null) {
            builder.setNeedConversation(options.getNeedConversation());
        }
        if (options.getNeedUnreadCount() != null) {
            builder.setNeedUnreadCount(options.getNeedUnreadCount());
        }
        if (options.getNeedOnlinePush() != null) {
            builder.setNeedOnlinePush(options.getNeedOnlinePush());
        }
        if (options.getNeedOfflinePush() != null) {
            builder.setNeedOfflinePush(options.getNeedOfflinePush());
        }
        if (options.getSenderSync() != null) {
            builder.setSenderSync(options.getSenderSync());
        }
        if (options.getNotification() != null) {
            builder.setNotification(options.getNotification());
        }
        if (options.getNeedLastMessage() != null) {
            builder.setNeedLastMessage(options.getNeedLastMessage());
        }
        return builder.build();
    }

    private static MessageOptions fromProtoOptions(ProtoMessageOptions proto) {
        MessageOptions options = new MessageOptions();
        if (proto.hasNeedHistory()) {
            options.setNeedHistory(proto.getNeedHistory());
        }
        if (proto.hasNeedConversation()) {
            options.setNeedConversation(proto.getNeedConversation());
        }
        if (proto.hasNeedUnreadCount()) {
            options.setNeedUnreadCount(proto.getNeedUnreadCount());
        }
        if (proto.hasNeedOnlinePush()) {
            options.setNeedOnlinePush(proto.getNeedOnlinePush());
        }
        if (proto.hasNeedOfflinePush()) {
            options.setNeedOfflinePush(proto.getNeedOfflinePush());
        }
        if (proto.hasSenderSync()) {
            options.setSenderSync(proto.getSenderSync());
        }
        if (proto.hasNotification()) {
            options.setNotification(proto.getNotification());
        }
        if (proto.hasNeedLastMessage()) {
            options.setNeedLastMessage(proto.getNeedLastMessage());
        }
        return options;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
