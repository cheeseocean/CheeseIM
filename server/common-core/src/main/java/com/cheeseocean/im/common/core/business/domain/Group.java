package com.cheeseocean.im.common.core.business.domain;

import com.cheeseocean.im.common.core.enums.GroupStatusEnum;
import com.cheeseocean.im.common.core.enums.GroupTypeEnum;
import com.cheeseocean.im.common.core.enums.NeedVerificationEnum;

/**
 * 群组领域对象。
 *
 * <p>使用枚举表达状态和类型，业务逻辑直接操作枚举而非魔法数字。
 * 群状态见 {@link GroupStatusEnum}，
 * 群类型见 {@link GroupTypeEnum}，
 * 入群验证方式见 {@link NeedVerificationEnum}。
 */
public class Group {

    /** 群组唯一标识 */
    private String groupId;

    /** 群名称 */
    private String groupName;

    /** 群公告 */
    private String notification;

    /** 群简介 */
    private String introduction;

    /** 群头像 URL */
    private String faceUrl;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /** 群状态：正常 / 已解散 / 已封禁 */
    private GroupStatusEnum status;

    /** 创建者用户 ID */
    private String creatorUserId;

    /** 群类型：普通群（写扩散）/ 超级大群（读扩散） */
    private GroupTypeEnum groupType;

    /** 入群验证方式：无需验证 / 需要审批 / 禁止加入 */
    private NeedVerificationEnum needVerification;

    /**
     * 是否允许查看成员资料。
     * 0=允许，1=禁止。
     */
    private int lookMemberInfo;

    /**
     * 是否允许加成员为好友。
     * 0=允许，1=禁止。
     */
    private int applyMemberFriend;

    /** 最近更新公告的时间（毫秒时间戳） */
    private long notificationUpdateTime;

    /** 最近更新公告的操作者 ID */
    private String notificationUserId;

    /** 创建时间（毫秒时间戳） */
    private long createTime;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /** 是否正常运营 */
    public boolean isNormal() {
        return status == GroupStatusEnum.NORMAL;
    }

    /** 是否已解散 */
    public boolean isDisbanded() {
        return status == GroupStatusEnum.DISBANDED;
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getNotification() { return notification; }
    public void setNotification(String notification) { this.notification = notification; }

    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }

    public String getFaceUrl() { return faceUrl; }
    public void setFaceUrl(String faceUrl) { this.faceUrl = faceUrl; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public GroupStatusEnum getStatus() { return status; }
    public void setStatus(GroupStatusEnum status) { this.status = status; }

    public String getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(String creatorUserId) { this.creatorUserId = creatorUserId; }

    public GroupTypeEnum getGroupType() { return groupType; }
    public void setGroupType(GroupTypeEnum groupType) { this.groupType = groupType; }

    public NeedVerificationEnum getNeedVerification() { return needVerification; }
    public void setNeedVerification(NeedVerificationEnum needVerification) { this.needVerification = needVerification; }

    public int getLookMemberInfo() { return lookMemberInfo; }
    public void setLookMemberInfo(int lookMemberInfo) { this.lookMemberInfo = lookMemberInfo; }

    public int getApplyMemberFriend() { return applyMemberFriend; }
    public void setApplyMemberFriend(int applyMemberFriend) { this.applyMemberFriend = applyMemberFriend; }

    public long getNotificationUpdateTime() { return notificationUpdateTime; }
    public void setNotificationUpdateTime(long notificationUpdateTime) { this.notificationUpdateTime = notificationUpdateTime; }

    public String getNotificationUserId() { return notificationUserId; }
    public void setNotificationUserId(String notificationUserId) { this.notificationUserId = notificationUserId; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
