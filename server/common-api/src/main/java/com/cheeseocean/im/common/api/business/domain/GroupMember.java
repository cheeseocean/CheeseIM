package com.cheeseocean.im.common.api.business.domain;

import com.cheeseocean.im.common.api.enums.GroupMemberRoleEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 群成员领域对象。
 *
 * <p>用枚举 {@link GroupMemberRoleEnum} 表达成员角色，
 * 提供 {@link #isMuted()} 和 {@link #isAdminOrOwner()} 业务方法。
 */
@Data
public class GroupMember implements Serializable {

    /**
     * 文档唯一标识（"{groupId}:{userId}"）
     */
    private String              id;
    /**
     * 所属群组 ID
     */
    private String              groupId;
    /**
     * 成员用户 ID
     */
    private String              userId;
    /**
     * 在群里的昵称（空时显示用户昵称）
     */
    private String              nickname;
    /**
     * 在群里的头像 URL
     */
    private String              avatarUrl;
    /**
     * 成员角色：普通成员 / 群主 / 管理员
     */
    private GroupMemberRoleEnum roleLevel;
    /**
     * 入群方式来源（业务方自定义）
     */
    private int                 joinSource;
    /**
     * 邀请人用户 ID（主动申请时为空）
     */
    private String              inviterUserId;
    /**
     * 执行此操作的操作者 ID
     */
    private String              operatorUserId;
    /**
     * 禁言到期时间（毫秒时间戳）。
     * 0 或小于当前时间表示未被禁言。
     */
    private long                muteEndTime;
    /**
     * 扩展字段（JSON 字符串）
     */
    private String              ex;
    /**
     * 入群时间（毫秒时间戳）
     */
    private long                joinTime;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /**
     * 判断成员当前是否仍处于禁言窗口内。
     */
    public boolean isMuted() {
        return muteEndTime > System.currentTimeMillis();
    }

    /**
     * 判断成员是否具备管理权限。
     */
    public boolean isAdminOrOwner() {
        return roleLevel != null && roleLevel.isAdminOrOwner();
    }

}
