package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.SessionType;

/**
 * 封装消息发送默认选项判断逻辑，避免发送器内散落多组内容类型分支。
 */
public final class MessageOptionPolicy {

    private MessageOptionPolicy() {
    }

    /**
     * 基于消息内容和会话类型补齐默认选项。
     */
    public static MessageOptions fillDefaultOptions(Message message) {
        MessageOptions options = message.getOptions() == null ? new MessageOptions() : message.getOptions();
        SessionType sessionType = message.getSessionType();
        ContentType contentType = message.getContentType();

        applyDefault(options::getNeedHistory, options::setNeedHistory, defaultNeedHistory(contentType));
        applyDefault(options::getNeedConversation, options::setNeedConversation, defaultNeedConversation(contentType));
        applyDefault(options::getNeedUnreadCount, options::setNeedUnreadCount, defaultNeedUnreadCount(contentType, sessionType));
        applyDefault(options::getNeedOnlinePush, options::setNeedOnlinePush, defaultNeedOnlinePush(contentType));
        applyDefault(options::getNeedOfflinePush, options::setNeedOfflinePush, defaultNeedOfflinePush(contentType));
        applyDefault(options::getSenderSync, options::setSenderSync, defaultSenderSync(contentType, sessionType));
        applyDefault(options::getNotification, options::setNotification, defaultNotification(contentType, sessionType));
        applyDefault(options::getNeedLastMessage, options::setNeedLastMessage, defaultNeedLastMessage(contentType));
        return options;
    }

    private static void applyDefault(java.util.function.Supplier<Boolean> getter,
                                     java.util.function.Consumer<Boolean> setter,
                                     boolean defaultValue) {
        if (getter.get() == null) {
            setter.accept(defaultValue);
        }
    }

    private static boolean defaultNeedHistory(ContentType contentType) {
        return !isTyping(contentType) && !isSilentNotification(contentType);
    }

    private static boolean defaultNeedConversation(ContentType contentType) {
        return !isTyping(contentType) && !isSilentNotification(contentType);
    }

    private static boolean defaultNeedUnreadCount(ContentType contentType, SessionType sessionType) {
        return !isTyping(contentType) && !isRevokeNotify(contentType) && !isSilentNotification(contentType);
    }

    private static boolean defaultNeedOnlinePush(ContentType contentType) {
        return !isSilentNotification(contentType);
    }

    private static boolean defaultNeedOfflinePush(ContentType contentType) {
        return !isTyping(contentType) && !isRevokeNotify(contentType) && !isNotificationContent(contentType);
    }

    private static boolean defaultSenderSync(ContentType contentType, SessionType sessionType) {
        if (isRevokeNotify(contentType)) {
            return true;
        }
        if (isNotificationContent(contentType)) {
            return false;
        }
        return sessionType == SessionType.SINGLE;
    }

    private static boolean defaultNotification(ContentType contentType, SessionType sessionType) {
        return sessionType == SessionType.NOTIFICATION || isRevokeNotify(contentType) || isNotificationContent(contentType);
    }

    private static boolean defaultNeedLastMessage(ContentType contentType) {
        return !isTyping(contentType) && !isSilentNotification(contentType);
    }

    private static boolean isRevokeNotify(ContentType contentType) {
        return sameContentType(contentType, ContentType.REVOKE_NOTIFY);
    }

    private static boolean isTyping(ContentType contentType) {
        return sameContentType(contentType, ContentType.TYPING);
    }

    private static boolean isNotificationContent(ContentType contentType) {
        return sameContentType(contentType, ContentType.SYSTEM_NOTIFY)
                || sameContentType(contentType, ContentType.FORCE_LOGOUT);
    }

    private static boolean isSilentNotification(ContentType contentType) {
        return sameContentType(contentType, ContentType.FORCE_LOGOUT);
    }

    public static boolean isReadReceipt(ContentType contentType) {
        return sameContentType(contentType, ContentType.READ_RECEIPT);
    }

    private static boolean sameContentType(ContentType contentType, ContentType expectedType) {
        return contentType != null && contentType == expectedType;
    }
}
