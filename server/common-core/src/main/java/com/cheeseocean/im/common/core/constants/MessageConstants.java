package com.cheeseocean.im.common.core.constants;

public final class MessageConstants {
    public static final int MSG_STATUS_SENDING = 1;
    public static final int MSG_STATUS_SUCCESS = 2;
    public static final int MSG_STATUS_FAILED = 3;

    public static final String REDIS_KEY_USER_TOKEN = "cheese_im:user:token:";
    public static final String REDIS_KEY_USER_ONLINE = "cheese_im:user:online:";
    public static final String REDIS_KEY_USER_SESSION = "cheese_im:user:session:";
    public static final String REDIS_KEY_MSG_SEQ = "cheese_im:msg:seq:";

    private MessageConstants() {
    }
}
