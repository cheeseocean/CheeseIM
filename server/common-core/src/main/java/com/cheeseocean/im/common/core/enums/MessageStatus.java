package com.cheeseocean.im.common.core.enums;

public final class MessageStatus {

    private MessageStatus() {
    }

    public static final int SENDING = 0;
    public static final int SEND_SUCCESS = 1;
    public static final int REVOKED = 2;
    public static final int DELETED = 3;
}
