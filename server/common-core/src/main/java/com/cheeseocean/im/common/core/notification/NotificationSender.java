package com.cheeseocean.im.common.core.notification;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.OfflinePushInfo;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessageSource;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.core.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通知消息发送器。
 *
 * <p>该组件负责把任意通知载荷封装成 {@link Message}，
 * 并统一复用 {@link MessageSender} 的消息发送链路投递系统通知。
 *
 * @author xxxcrel
 */
@Component
public class NotificationSender {

    private final ObjectMapper objectMapper;

    @DubboReference
    private MessageSender messageSender;

    public NotificationSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 向单个用户发送通知会话消息。
     */
    public SendMessageResp sendToUser(String sendUserId,
                                      String recvUserId,
                                      ContentType contentType,
                                      Object payload) {
        return sendToUser(sendUserId, recvUserId, contentType, null, payload, null);
    }

    /**
     * 向单个用户发送带业务通知类型的通知消息。
     */
    public SendMessageResp sendToUser(String sendUserId,
                                      String recvUserId,
                                      ContentType contentType,
                                      String notificationType,
                                      Object payload,
                                      Map<String, String> attributes) {
        NotificationRule rule = NotificationRules.get(contentType, notificationType);
        return send(sendUserId, recvUserId, null, contentType, rule.chatType(), notificationType, payload, attributes);
    }

    /**
     * 向多个用户逐个发送通知会话消息。
     */
    public List<SendMessageResp> sendToUsers(String sendUserId,
                                             Collection<String> recvUserIds,
                                             ContentType contentType,
                                             Object payload) {
        return sendToUsers(sendUserId, recvUserIds, contentType, null, payload, null);
    }

    /**
     * 向多个用户逐个发送带业务通知类型的通知消息。
     */
    public List<SendMessageResp> sendToUsers(String sendUserId,
                                             Collection<String> recvUserIds,
                                             ContentType contentType,
                                             String notificationType,
                                             Object payload,
                                             Map<String, String> attributes) {
        if (recvUserIds == null || recvUserIds.isEmpty()) {
            return new ArrayList<>();
        }
        return recvUserIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(recvUserId -> sendToUser(sendUserId, recvUserId, contentType, notificationType, payload, attributes))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 按显式会话类型发送通知。
     *
     * <p>单聊/通知使用 {@code recvUserId}，群聊使用 {@code groupId}。
     */
    public SendMessageResp send(String sendUserId,
                                String recvUserId,
                                String groupId,
                                ContentType contentType,
                                ChatType chatType,
                                Object payload) {
        return send(sendUserId, recvUserId, groupId, contentType, chatType, null, payload, null);
    }

    /**
     * 按显式会话类型与业务通知类型发送通知。
     */
    public SendMessageResp send(String sendUserId,
                                String recvUserId,
                                String groupId,
                                ContentType contentType,
                                ChatType chatType,
                                String notificationType,
                                Object payload,
                                Map<String, String> attributes) {
        Message message = buildMessage(sendUserId, recvUserId, groupId, contentType, chatType, notificationType, payload, attributes);
        return messageSender.sendMessage(new SendMessageReq(message));
    }

    /**
     * 构造通知消息模型，交由统一消息发送链路补齐默认选项和后续投递。
     */
    private Message buildMessage(String sendUserId,
                                 String recvUserId,
                                 String groupId,
                                 ContentType contentType,
                                 ChatType chatType,
                                 String notificationType,
                                 Object payload,
                                 Map<String, String> attributes) {
        validateRouting(recvUserId, groupId, chatType);
        NotificationRule rule    = NotificationRules.get(contentType, notificationType);
        Message          message = new Message();
        long             now     = System.currentTimeMillis();
        message.setClientMsgId(IdGenerator.generateMsgId());
        message.setSenderId(sendUserId);
        message.setReceiverId(recvUserId);
        message.setGroupId(groupId);
        message.setContent(serializePayload(payload));
        message.setContentType(contentType);
        message.setChatType(chatType);
        message.setSendTime(now);
        message.setCreateTime(now);
        message.setPlatformType(PlatformType.UNKNOWN);
        message.setSource(MessageSource.SYSTEM);
        message.setOptions(buildOptions(rule, chatType));
        message.setOfflinePushInfo(copyOfflinePushInfo(rule.offlinePushInfoTemplate()));
        message.setAttributes(mergeAttributes(notificationType, attributes));
        return message;
    }

    private byte[] serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize notification payload", e);
        }
    }

    private void validateRouting(String recvUserId,
                                 String groupId,
                                 ChatType chatType) {
        if (chatType == ChatType.GROUP) {
            if (!StringUtils.hasText(groupId)) {
                throw new IllegalArgumentException("groupId required for group notification");
            }
            return;
        }
        if (!StringUtils.hasText(recvUserId)) {
            throw new IllegalArgumentException("recvUserId required for single/notification message");
        }
    }

    /**
     * 按通知规则构造消息选项。
     */
    private MessageOptions buildOptions(NotificationRule rule, ChatType actualChatType) {
        MessageOptions options       = new MessageOptions();
        boolean        sendAsMessage = rule.sendAsMessage();
        boolean        reliable      = rule.reliabilityLevel() != NotificationReliabilityLevel.UNRELIABLE;
        options.setNeedHistory(reliable);
        options.setNeedConversation(sendAsMessage);
        options.setNeedUnreadCount(sendAsMessage && rule.unreadCount());
        options.setNeedOnlinePush(rule.onlinePush());
        options.setNeedOfflinePush(rule.offlinePush());
        options.setSenderSync(actualChatType == ChatType.PRIVATE && sendAsMessage);
        options.setNotification(actualChatType == ChatType.NOTIFICATION || !sendAsMessage);
        options.setNeedLastMessage(sendAsMessage);
        return options;
    }

    private OfflinePushInfo copyOfflinePushInfo(OfflinePushInfo template) {
        if (template == null) {
            return null;
        }
        OfflinePushInfo copy = new OfflinePushInfo();
        copy.setTitle(template.getTitle());
        copy.setDesc(template.getDesc());
        copy.setEx(template.getEx());
        copy.setIOSPushSound(template.getIOSPushSound());
        copy.setIOSBadgeCount(template.getIOSBadgeCount());
        copy.setSignalInfo(template.getSignalInfo());
        copy.setPushExtras(template.getPushExtras());
        return copy;
    }

    private Map<String, String> mergeAttributes(String notificationType, Map<String, String> attributes) {
        if (!StringUtils.hasText(notificationType) && (attributes == null || attributes.isEmpty())) {
            return null;
        }
        Map<String, String> merged = new LinkedHashMap<>();
        if (attributes != null && !attributes.isEmpty()) {
            merged.putAll(attributes);
        }
        if (StringUtils.hasText(notificationType)) {
            merged.put("notificationType", notificationType);
        }
        return merged;
    }
}
