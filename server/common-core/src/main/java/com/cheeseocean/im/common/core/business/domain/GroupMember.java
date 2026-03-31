package com.cheeseocean.im.common.core.business.domain;

import com.cheeseocean.im.common.core.enums.GroupMemberRoleEnum;

/**
 * 群成员领域对象。
 *
 * <p>用枚举 {@link GroupMemberRoleEnum} 表达成员角色，
 * 提供 {@link #isMuted()} 和 {@link #isAdminOrOwner()} 业务方法。
 */
public class GroupMember {

    /** 文档唯一标识（"{groupId}:{userId}"） */
    private String id;

    /** 所属群组 ID */
    private String groupId;

    /** 成员用户 ID */
    private String userId;

    /** 在群里的昵称（空时显示用户昵称） */
    private String nickname;

    /** 在群里的头像 URL */
    private String faceUrl;

    /** 成员角色：普通成员 / 群主 / 管理员 */
    private GroupMemberRoleEnum roleLevel;

    /** 入群方式来源（业务方自定义） */
    private int joinSource;

    /** 邀请人用户 ID（主动申请时为空） */
    private String inviterUserId;

    /** 执行此操作的操作者 ID */
    private String operatorUserId;

    /**
     * 禁言到期时间（毫秒时间戳）。
     * 0 或小于当前时间表示未被禁言。
     */
    private long muteEndTime;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /** 入群时间（毫秒时间戳） */
    private long joinTime;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /**
     * 当前是否处于禁言状态。
     */
    public boolean isMuted() {
        return muteEndTime > System.currentTimeMillis();
    }

    /**
     * 是否为管理员或群主。
     */
    public boolean isAdminOrOwner() {
        return roleLevel != null && roleLevel.isAdminOrOwner();
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getFaceUrl() { return faceUrl; }
    public void setFaceUrl(String faceUrl) { this.faceUrl = faceUrl; }

    public GroupMemberRoleEnum getRoleLevel() { return roleLevel; }
    public void setRoleLevel(GroupMemberRoleEnum roleLevel) { this.roleLevel = roleLevel; }

    public int getJoinSource() { return joinSource; }
    public void setJoinSource(int joinSource) { this.joinSource = joinSource; }

    public String getInviterUserId() { return inviterUserId; }
    public void setInviterUserId(String inviterUserId) { this.inviterUserId = inviterUserId; }

    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }

    public long getMuteEndTime() { return muteEndTime; }
    public void setMuteEndTime(long muteEndTime) { this.muteEndTime = muteEndTime; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getJoinTime() { return joinTime; }
    public void setJoinTime(long joinTime) { this.joinTime = joinTime; }
}
