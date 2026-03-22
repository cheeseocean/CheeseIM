package com.cheeseocean.im.client.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class TcpPacketCodec {

    public static final short MAGIC = (short) 0xCEEE;
    public static final byte VERSION = 0x01;
    public static final int OPERATION_ID_LENGTH = 16;
    public static final int HEADER_LENGTH = 2 + 1 + 1 + 4 + OPERATION_ID_LENGTH + 8;

    private TcpPacketCodec() {
    }

    public static byte[] encode(TcpPacket packet) {
        byte[] payload = packet.data() == null ? new byte[0] : packet.data().getBytes(StandardCharsets.UTF_8);
        byte[] operationId = normalizeOperationId(packet.operationId());
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + payload.length);
        buffer.putShort(MAGIC);
        buffer.put(VERSION);
        buffer.put(packet.msgType());
        buffer.putInt(payload.length);
        buffer.put(operationId);
        buffer.putLong(packet.timestamp());
        buffer.put(payload);
        return buffer.array();
    }

    public static TcpPacket read(InputStream inputStream) throws IOException {
        byte[] header = inputStream.readNBytes(HEADER_LENGTH);
        if (header.length != HEADER_LENGTH) {
            throw new IOException("incomplete tcp packet header");
        }

        ByteBuffer headerBuffer = ByteBuffer.wrap(header);
        short magic = headerBuffer.getShort();
        if (magic != MAGIC) {
            throw new IOException("invalid tcp magic: " + Integer.toHexString(Short.toUnsignedInt(magic)));
        }

        byte version = headerBuffer.get();
        if (version != VERSION) {
            throw new IOException("unsupported tcp version: " + version);
        }

        byte msgType = headerBuffer.get();
        int payloadLength = headerBuffer.getInt();
        byte[] operationIdBytes = new byte[OPERATION_ID_LENGTH];
        headerBuffer.get(operationIdBytes);
        long timestamp = headerBuffer.getLong();

        byte[] payload = inputStream.readNBytes(payloadLength);
        if (payload.length != payloadLength) {
            throw new IOException("incomplete tcp packet payload");
        }

        return new TcpPacket(
                msgType,
                new String(operationIdBytes, StandardCharsets.UTF_8).trim(),
                timestamp,
                new String(payload, StandardCharsets.UTF_8)
        );
    }

    private static byte[] normalizeOperationId(String operationId) {
        byte[] bytes = operationId == null
                ? new byte[0]
                : operationId.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[OPERATION_ID_LENGTH];
        int copyLength = Math.min(bytes.length, OPERATION_ID_LENGTH);
        System.arraycopy(bytes, 0, normalized, 0, copyLength);
        return normalized;
    }
}
