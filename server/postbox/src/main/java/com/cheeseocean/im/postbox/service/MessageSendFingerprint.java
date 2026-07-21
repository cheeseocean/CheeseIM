package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.protocol.proto.ProtoMessage;
import com.google.protobuf.CodedOutputStream;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 消息发送 inbox 的稳定指纹。
 *
 * <p>服务端生成字段不参与指纹；protobuf map 使用确定性序列化，保证客户端重试时字段顺序变化
 * 不会被误判为不同载荷。</p>
 */
final class MessageSendFingerprint {

    private MessageSendFingerprint() {
    }

    static String payload(Message message) {
        ProtoMessage canonical = ProtoMessageMapper.toProto(message).toBuilder()
                .clearServerMsgId()
                .clearSendTime()
                .clearCreateTime()
                .clearStatus()
                .clearSeq()
                .build();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(canonical.getSerializedSize());
            CodedOutputStream output = CodedOutputStream.newInstance(bytes);
            output.useDeterministicSerialization();
            canonical.writeTo(output);
            output.flush();
            return sha256(bytes.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to serialize message fingerprint", exception);
        }
    }

    static String identity(String senderId, String conversationId, String clientMsgId) {
        MessageDigest digest = sha256Digest();
        updatePart(digest, senderId);
        updatePart(digest, conversationId);
        updatePart(digest, clientMsgId);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(sha256Digest().digest(value));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updatePart(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
