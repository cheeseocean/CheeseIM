package com.cheeseocean.im.common.core.constants;

public final class MessageConstants {

    public static final int CONTENT_TYPE_TEXT = 101;
    public static final int CONTENT_TYPE_IMAGE = 102;
    public static final int CONTENT_TYPE_VOICE = 103;
    public static final int CONTENT_TYPE_VIDEO = 104;
    public static final int CONTENT_TYPE_FILE = 105;
    public static final int CONTENT_TYPE_LOCATION = 106;
    public static final int CONTENT_TYPE_CUSTOM = 200;

    public static final int SESSION_TYPE_SINGLE = 1;
    public static final int SESSION_TYPE_GROUP = 2;
    public static final int SESSION_TYPE_NOTIFICATION = 3;

    public static final int MSG_STATUS_SENDING = 1;
    public static final int MSG_STATUS_SUCCESS = 2;
    public static final int MSG_STATUS_FAILED = 3;

    public static final int PLATFORM_IOS = 1;
    public static final int PLATFORM_ANDROID = 2;
    public static final int PLATFORM_WINDOWS = 3;
    public static final int PLATFORM_OSX = 4;
    public static final int PLATFORM_WEB = 5;
    public static final int PLATFORM_MINI_WEB = 6;
    public static final int PLATFORM_LINUX = 7;

    public static final String REDIS_KEY_USER_TOKEN = "cheese_im:user:token:";
    public static final String REDIS_KEY_USER_ONLINE = "cheese_im:user:online:";
    public static final String REDIS_KEY_USER_SESSION = "cheese_im:user:session:";
    public static final String REDIS_KEY_MSG_SEQ = "cheese_im:msg:seq:";

    public static final int ERR_CODE_SUCCESS = 0;
    public static final int ERR_CODE_INVALID_PARAM = 1001;
    public static final int ERR_CODE_USER_NOT_FOUND = 1002;
    public static final int ERR_CODE_TOKEN_INVALID = 1003;
    public static final int ERR_CODE_MSG_SEND_FAILED = 1004;
    public static final int ERR_CODE_INTERNAL_ERROR = 1005;

    private MessageConstants() {
    }
}
