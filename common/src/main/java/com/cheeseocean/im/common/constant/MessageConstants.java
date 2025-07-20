package com.cheeseocean.im.common.constant;

/**
 * 消息相关常量定义
 * 参照OpenIM的消息类型和状态定义
 * 
 * @author CheeseIM
 */
public class MessageConstants {
    
    /**
     * 消息内容类型
     */
    public static class ContentType {
        /** 文本消息 */
        public static final int TEXT = 101;
        /** 图片消息 */
        public static final int IMAGE = 102;
        /** 语音消息 */
        public static final int VOICE = 103;
        /** 视频消息 */
        public static final int VIDEO = 104;
        /** 文件消息 */
        public static final int FILE = 105;
        /** 位置消息 */
        public static final int LOCATION = 106;
        /** 名片消息 */
        public static final int CARD = 107;
        /** 表情消息 */
        public static final int EMOJI = 108;
        /** 红包消息 */
        public static final int RED_PACKET = 109;
        /** 转账消息 */
        public static final int TRANSFER = 110;
        /** 链接消息 */
        public static final int LINK = 111;
        /** 音乐消息 */
        public static final int MUSIC = 112;
        /** 小程序消息 */
        public static final int MINI_PROGRAM = 113;
        
        /** 系统通知 */
        public static final int SYSTEM_NOTIFICATION = 1001;
        /** 好友申请 */
        public static final int FRIEND_REQUEST = 1002;
        /** 群邀请 */
        public static final int GROUP_INVITE = 1003;
        /** 群公告 */
        public static final int GROUP_ANNOUNCEMENT = 1004;
        /** 群成员变更 */
        public static final int GROUP_MEMBER_CHANGE = 1005;
        
        /** 撤回消息 */
        public static final int REVOKE = 2101;
        /** 已读回执 */
        public static final int READ_RECEIPT = 2102;
        /** 正在输入 */
        public static final int TYPING = 2103;
    }
    
    /**
     * 会话类型 - 参照OpenIM的SessionType定义
     */
    public static class SessionType {
        /** 单聊 */
        public static final int SINGLE_CHAT_TYPE = 1;
        /** 写群聊（发送群消息） */
        public static final int WRITE_GROUP_CHAT_TYPE = 2;
        /** 读群聊（接收群消息，用于推送控制） */
        public static final int READ_GROUP_CHAT_TYPE = 3;
        /** 通知类型 */
        public static final int NOTIFICATION_CHAT_TYPE = 4;
    }
    
    /**
     * 消息状态
     */
    public static class Status {
        /** 发送中 */
        public static final int SENDING = 1;
        /** 发送成功 */
        public static final int SENT = 2;
        /** 发送失败 */
        public static final int FAILED = 3;
        /** 已读 */
        public static final int READ = 4;
        /** 已撤回 */
        public static final int REVOKED = 5;
    }
    
    /**
     * 消息来源
     */
    public static class MsgFrom {
        /** 用户消息 */
        public static final int USER = 100;
        /** 系统消息 */
        public static final int SYSTEM = 200;
        /** 管理员消息 */
        public static final int ADMIN = 300;
    }
    
    /**
     * 平台ID
     */
    public static class Platform {
        /** iOS */
        public static final int IOS = 1;
        /** Android */
        public static final int ANDROID = 2;
        /** Web */
        public static final int WEB = 3;
        /** Windows */
        public static final int WINDOWS = 4;
        /** Mac */
        public static final int MAC = 5;
        /** Linux */
        public static final int LINUX = 6;
        /** iPad */
        public static final int IPAD = 7;
        /** Android Pad */
        public static final int ANDROID_PAD = 8;
        /** 小程序 */
        public static final int MINI_PROGRAM = 9;
    }
    
    /**
     * 推送优先级
     */
    public static class PushPriority {
        /** 低优先级 */
        public static final int LOW = 0;
        /** 正常优先级 */
        public static final int NORMAL = 1;
        /** 高优先级 */
        public static final int HIGH = 2;
    }
    
