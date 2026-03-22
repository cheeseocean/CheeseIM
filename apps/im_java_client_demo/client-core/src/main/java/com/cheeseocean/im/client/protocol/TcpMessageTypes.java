package com.cheeseocean.im.client.protocol;

public final class TcpMessageTypes {

    public static final byte TCP_CONNECT_REQ = 1;
    public static final byte TCP_CONNECT_SUCCESS = 2;
    public static final byte TCP_CONNECT_FAILED = 3;
    public static final byte TCP_AUTH_REQ = 10;
    public static final byte TCP_AUTH_SUCCESS = 11;
    public static final byte TCP_AUTH_FAILED = 12;
    public static final byte TCP_HEARTBEAT_REQ = 20;
    public static final byte TCP_HEARTBEAT_RESP = 21;
    public static final byte TCP_SEND_MSG_REQ = 30;
    public static final byte TCP_SEND_MSG_RESP = 31;
    public static final byte TCP_RECV_MSG_NOTIFY = 32;

    private TcpMessageTypes() {
    }
}
