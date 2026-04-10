package com.cheeseocean.im.common.core.notification;

import com.cheeseocean.im.common.api.dto.message.OfflinePushInfo;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.SessionType;

/**
 * 单种通知内容类型对应的发送规则。
 *
 * @author xxxcrel
 */
public record NotificationRule(
        ContentType contentType,
        SessionType sessionType,
        boolean sendAsMessage,
        NotificationReliabilityLevel reliabilityLevel,
        boolean unreadCount,
        boolean onlinePush,
        boolean offlinePush,
        OfflinePushInfo offlinePushInfoTemplate
) {
}
