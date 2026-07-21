package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.api.protocol.proto.ProtoMessage;
import com.google.protobuf.CodedOutputStream;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * ingress inbox 的稳定身份与载荷指纹。
 */
final class IngressMessageFingerprint {

    private IngressMessageFingerprint() {
    }

    static String payload(Message message) {
        ProtoMessage canonical = ProtoMessageMapper.toProto(message).toBuilder()
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
            throw new IllegalStateException("Failed to serialize ingress fingerprint", exception);
        }
    }

    static String serverMessageId(String serverMsgId) {
        return sha256(serverMsgId.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
