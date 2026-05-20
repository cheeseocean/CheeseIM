package com.cheeseocean.im.common.core.notification;

import com.cheeseocean.im.common.api.dto.message.OfflinePushInfo;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ChatType;

import java.util.EnumMap;
import java.util.Map;

import static com.cheeseocean.im.common.api.enums.ContentType.*;

/**
 * 通知规则注册表。
 *
 * <p>集中维护通知内容类型与会话类型、可靠性和推送策略之间的映射关系。
 *
 * @author xxxcrel
 */
public final class NotificationRules {

    private static final Map<ContentType, NotificationRule> RULES = createDefaults();

    private NotificationRules() {
    }

    /**
     * 根据内容类型获取通知规则；缺省时回退为“作为单聊系统消息发送”。
     */
    public static NotificationRule get(ContentType contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("contentType required");
        }
        return RULES.getOrDefault(contentType, defaultSingleRule(contentType));
    }

    private static Map<ContentType, NotificationRule> createDefaults() {
        EnumMap<ContentType, NotificationRule> rules = new EnumMap<>(ContentType.class);
        rules.put(
                ContentType.SYSTEM_NOTIFY,
                new NotificationRule(
                        ContentType.SYSTEM_NOTIFY,
                        ChatType.NOTIFICATION,
                        false,
                        NotificationReliabilityLevel.RELIABLE_NO_MSG,
                        false,
                        true,
                        true,
                        offlinePushTemplate("System notifications", "You have a new system notification")
                )
        );
        rules.put(
                ContentType.FORCE_LOGOUT,
                new NotificationRule(
                        ContentType.FORCE_LOGOUT,
                        ChatType.NOTIFICATION,
                        false,
                        NotificationReliabilityLevel.UNRELIABLE,
                        false,
                        true,
                        false,
                        null
                )
        );
        rules.put(
                ContentType.REVOKE_NOTIFY,
                new NotificationRule(
                        ContentType.REVOKE_NOTIFY,
                        ChatType.PRIVATE,
                        true,
                        NotificationReliabilityLevel.RELIABLE_WITH_MSG,
                        false,
                        true,
                        false,
                        null
                )
        );
        rules.put(
                ContentType.READ_RECEIPT,
                new NotificationRule(
                        ContentType.READ_RECEIPT,
                        ChatType.PRIVATE,
                        false,
                        NotificationReliabilityLevel.UNRELIABLE,
                        false,
                        true,
                        false,
                        null
                )
        );
        rules.putAll(createFriendDefaults());
        return rules;
    }

    private static NotificationRule defaultSingleRule(ContentType contentType) {
        return new NotificationRule(
                contentType,
                ChatType.PRIVATE,
                true,
                NotificationReliabilityLevel.RELIABLE_WITH_MSG,
                true,
                true,
                false,
                null
        );
    }

    private static Map<ContentType, NotificationRule> createFriendDefaults() {
        Map<ContentType, NotificationRule> rules = new java.util.LinkedHashMap<>();
        rules.put(
                FRIEND_REQUEST,
                friendRule("New friend request", "You have a new friend request")
        );
        rules.put(
                FRIEND_REQUEST_ACCEPTED,
                friendRule("Friend request accepted", "Your friend request has been accepted")
        );
        rules.put(
                FRIEND_REQUEST_REJECTED,
                friendRule("Friend request rejected", "Your friend request has been rejected")
        );
        rules.put(
                FRIEND_REQUEST_CANCELLED,
                friendRule("Friend request cancelled", "A friend request has been cancelled")
        );
        rules.put(
                FRIEND_DELETED,
                friendRule("Friend removed", "A friend relationship has been removed")
        );
        rules.put(
                FRIEND_REMARK_MODIFIED,
                friendRule("Friend remark updated", "Your friend's remark has been updated")
        );
        rules.put(
                ADDED_TO_BLACKLIST,
                friendRule("Blacklist updated", "A user has been added to your blacklist")
        );
        rules.put(
                REMOVED_FROM_BLACKLIST,
                friendRule("Blacklist updated", "A user has been removed from your blacklist")
        );
        rules.put(
                FRIEND_INFO_UPDATED,
                friendRule("Friend profile updated", "Your friend's profile has been changed")
        );
        return Map.copyOf(rules);
    }

    private static NotificationRule friendRule(String offlinePushTitle, String offlinePushDesc) {
        return new NotificationRule(
                ContentType.SYSTEM_NOTIFY,
                ChatType.PRIVATE,
                false,
                NotificationReliabilityLevel.RELIABLE_NO_MSG,
                false,
                true,
                true,
                offlinePushTemplate(offlinePushTitle, offlinePushDesc)
        );
    }

    private static OfflinePushInfo offlinePushTemplate(String title, String desc) {
        OfflinePushInfo info = new OfflinePushInfo();
        info.setTitle(title);
        info.setDesc(desc);
        return info;
    }
}
