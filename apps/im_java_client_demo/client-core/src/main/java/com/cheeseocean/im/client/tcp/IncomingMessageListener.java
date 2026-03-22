package com.cheeseocean.im.client.tcp;

import com.cheeseocean.im.client.protocol.TcpPacket;

public interface IncomingMessageListener {

    default void onConnected() {
    }

    default void onAuthSuccess() {
    }

    default void onAuthFailed(TcpPacket packet) {
    }

    default void onSendAck(TcpPacket packet) {
    }

    default void onMessage(TcpPacket packet) {
    }

    default void onError(Throwable error) {
    }

    default void onDisconnected() {
    }

    static IncomingMessageListener noop() {
        return new IncomingMessageListener() {
        };
    }
}
