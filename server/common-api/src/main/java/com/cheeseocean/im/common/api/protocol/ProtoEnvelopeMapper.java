package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.protocol.proto.ProtoAuthRequest;
import com.cheeseocean.im.common.api.protocol.proto.ProtoAuthResponse;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatSendAck;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatReadNotify;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatRevokeNotify;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatTypingNotify;
import com.cheeseocean.im.common.api.protocol.proto.ProtoClientEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoConnectResponse;
import com.cheeseocean.im.common.api.protocol.proto.ProtoErrorResponse;
import com.cheeseocean.im.common.api.protocol.proto.ProtoHeartbeatResponse;
import com.cheeseocean.im.common.api.protocol.proto.ProtoForceLogoutNotify;
import com.cheeseocean.im.common.api.protocol.proto.ProtoServerEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 统一封装协议层 envelope 与 protobuf 结构之间的转换。
 */
public final class ProtoEnvelopeMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProtoEnvelopeMapper() {
    }

    public static ClientEnvelope fromProto(ProtoClientEnvelope proto) {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(CommandType.fromCode(proto.getCommand()));
        envelope.setRequestId(proto.getRequestId());
        envelope.setBody(resolveClientBody(proto));
        return envelope;
    }

    public static ProtoServerEnvelope toProto(ServerEnvelope envelope) {
        ProtoServerEnvelope.Builder builder = ProtoServerEnvelope.newBuilder()
                .setCommand(envelope.getCommand().getCode())
                .setRequestId(envelope.getRequestId() == null ? "" : envelope.getRequestId());
        switch (envelope.getCommand()) {
            case CONNECT -> builder.setConnect(toConnectResponse(envelope.getBody()));
            case AUTH -> builder.setAuth(toAuthResponse(envelope.getBody()));
            case HEARTBEAT -> builder.setHeartbeat(toHeartbeatResponse(envelope.getBody()));
            case CHAT_SEND_ACK -> builder.setChatSendAck(toChatSendAck(envelope.getBody()));
            case CHAT_RECV -> builder.setChatMessage(ProtoMessageMapper.toProto(toDispatchPayload(envelope.getBody()).getMsg()));
            case CHAT_READ -> builder.setChatReadNotify(toChatReadNotify(envelope.getBody()));
            case CHAT_REVOKE -> builder.setChatRevokeNotify(toChatRevokeNotify(envelope.getBody()));
            case CHAT_TYPING -> builder.setChatTypingNotify(toChatTypingNotify(envelope.getBody()));
            case FORCE_LOGOUT -> builder.setForceLogout(toForceLogoutNotify(envelope.getBody()));
            case ERROR -> builder.setError(toErrorResponse(envelope.getBody()));
            default -> builder.setError(ProtoErrorResponse.newBuilder()
                    .setCode(500)
                    .setMessage("unsupported envelope command")
                    .build());
        }
        return builder.build();
    }

    private static byte[] resolveClientBody(ProtoClientEnvelope proto) {
        return switch (proto.getPayloadCase()) {
            case AUTH -> proto.getAuth().toByteArray();
            case CHAT_MESSAGE -> proto.getChatMessage().toByteArray();
            case CHAT_READ -> proto.getChatRead().toByteArray();
            case CHAT_REVOKE -> proto.getChatRevoke().toByteArray();
            case CHAT_TYPING -> proto.getChatTyping().toByteArray();
            case PAYLOAD_NOT_SET -> null;
        };
    }

    private static ProtoAuthResponse toAuthResponse(Object body) {
        Map<?, ?> map = OBJECT_MAPPER.convertValue(body, Map.class);
        return ProtoAuthResponse.newBuilder()
                .setUserId(stringValue(firstNonNull(map.get("userID"), map.get("userId"))))
                .setMessage(stringValue(map.get("message")))
                .build();
    }

    private static ProtoConnectResponse toConnectResponse(Object body) {
        if (body == null) {
            return ProtoConnectResponse.getDefaultInstance();
        }
        if (body instanceof String message) {
            return ProtoConnectResponse.newBuilder()
                    .setMessage(message)
                    .build();
        }
        Map<?, ?> map = OBJECT_MAPPER.convertValue(body, Map.class);
        return ProtoConnectResponse.newBuilder()
                .setConnId(stringValue(firstNonNull(map.get("connId"), map.get("connID"))))
                .setMessage(stringValue(firstNonNull(map.get("message"), map.get("msg"))))
                .build();
    }

    private static ProtoHeartbeatResponse toHeartbeatResponse(Object body) {
        return ProtoHeartbeatResponse.newBuilder()
                .setMessage(body == null ? "" : String.valueOf(body))
                .build();
    }

    private static ProtoChatSendAck toChatSendAck(Object body) {
        Map<?, ?> map = OBJECT_MAPPER.convertValue(body, Map.class);
        ProtoChatSendAck.Builder builder = ProtoChatSendAck.newBuilder();
        Object serverMsgId = firstNonNull(map.get("serverMsgID"), map.get("serverMsgId"));
        Object clientMsgId = firstNonNull(map.get("clientMsgID"), map.get("clientMsgId"));
        Object sendTime = map.get("sendTime");
        if (serverMsgId != null) {
            builder.setServerMsgId(String.valueOf(serverMsgId));
        }
        if (clientMsgId != null) {
            builder.setClientMsgId(String.valueOf(clientMsgId));
        }
        if (sendTime instanceof Number number) {
            builder.setSendTime(number.longValue());
        }
        return builder.build();
    }

    private static ProtoErrorResponse toErrorResponse(Object body) {
        Map<?, ?> map = OBJECT_MAPPER.convertValue(body, Map.class);
        ProtoErrorResponse.Builder builder = ProtoErrorResponse.newBuilder();
        Object code = map.get("code");
        if (code instanceof Number number) {
            builder.setCode(number.intValue());
        }
        Object message = map.get("message");
        if (message != null) {
            builder.setMessage(String.valueOf(message));
        }
        return builder.build();
    }

    private static ProtoChatReadNotify toChatReadNotify(Object body) {
        Map<?, ?> map = OBJECT_MAPPER.convertValue(body, Map.class);
        return ProtoChatReadNotify.newBuilder()
                .setConversationId(stringValue(map.get("conversationId")))
                .setReaderId(stringValue(map.get("readerId")))
                .setReadSeq(longValue(map.get("readSeq")))
                .setUpdatedAt(longValue(map.get("updatedAt")))
                .build();
    }

    private static ProtoChatRevokeNotify toChatRevokeNotify(Object body) {
        Map<?, ?> map = OBJECT_MAPPER.convertValue(body, Map.class);
        return ProtoChatRevokeNotify.newBuilder()
                .setConversationId(stringValue(map.get("conversationId")))
                .setServerMsgId(stringValue(map.get("serverMsgId")))
                .setOperatorUserId(stringValue(map.get("operatorUserId")))
                .setOperatorName(stringValue(map.get("operatorName")))
                .setTargetSenderId(stringValue(map.get("targetSenderId")))
                .setTargetSenderName(stringValue(map.get("targetSenderName")))
                .setRevokedAt(longValue(map.get("revokedAt")))
                .setMutationVersion(longValue(map.get("mutationVersion")))
                .build();
    }

    private static ProtoChatTypingNotify toChatTypingNotify(Object body) {
        Map<?, ?> map = OBJECT_MAPPER.convertValue(body, Map.class);
        return ProtoChatTypingNotify.newBuilder()
                .setConversationId(stringValue(map.get("conversationId")))
                .setSenderId(stringValue(map.get("senderId")))
                .setAction((int) longValue(map.get("action")))
                .setExpiresAt(longValue(map.get("expiresAt")))
                .build();
    }

    private static ProtoForceLogoutNotify toForceLogoutNotify(Object body) {
        Map<?, ?> map = OBJECT_MAPPER.convertValue(body, Map.class);
        return ProtoForceLogoutNotify.newBuilder()
                .setReason(stringValue(map.get("reason")))
                .setSessionId(stringValue(map.get("sessionId")))
                .setDeviceId(stringValue(map.get("deviceId")))
                .setOccurredAt(longValue(map.get("occurredAt")))
                .build();
    }

    private static DispatchPayload toDispatchPayload(Object body) {
        return OBJECT_MAPPER.convertValue(body, DispatchPayload.class);
    }

    public static ProtoAuthRequest parseAuthRequest(byte[] body) throws java.io.IOException {
        return ProtoAuthRequest.parseFrom(body);
    }

    public static com.cheeseocean.im.common.api.dto.message.Message parseMessage(byte[] body) throws java.io.IOException {
        return ProtoMessageMapper.fromProto(com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(body));
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
