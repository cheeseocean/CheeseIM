package com.cheeseocean.im.common.constants;

/**
 * 消息相关常量
 * 
 * @author CheeseIM
 */
public class MessageConstants {
    
    // 消息类型
    public static final int CONTENT_TYPE_TEXT = 101;      // 文本消息
    public static final int CONTENT_TYPE_IMAGE = 102;     // 图片消息
    public static final int CONTENT_TYPE_VOICE = 103;     // 语音消息
    public static final int CONTENT_TYPE_VIDEO = 104;     // 视频消息
    public static final int CONTENT_TYPE_FILE = 105;      // 文件消息
    public static final int CONTENT_TYPE_LOCATION = 106;  // 位置消息
    public static final int CONTENT_TYPE_CUSTOM = 200;    // 自定义消息
    
    // 会话类型
    public static final int SESSION_TYPE_SINGLE = 1;      // 单聊
    public static final int SESSION_TYPE_GROUP = 2;       // 群聊
    public static final int SESSION_TYPE_NOTIFICATION = 3; // 通知
    
    // 消息状态
    public static final int MSG_STATUS_SENDING = 1;       // 发送中
    public static final int MSG_STATUS_SUCCESS = 2;       // 发送成功
    public static final int MSG_STATUS_FAILED = 3;        // 发送失败
    
    // 平台类型
    public static final int PLATFORM_IOS = 1;             // iOS
    public static final int PLATFORM_ANDROID = 2;         // Android
    public static final int PLATFORM_WINDOWS = 3;         // Windows
    public static final int PLATFORM_OSX = 4;             // OSX
    public static final int PLATFORM_WEB = 5;             // Web
    public static final int PLATFORM_MINI_WEB = 6;        // MiniWeb
    public static final int PLATFORM_LINUX = 7;           // Linux
    
    // Redis Key 前缀
    public static final String REDIS_KEY_USER_TOKEN = "cheese_im:user:token:";
    public static final String REDIS_KEY_USER_ONLINE = "cheese_im:user:online:";
    public static final String REDIS_KEY_USER_SESSION = "cheese_im:user:session:";
    public static final String REDIS_KEY_MSG_SEQ = "cheese_im:msg:seq:";
    
    // 错误码
    public static final int ERR_CODE_SUCCESS = 0;
    public static final int ERR_CODE_INVALID_PARAM = 1001;
    public static final int ERR_CODE_USER_NOT_FOUND = 1002;
    public static final int ERR_CODE_TOKEN_INVALID = 1003;
    public static final int ERR_CODE_MSG_SEND_FAILED = 1004;
    public static final int ERR_CODE_INTERNAL_ERROR = 1005;
    
    private MessageConstants() {
        // 私有构造函数，防止实例化
    }
}
