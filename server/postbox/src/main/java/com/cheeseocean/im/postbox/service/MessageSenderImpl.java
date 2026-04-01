package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.conversation.ConversationRecvOptService;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.api.user.UserSettingsService;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.RecvMsgOpt;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.core.util.IdGenerator;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class MessageSenderImpl implements MessageSender {

    private final IngressEventPublisher ingressEventPublisher;

    @DubboReference
    private FriendRelationService friendRelationService;

    @DubboReference
    private ConversationRecvOptService conversationRecvOptService;

    @DubboReference
    private UserSettingsService userSettingsService;

    public MessageSenderImpl(IngressEventPublisher ingressEventPublisher) {
        this.ingressEventPublisher = ingressEventPublisher;
    }

    @Override
    public SendMessageResp sendMessage(SendMessageReq req) {
        String conversationId = resolveConversationId(req);
        MessageOptions options = fillDefaultOptions(req);

        // Permission checks for single-chat non-notification messages only.
        if (requiresPermissionCheck(req, options)) {
            SendMessageResp blocked = checkSingleChatPermission(req, conversationId, options);
            if (blocked != null) {
                return blocked;
            }
        }

        String serverMsgId = IdGenerator.generateMsgId();

        IngressEvent event = new IngressEvent();
        event.setRequestId(req.getRequestId());
        event.setConversationId(conversationId);
        event.setClientMsgId(req.getClientMsgId());
        event.setServerMsgId(serverMsgId);
        event.setSenderId(req.getSenderId());
        event.setReceiverId(req.getRecvId());
        event.setGroupId(req.getGroupId());
        event.setSessionType(req.getSessionType());
        event.setContentType(req.getContentType());
        event.setContent(req.getContent());
        event.setSendTime(req.getSendTime() == null ? System.currentTimeMillis() : req.getSendTime());
        event.setOptions(options);
        event.setExt(req.getExt());
        ingressEventPublisher.publish(event);

        SendMessageResp resp = new SendMessageResp();
        resp.setAccepted(true);
        resp.setRequestId(req.getRequestId());
        resp.setConversationId(conversationId);
        resp.setClientMsgId(req.getClientMsgId());
        resp.setServerMsgId(serverMsgId);
        return resp;
    }

    /**
     * 权限检查仅对单聊非通知消息生效，通知消息和群消息直接跳过。
     */
    private boolean requiresPermissionCheck(SendMessageReq req, MessageOptions options) {
        if (Boolean.TRUE.equals(options.isNotification())) {
            return false;
        }
        SessionType sessionType = resolveSessionType(req.getSessionType());
        return sessionType == SessionType.SINGLE;
    }

    /**
     * 单聊消息发送权限校验：
     *
     *  1. 黑名单检查  — recvId 将 senderId 加入黑名单则直接拒绝。
     *  2. 全局 recvMsgOpt：
     *       NOT_RECEIVE (1)        → 直接拒绝
     *       RECEIVE_NOT_NOTIFY (2) → 关闭离线推送，继续投递
     *  3. 会话级 recvMsgOpt（全局通过后才检查）：
     *       NOT_RECEIVE (1)        → 直接拒绝（已读回执除外）
     *       RECEIVE_NOT_NOTIFY (2) → 关闭离线推送，继续投递
     *
     * 返回 null 表示消息可正常投递（options 可能已被修改）；
     * 返回非 null 表示消息需被丢弃。
     */
    private SendMessageResp checkSingleChatPermission(SendMessageReq req,
                                                       String conversationId,
                                                       MessageOptions options) {
        String senderId = req.getSenderId();
        String recvId   = req.getRecvId();

        // 1. 黑名单
        if (friendRelationService.isBlocked(senderId, recvId)) {
            return rejectedResp(req, conversationId);
        }

        // 2. 全局 recvMsgOpt
        RecvMsgOpt globalOpt = RecvMsgOpt.fromCode(userSettingsService.getGlobalRecvMsgOpt(recvId));
        if (globalOpt == RecvMsgOpt.NOT_RECEIVE) {
            return rejectedResp(req, conversationId);
        }
        if (globalOpt == RecvMsgOpt.RECEIVE_NOT_NOTIFY) {
            options.setNeedOfflinePush(false);
        }

        // 3. 会话级 recvMsgOpt
        RecvMsgOpt convOpt = RecvMsgOpt.fromCode(conversationRecvOptService.getRecvMsgOpt(recvId, conversationId));
        if (convOpt == RecvMsgOpt.NOT_RECEIVE) {
            // 已读回执绕过会话级屏蔽
            if (!isReadReceipt(req.getContentType())) {
                return rejectedResp(req, conversationId);
            }
        }
        if (convOpt == RecvMsgOpt.RECEIVE_NOT_NOTIFY) {
            options.setNeedOfflinePush(false);
        }

        return null;
    }

    private static boolean isReadReceipt(Integer contentType) {
        return isContentType(contentType, ContentType.READ_RECEIPT);
    }

    private static SendMessageResp rejectedResp(SendMessageReq req, String conversationId) {
        SendMessageResp resp = new SendMessageResp();
        resp.setAccepted(false);
        resp.setRequestId(req.getRequestId());
        resp.setConversationId(conversationId);
        resp.setClientMsgId(req.getClientMsgId());
        return resp;
    }

    private static String resolveConversationId(SendMessageReq req) {
        SessionType sessionType = resolveSessionType(req.getSessionType());
        if (sessionType == SessionType.GROUP) {
            return ConversationIdUtil.group(req.getGroupId());
        }
        if (sessionType == SessionType.NOTIFICATION) {
            return ConversationIdUtil.notification(req.getRecvId());
        }
        return ConversationIdUtil.single(req.getSenderId(), req.getRecvId());
    }

    private static MessageOptions fillDefaultOptions(SendMessageReq req) {
        MessageOptions options = req.getOptions() == null ? new MessageOptions() : req.getOptions();
        SessionType sessionType = resolveSessionType(req.getSessionType());
        Integer contentType = req.getContentType();

        applyDefault(options::isNeedHistory, options::setNeedHistory, defaultNeedHistory(contentType));
        applyDefault(options::isNeedConversation, options::setNeedConversation, defaultNeedConversation(contentType));
        applyDefault(options::isNeedUnreadCount, options::setNeedUnreadCount, defaultNeedUnreadCount(contentType, sessionType));
        applyDefault(options::isNeedOnlinePush, options::setNeedOnlinePush, defaultNeedOnlinePush(contentType));
        applyDefault(options::isNeedOfflinePush, options::setNeedOfflinePush, defaultNeedOfflinePush(contentType));
        applyDefault(options::isSenderSync, options::setSenderSync, defaultSenderSync(contentType, sessionType));
        applyDefault(options::isNotification, options::setNotification, defaultNotification(contentType, sessionType));
        applyDefault(options::isNeedLastMessage, options::setNeedLastMessage, defaultNeedLastMessage(contentType));
        return options;
    }

    private static SessionType resolveSessionType(Integer sessionType) {
        if (sessionType == null) {
            return SessionType.SINGLE;
        }
        try {
            return SessionType.fromCode(sessionType);
        } catch (IllegalArgumentException ex) {
            return SessionType.SINGLE;
        }
    }

    private static void applyDefault(java.util.function.Supplier<Boolean> getter,
                                     java.util.function.Consumer<Boolean> setter,
                                     boolean defaultValue) {
        if (getter.get() == null) {
            setter.accept(defaultValue);
        }
    }

    private static boolean defaultNeedHistory(Integer contentType) {
        if (isTyping(contentType) || isSilentNotification(contentType)) {
            return false;
        }
        return true;
    }

    private static boolean defaultNeedConversation(Integer contentType) {
        if (isTyping(contentType) || isSilentNotification(contentType)) {
            return false;
        }
        return true;
    }

    private static boolean defaultNeedUnreadCount(Integer contentType, SessionType sessionType) {
        if (isTyping(contentType) || isRevokeNotify(contentType) || isSilentNotification(contentType)) {
            return false;
        }
        return true;
    }

    private static boolean defaultNeedOnlinePush(Integer contentType) {
        return !isSilentNotification(contentType);
    }

    private static boolean defaultNeedOfflinePush(Integer contentType) {
        if (isTyping(contentType) || isRevokeNotify(contentType)
                || isNotificationContent(contentType)) {
            return false;
        }
        return true;
    }

    private static boolean defaultSenderSync(Integer contentType, SessionType sessionType) {
        if (isRevokeNotify(contentType)) {
            return true;
        }
        if (isNotificationContent(contentType)) {
            return false;
        }
        return sessionType == SessionType.SINGLE;
    }

    private static boolean defaultNotification(Integer contentType, SessionType sessionType) {
        return sessionType == SessionType.NOTIFICATION || isRevokeNotify(contentType) || isNotificationContent(contentType);
    }

    private static boolean defaultNeedLastMessage(Integer contentType) {
        if (isTyping(contentType) || isSilentNotification(contentType)) {
            return false;
        }
        return true;
    }

    private static boolean isRevokeNotify(Integer contentType) {
        return isContentType(contentType, ContentType.REVOKE_NOTIFY);
    }

    private static boolean isTyping(Integer contentType) {
        return isContentType(contentType, ContentType.TYPING);
    }

    private static boolean isNotificationContent(Integer contentType) {
        return isContentType(contentType, ContentType.SYSTEM_NOTIFY)
                || isContentType(contentType, ContentType.FORCE_LOGOUT);
    }

    private static boolean isSilentNotification(Integer contentType) {
        return isContentType(contentType, ContentType.FORCE_LOGOUT);
    }

    private static boolean isContentType(Integer contentType, ContentType expectedType) {
        if (contentType == null) {
            return false;
        }
        try {
            return ContentType.fromCode(contentType) == expectedType;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