    /**
     * 推送类型
     */
    public static class PushType {
        /** 文本推送 */
        public static final int TEXT = 1;
        /** 图片推送 */
        public static final int IMAGE = 2;
        /** 语音推送 */
        public static final int VOICE = 3;
        /** 视频推送 */
        public static final int VIDEO = 4;
        /** 文件推送 */
        public static final int FILE = 5;
        /** 位置推送 */
        public static final int LOCATION = 6;
        /** 自定义推送 */
        public static final int CUSTOM = 7;
    }
    
    /**
     * 获取内容类型描述
     */
    public static String getContentTypeDesc(Integer contentType) {
        if (contentType == null) {
            return "未知";
        }
        
        switch (contentType) {
            case ContentType.TEXT: return "文本";
            case ContentType.IMAGE: return "图片";
            case ContentType.VOICE: return "语音";
            case ContentType.VIDEO: return "视频";
            case ContentType.FILE: return "文件";
            case ContentType.LOCATION: return "位置";
            case ContentType.CARD: return "名片";
            case ContentType.EMOJI: return "表情";
            case ContentType.RED_PACKET: return "红包";
            case ContentType.TRANSFER: return "转账";
            case ContentType.LINK: return "链接";
            case ContentType.MUSIC: return "音乐";
            case ContentType.MINI_PROGRAM: return "小程序";
            case ContentType.SYSTEM_NOTIFICATION: return "系统通知";
            case ContentType.FRIEND_REQUEST: return "好友申请";
            case ContentType.GROUP_INVITE: return "群邀请";
            case ContentType.GROUP_ANNOUNCEMENT: return "群公告";
            case ContentType.GROUP_MEMBER_CHANGE: return "群成员变更";
            case ContentType.REVOKE: return "撤回消息";
            case ContentType.READ_RECEIPT: return "已读回执";
            case ContentType.TYPING: return "正在输入";
            default: return "未知类型(" + contentType + ")";
        }
    }
    
    /**
     * 获取会话类型描述
     */
    public static String getSessionTypeDesc(Integer sessionType) {
        if (sessionType == null) {
            return "未知";
        }

        switch (sessionType) {
            case SessionType.SINGLE_CHAT_TYPE: return "单聊";
            case SessionType.WRITE_GROUP_CHAT_TYPE: return "写群聊";
            case SessionType.READ_GROUP_CHAT_TYPE: return "读群聊";
            case SessionType.NOTIFICATION_CHAT_TYPE: return "通知";
            default: return "未知类型(" + sessionType + ")";
        }
    }
    
    /**
     * 获取平台描述
     */
    public static String getPlatformDesc(Integer platformID) {
        if (platformID == null) {
            return "未知";
        }
        
        switch (platformID) {
            case Platform.IOS: return "iOS";
            case Platform.ANDROID: return "Android";
            case Platform.WEB: return "Web";
            case Platform.WINDOWS: return "Windows";
            case Platform.MAC: return "Mac";
            case Platform.LINUX: return "Linux";
            case Platform.IPAD: return "iPad";
            case Platform.ANDROID_PAD: return "Android Pad";
            case Platform.MINI_PROGRAM: return "小程序";
            default: return "未知平台(" + platformID + ")";
        }
    }
    
    /**
     * 检查是否为系统消息
     */
    public static boolean isSystemMessage(Integer contentType) {
        return contentType != null && contentType >= 1000 && contentType < 2000;
    }
    
    /**
     * 检查是否为控制消息（不需要推送）
     */
    public static boolean isControlMessage(Integer contentType) {
        return contentType != null && contentType >= 2100;
    }
    
    /**
     * 检查是否需要推送
     */
    public static boolean needPush(Integer contentType) {
        return contentType != null && !isControlMessage(contentType);
    }
}
