package com.cheeseocean.im.common.core.notification;

import com.cheeseocean.im.common.api.dto.message.OfflinePushInfo;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ChatType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 通知规则注册表。
 *
 * <p>集中维护通知内容类型与会话类型、可靠性和推送策略之间的映射关系。
 *
 * @author xxxcrel
 */
public final class NotificationRules {

    private static final Map<ContentType, NotificationRule> RULES = createDefaults();
    private static final Map<String, NotificationRule> FRIEND_RULES = createFriendDefaults();

    public static final String FRIEND_REQUEST_CREATED = "friend_request_created";
    public static final String FRIEND_REQUEST_ACCEPTED = "friend_request_accepted";
    public static final String FRIEND_REQUEST_REJECTED = "friend_request_rejected";
    public static final String FRIEND_REQUEST_CANCELLED = "friend_request_cancelled";
    public static final String FRIEND_DELETED = "friend_deleted";
    public static final String FRIEND_REMARK_SET = "friend_remark_set";
    public static final String BLACK_ADDED = "black_added";
    public static final String BLACK_DELETED = "black_deleted";
    public static final String FRIEND_INFO_UPDATED = "friend_info_updated";

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

    /**
     * 根据内容类型和业务通知类型获取规则。
     *
     * <p>好友通知虽然当前仍使用 {@link ContentType#SYSTEM_NOTIFY} 承载，
     * 但不同 notificationType 在会话类型、离线推送文案上存在差异。
     */
    public static NotificationRule get(ContentType contentType, String notificationType) {
        if (contentType == null) {
            throw new IllegalArgumentException("contentType required");
        }
        if (contentType == ContentType.SYSTEM_NOTIFY && notificationType != null && !notificationType.isBlank()) {
            NotificationRule friendRule = FRIEND_RULES.get(notificationType);
            if (friendRule != null) {
                return friendRule;
            }
        }
        return get(contentType);
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

    private static Map<String, NotificationRule> createFriendDefaults() {
        Map<String, NotificationRule> rules = new java.util.LinkedHashMap<>();
        rules.put(
                FRIEND_REQUEST_CREATED,
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
                FRIEND_REMARK_SET,
                friendRule("Friend remark updated", "Your friend's remark has been updated")
        );
        rules.put(
                BLACK_ADDED,
                friendRule("Blacklist updated", "A user has been added to your blacklist")
        );
        rules.put(
                BLACK_DELETED,
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
