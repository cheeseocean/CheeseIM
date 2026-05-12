package com.cheeseocean.im.common.core.notification;

import com.cheeseocean.im.common.api.dto.message.OfflinePushInfo;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ContentType;

/**
 * 单种通知内容类型对应的发送规则。
 *
 * @author xxxcrel
 */
public record NotificationRule(
        ContentType contentType,
        ChatType chatType,
        boolean sendAsMessage,
        NotificationReliabilityLevel reliabilityLevel,
        boolean unreadCount,
        boolean onlinePush,
        boolean offlinePush,
        OfflinePushInfo offlinePushInfoTemplate
) {
}
