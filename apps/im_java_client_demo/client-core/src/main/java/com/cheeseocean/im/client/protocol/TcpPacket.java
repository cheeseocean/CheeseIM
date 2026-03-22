package com.cheeseocean.im.client.protocol;

public record TcpPacket(byte msgType, String operationId, long timestamp, String data) {
}
