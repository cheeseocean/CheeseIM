package com.cheeseocean.im.postoffice.codec;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ProtoEnvelopeMapper;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoClientEnvelope;
import com.cheeseocean.im.common.api.enums.CommandType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class TcpEnvelopeCodecSupport {

    static final short MAGIC = (short) 0xCEEE;
    static final byte VERSION = 0x01;
    static final int HEADER_LENGTH = 32;
    static final int MAX_DATA_LENGTH = 1024 * 1024;
    static final byte TCP_CONNECT_REQ = 1;
    static final byte TCP_CONNECT_SUCCESS = 2;
    static final byte TCP_AUTH_REQ = 10;
    static final byte TCP_AUTH_SUCCESS = 11;
    static final byte TCP_HEARTBEAT_REQ = 20;
    static final byte TCP_HEARTBEAT_RESP = 21;
    static final byte TCP_SEND_MSG_REQ = 30;
    static final byte TCP_SEND_MSG_RESP = 31;
    static final byte TCP_RECV_MSG_NOTIFY = 32;
    static final byte TCP_REVOKE_MSG_REQ = 34;
    static final byte TCP_REVOKE_MSG_NOTIFY = 35;
    static final byte TCP_FORCE_LOGOUT_NOTIFY = 42;
    static final byte TCP_FRIEND_APPLICATION_NOTIFY = 70;
    static final byte TCP_FRIEND_APPLICATION_PROCESSED_NOTIFY = 71;
    static final byte TCP_FRIEND_INFO_CHANGE_NOTIFY = 72;
    static final byte TCP_ERROR_RESP = 90;

    private TcpEnvelopeCodecSupport() {
    }

    static ClientEnvelope decode(byte msgType, String requestId, byte[] data) {
        ProtoClientEnvelope proto = decodeClientProto(msgType, requestId, data);
        return ProtoEnvelopeMapper.fromProto(proto);
    }

    static byte[] encode(ServerEnvelope envelope) {
        byte msgType = resolveServerMsgType(envelope);
        byte[] payload = encodePayload(envelope, msgType);
        long timestamp = System.currentTimeMillis();
        byte[] dataBytes = payload == null ? new byte[0] : payload;
        byte[] requestBytes = envelope != null && envelope.getRequestId() != null
                ? envelope.getRequestId().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        byte[] fixedRequestId = new byte[16];
        System.arraycopy(requestBytes, 0, fixedRequestId, 0, Math.min(requestBytes.length, 16));

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + dataBytes.length);
        buffer.putShort(MAGIC);
        buffer.put(VERSION);
        buffer.put(msgType);
        buffer.putInt(dataBytes.length);
        buffer.put(fixedRequestId);
        buffer.putLong(timestamp);
        buffer.put(dataBytes);
        return buffer.array();
    }

    private static ProtoClientEnvelope decodeClientProto(byte msgType, String requestId, byte[] data) {
        ProtoClientEnvelope.Builder builder = ProtoClientEnvelope.newBuilder()
                .setCommand(resolveClientCommand(msgType).getCode())
                .setRequestId(requestId == null ? "" : requestId);
        try {
            switch (msgType) {
                case TCP_AUTH_REQ -> builder.setAuth(com.cheeseocean.im.common.api.protocol.proto.ProtoAuthRequest.parseFrom(data));
                case TCP_SEND_MSG_REQ -> builder.setChatMessage(com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(data));
                default -> {
                }
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode TCP protobuf body", e);
        }
    }

    private static byte[] encodePayload(ServerEnvelope envelope, byte msgType) {
        if (envelope == null) {
            return new byte[0];
        }
        return switch (msgType) {
            case TCP_CONNECT_SUCCESS -> ProtoEnvelopeMapper.toProto(envelope).getConnect().toByteArray();
            case TCP_AUTH_SUCCESS -> ProtoEnvelopeMapper.toProto(envelope).getAuth().toByteArray();
            case TCP_HEARTBEAT_RESP -> ProtoEnvelopeMapper.toProto(envelope).getHeartbeat().toByteArray();
            case TCP_SEND_MSG_RESP -> ProtoEnvelopeMapper.toProto(envelope).getChatSendAck().toByteArray();
            case TCP_RECV_MSG_NOTIFY,
                    TCP_FRIEND_APPLICATION_NOTIFY,
                    TCP_FRIEND_APPLICATION_PROCESSED_NOTIFY,
                    TCP_FRIEND_INFO_CHANGE_NOTIFY -> ProtoEnvelopeMapper.toProto(envelope).getChatMessage().toByteArray();
            case TCP_ERROR_RESP -> ProtoEnvelopeMapper.toProto(envelope).getError().toByteArray();
            default -> ProtoEnvelopeMapper.toProto(envelope).toByteArray();
        };
    }

    private static CommandType resolveClientCommand(byte msgType) {
        switch (msgType) {
            case TCP_SEND_MSG_REQ:
                return CommandType.CHAT_SEND;
            case TCP_REVOKE_MSG_REQ:
                return CommandType.CHAT_REVOKE;
            case TCP_AUTH_REQ:
                return CommandType.AUTH;
            case TCP_HEARTBEAT_REQ:
                return CommandType.HEARTBEAT;
            case TCP_CONNECT_REQ:
                return CommandType.CONNECT;
            default:
                return null;
        }
    }

    private static byte resolveServerMsgType(ServerEnvelope envelope) {
        if (envelope == null || envelope.getCommand() == null) {
            return TCP_ERROR_RESP;
        }
        switch (envelope.getCommand()) {
            case CONNECT:
                return TCP_CONNECT_SUCCESS;
            case AUTH:
                return TCP_AUTH_SUCCESS;
            case HEARTBEAT:
                return TCP_HEARTBEAT_RESP;
            case CHAT_SEND:
                return TCP_SEND_MSG_RESP;
            case CHAT_RECV:
                return resolveChatRecvMsgType(envelope.getBody());
            case CHAT_REVOKE:
                return TCP_REVOKE_MSG_NOTIFY;
            case FORCE_LOGOUT:
                return TCP_FORCE_LOGOUT_NOTIFY;
            case ERROR:
            default:
                return TCP_ERROR_RESP;
        }
    }

    private static byte resolveChatRecvMsgType(Object body) {
        DispatchPayload payload = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(body, DispatchPayload.class);
        if (payload.getMsg() == null || payload.getMsg().getAttributes() == null) {
            return TCP_RECV_MSG_NOTIFY;
        }
        String notificationType = payload.getMsg().getAttributes().get("notificationType");
        if ("friend_request_created".equals(notificationType)) {
            return TCP_FRIEND_APPLICATION_NOTIFY;
        }
        if ("friend_request_accepted".equals(notificationType)) {
            return TCP_FRIEND_INFO_CHANGE_NOTIFY;
        }
        if ("friend_request_rejected".equals(notificationType)
                || "friend_request_cancelled".equals(notificationType)) {
            return TCP_FRIEND_APPLICATION_PROCESSED_NOTIFY;
        }
        return TCP_RECV_MSG_NOTIFY;
    }
}
