package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 消息内容类型枚举。
 *
 * <p>代码分段设计：
 * <ul>
 *   <li>100-199: 普通消息</li>
 *   <li>200-299: 好友相关通知</li>
 *   <li>300-399: 群组相关通知</li>
 *   <li>2000-2999: 系统消息/通知</li>
 *   <li>4000-4999: 其他</li>
 * </ul>
 *
 * @author xxxcrel
 */
public enum ContentType implements IEnum {
    // ========== 普通消息 ==========
    /** 文本消息。 */
    TEXT(101, "文本"),
    /** 图片消息。 */
    IMAGE(102, "图片"),
    /** 语音消息。 */
    VOICE(103, "语音"),
    /** 视频消息。 */
    VIDEO(104, "视频"),
    /** 文件消息。 */
    FILE(105, "文件"),
    /** 位置消息。 */
    LOCATION(106, "位置"),
    /** 自定义消息。 */
    CUSTOM(200, "自定义"),

    // ========== 好友相关通知 ==========
    /** 好友申请。 */
    FRIEND_REQUEST(201, "好友申请"),
    /** 好友申请已接受。 */
    FRIEND_REQUEST_ACCEPTED(202, "好友申请已接受"),
    /** 好友申请已拒绝。 */
    FRIEND_REQUEST_REJECTED(203, "好友申请已拒绝"),
    /**
     * 好友申请已取消
     */
    FRIEND_REQUEST_CANCELLED(204, "好友申请已拒绝"),
    /**
     * 好友备注已修改
     */
    FRIEND_REMARK_MODIFIED(205, "好友备注已修改"),
    /**
     * 好友信息已更新
     */
    FRIEND_INFO_UPDATED(206, "好友信息已更新"),
    /** 好友已删除。 */
    FRIEND_DELETED(207, "好友已删除"),
    /** 被加入黑名单提示。 */
    ADDED_TO_BLACKLIST(208, "被加入黑名单"),
    /** 对方将你移出黑名单。 */
    REMOVED_FROM_BLACKLIST(209, "被移出黑名单"),

    // ========== 群组相关通知 ==========
    /** 入群申请。 */
    GROUP_APPLICATION(301, "入群申请"),
    /** 入群申请已接受。 */
    GROUP_APPLICATION_ACCEPTED(302, "入群申请已接受"),
    /** 入群申请已拒绝。 */
    GROUP_APPLICATION_REJECTED(303, "入群申请已拒绝"),
    /** 被踢出群聊。 */
    KICKED_FROM_GROUP(304, "被踢出群聊"),
    /** 群聊解散通知。 */
    GROUP_DISMISSED(305, "群聊已解散"),
    /** 被设置为群管理员。 */
    PROMOTED_TO_ADMIN(306, "被设置为群管理员"),
    /** 被取消群管理员。 */
    DEMOTED_FROM_ADMIN(307, "被取消群管理员"),
    /** 群成员变更通知（进群/退群）。 */
    GROUP_MEMBER_CHANGED(308, "群成员变更"),

    // ========== 系统消息/通知 ==========
    /** 已读回执。 */
    READ_RECEIPT(2004, "已读回执"),
    /** 撤回通知。 */
    REVOKE_NOTIFY(2005, "撤回通知"),
    /** 正在输入提示。 */
    TYPING(4002, "正在输入"),
    /** 系统通知。 */
    SYSTEM_NOTIFY(7001, "系统通知"),
    /** 强制下线通知。 */
    FORCE_LOGOUT(7002, "强制下线");

    private final int code;
    private final String desc;

    ContentType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    /**
     * 是否为携带附件的富媒体消息（图片/语音/视频/文件）。
     * 此类消息的 content JSON 中含 {@code attachmentId} 字段，
     * 历史持久化时会同步写入 {@code attachment_metadata} 集合供附件鉴权点查。
     */
    public boolean hasAttachment() {
        return this == IMAGE || this == VOICE || this == VIDEO || this == FILE;
    }

    public static ContentType fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ContentType code: " + code));
    }
}
