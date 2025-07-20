package com.cheeseocean.im.push.util;

import com.cheeseocean.im.common.constant.MessageConstants;
import com.cheeseocean.im.common.constant.OptionConstants;
import com.cheeseocean.im.common.entity.Message;

/**
 * 推送内容生成器
 * 根据消息类型和会话类型生成合适的推送标题和内容
 * 
 * @author CheeseIM
 */
public class PushContentGenerator {
    
    /**
     * 生成推送标题
     * 
     * @param message 原始消息
     * @param groupName 群组名称（可选）
     * @return 推送标题
     */
    public static String generateTitle(Message message, String groupName) {
        if (message == null) {
            return "新消息";
        }
        
        Integer sessionType = message.getSessionType();
        if (sessionType == null) {
            return "新消息";
        }
        
        switch (sessionType) {
            case MessageConstants.SessionType.SINGLE_CHAT_TYPE: // 单聊
                return message.getSenderNickname() != null ? message.getSenderNickname() : "用户";

            case MessageConstants.SessionType.WRITE_GROUP_CHAT_TYPE: // 写群聊
                if (groupName != null && !groupName.trim().isEmpty()) {
                    return groupName;
                } else if (message.getGroupID() != null) {
                    return "群聊"; // 可以根据需要获取群名称
                } else {
                    return "群聊";
                }

            case MessageConstants.SessionType.READ_GROUP_CHAT_TYPE: // 读群聊
                return "群聊消息";

            case MessageConstants.SessionType.NOTIFICATION_CHAT_TYPE: // 通知
                return "系统通知";

            default:
                return "新消息";
        }
    }
    
    /**
     * 生成推送内容
     * 
     * @param message 原始消息
     * @return 推送内容
     */
    public static String generateContent(Message message) {
        if (message == null) {
            return "您有新消息";
        }
        
        Integer contentType = message.getContentType();
        String content = message.getContent();
        
        // 如果有文本内容，优先使用
        if (content != null && !content.trim().isEmpty()) {
            // 根据会话类型添加前缀
            if (message.getSessionType() == MessageConstants.SessionType.WRITE_GROUP_CHAT_TYPE ||
                message.getSessionType() == MessageConstants.SessionType.READ_GROUP_CHAT_TYPE) { // 群聊
                String senderNickname = message.getSenderNickname();
                if (senderNickname != null && !senderNickname.trim().isEmpty()) {
                    return senderNickname + ": " + limitContentLength(content);
                }
            }
            return limitContentLength(content);
        }
        
        // 根据消息类型生成默认内容
        if (contentType != null) {
            String typeContent = getContentByType(contentType);
            
            // 群聊消息添加发送者前缀
            if (message.getSessionType() == MessageConstants.SessionType.WRITE_GROUP_CHAT_TYPE ||
                message.getSessionType() == MessageConstants.SessionType.READ_GROUP_CHAT_TYPE) {
                String senderNickname = message.getSenderNickname();
                if (senderNickname != null && !senderNickname.trim().isEmpty()) {
                    return senderNickname + ": " + typeContent;
                }
            }
            
            return typeContent;
        }
        
        return "您有新消息";
    }
    
    /**
     * 根据消息类型获取默认内容
     */
    private static String getContentByType(Integer contentType) {
        switch (contentType) {
            case MessageConstants.ContentType.TEXT: return "发送了一条文本消息";
            case MessageConstants.ContentType.IMAGE: return "[图片]";
            case MessageConstants.ContentType.VOICE: return "[语音]";
            case MessageConstants.ContentType.VIDEO: return "[视频]";
            case MessageConstants.ContentType.FILE: return "[文件]";
            case MessageConstants.ContentType.LOCATION: return "[位置]";
            case MessageConstants.ContentType.CARD: return "[名片]";
            case MessageConstants.ContentType.EMOJI: return "[表情]";
            case MessageConstants.ContentType.RED_PACKET: return "[红包]";
            case MessageConstants.ContentType.TRANSFER: return "[转账]";
            case MessageConstants.ContentType.LINK: return "[链接]";
            case MessageConstants.ContentType.MUSIC: return "[音乐]";
            case MessageConstants.ContentType.MINI_PROGRAM: return "[小程序]";

            // 系统消息类型
            case MessageConstants.ContentType.SYSTEM_NOTIFICATION: return "系统通知";
            case MessageConstants.ContentType.FRIEND_REQUEST: return "好友申请";
            case MessageConstants.ContentType.GROUP_INVITE: return "群邀请";
            case MessageConstants.ContentType.GROUP_ANNOUNCEMENT: return "群公告";
            case MessageConstants.ContentType.GROUP_MEMBER_CHANGE: return "群成员变更";

            // 撤回和已读消息
            case MessageConstants.ContentType.REVOKE: return "撤回了一条消息";
            case MessageConstants.ContentType.READ_RECEIPT: return "消息已读";
            case MessageConstants.ContentType.TYPING: return "正在输入...";

            default: return "新消息";
        }
    }
    
