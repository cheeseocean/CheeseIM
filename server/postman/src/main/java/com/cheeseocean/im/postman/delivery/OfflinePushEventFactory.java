package com.cheeseocean.im.postman.delivery;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.OfflinePushTriggerReason;
import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 离线推送事件的唯一构造入口，保证路由为空与在线投递失败补偿使用同一字段语义。
 */
@Component
public class OfflinePushEventFactory {

    public OfflinePushEvent create(String userId, Message message, OfflinePushTriggerReason reason) {
        OfflinePushEvent event = new OfflinePushEvent();
        event.setUserId(userId);
        event.setConversationId(ConversationIdUtil.buildConversationId(message));
        event.setSeq(message.getSeq());
        event.setServerMsgId(message.getServerMsgId());
        event.setSenderId(message.getSenderId());
        event.setSessionType(message.getChatType() == null ? null : message.getChatType().getCode());
        event.setContentType(message.getContentType() == null ? null : message.getContentType().getCode());
        event.setNotification(message.getOptions() != null
                && Boolean.TRUE.equals(message.getOptions().getNotification()));
        event.setTitle(message.getSenderId());
        event.setContent(message.getContent() == null
                ? null
                : new String(message.getContent(), StandardCharsets.UTF_8));
        event.setAttributes(message.getAttributes());
        event.setTriggerReason(reason);
        return event;
    }
}