    /**
     * 限制内容长度
     */
    private static String limitContentLength(String content) {
        if (content == null) {
            return "";
        }
        
        // 移除换行符和多余空格
        content = content.replaceAll("\\s+", " ").trim();
        
        // 限制长度
        int maxLength = 100;
        if (content.length() > maxLength) {
            return content.substring(0, maxLength - 3) + "...";
        }
        
        return content;
    }
    
    /**
     * 生成推送摘要（用于通知栏显示）
     * 
     * @param message 原始消息
     * @param groupName 群组名称（可选）
     * @return 推送摘要
     */
    public static String generateSummary(Message message, String groupName) {
        if (message == null) {
            return "您有新消息";
        }
        
        String title = generateTitle(message, groupName);
        String content = generateContent(message);
        
        // 单聊直接返回内容
        if (message.getSessionType() == MessageConstants.SessionType.SINGLE_CHAT_TYPE) {
            return content;
        }
        
        // 群聊和其他类型返回 "标题: 内容"
        return title + ": " + content;
    }
    
    /**
     * 生成推送副标题（iOS使用）
     * 
     * @param message 原始消息
     * @return 推送副标题
     */
    public static String generateSubtitle(Message message) {
        if (message == null ||
            (message.getSessionType() != MessageConstants.SessionType.WRITE_GROUP_CHAT_TYPE &&
             message.getSessionType() != MessageConstants.SessionType.READ_GROUP_CHAT_TYPE)) {
            return null; // 只有群聊才有副标题
        }
        
        String senderNickname = message.getSenderNickname();
        if (senderNickname != null && !senderNickname.trim().isEmpty()) {
            return senderNickname;
        }
        
        return null;
    }
    
    /**
     * 检查消息是否需要推送
     *
     * @param message 消息
     * @return 是否需要推送
     */
    public static boolean shouldOfflinePush(Message message) {
        if (message == null) {
            return false;
        }
        return message.getOptions().getOrDefault(OptionConstants.OFFLINE_PUSH, false);
    }
    
    /**
     * 获取推送优先级
     * 
     * @param message 消息
     * @return 优先级 (0:低, 1:正常, 2:高)
     */
    public static int getPushPriority(Message message) {
        if (message == null) {
            return 1; // 默认正常优先级
        }
        
        Integer contentType = message.getContentType();
        if (contentType == null) {
            return 1;
        }
        
        // 系统消息高优先级
        if (MessageConstants.isSystemMessage(contentType)) {
            return MessageConstants.PushPriority.HIGH;
        }

        // 特殊消息类型
        switch (contentType) {
            case MessageConstants.ContentType.RED_PACKET: // 红包
            case MessageConstants.ContentType.TRANSFER: // 转账
                return MessageConstants.PushPriority.HIGH; // 高优先级
            case MessageConstants.ContentType.VOICE: // 语音
            case MessageConstants.ContentType.VIDEO: // 视频
                return MessageConstants.PushPriority.NORMAL; // 正常优先级
            default:
                return MessageConstants.PushPriority.NORMAL; // 默认正常优先级
        }
    }
    
    /**
     * 获取推送声音
     * 
     * @param message 消息
     * @return 声音文件名
     */
    public static String getPushSound(Message message) {
        if (message == null) {
            return "default";
        }
        
        Integer contentType = message.getContentType();
        if (contentType == null) {
            return "default";
        }
        
        // 根据消息类型设置不同声音
        switch (contentType) {
            case MessageConstants.ContentType.RED_PACKET: // 红包
                return "money.wav";
            case MessageConstants.ContentType.TRANSFER: // 转账
                return "money.wav";
            case MessageConstants.ContentType.FRIEND_REQUEST: // 好友申请
                return "friend_request.wav";
            case MessageConstants.ContentType.GROUP_INVITE: // 群邀请
                return "group_invite.wav";
            default:
                return "default";
        }
    }
}
