package com.cheeseocean.im.common.api.business.domain;

import com.cheeseocean.im.common.api.enums.GroupStatusEnum;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.enums.NeedVerificationEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 群组领域对象。
 *
 * <p>使用枚举表达状态和类型，业务逻辑直接操作枚举而非魔法数字。
 * 群状态见 {@link GroupStatusEnum}，
 * 群类型见 {@link GroupTypeEnum}，
 * 入群验证方式见 {@link NeedVerificationEnum}。
 */
@Data
public class Group implements Serializable {

    /**
     * 群组唯一标识
     */
    private String               groupId;
    /**
     * 群名称
     */
    private String               groupName;
    /**
     * 群公告
     */
    private String               notification;
    /**
     * 群简介
     */
    private String               introduction;
    /**
     * 群头像 URL
     */
    private String               avatarUrl;
    /**
     * 扩展字段（JSON 字符串）
     */
    private String               ex;
    /**
     * 群状态：正常 / 已解散 / 已封禁
     */
    private GroupStatusEnum      status;
    /**
     * 创建者用户 ID
     */
    private String               creatorUserId;
    /**
     * 群类型：普通群（写扩散）/ 超级大群（读扩散）
     */
    private GroupTypeEnum        groupType;
    /**
     * 入群验证方式：无需验证 / 需要审批 / 禁止加入
     */
    private NeedVerificationEnum needVerification;
    /**
     * 是否允许查看成员资料。
     * 0=允许，1=禁止。
     */
    private int                  lookMemberInfo;
    /**
     * 是否允许加成员为好友。
     * 0=允许，1=禁止。
     */
    private int                  applyMemberFriend;
    /**
     * 最近更新公告的时间（毫秒时间戳）
     */
    private long                 notificationUpdateTime;
    /**
     * 最近更新公告的操作者 ID
     */
    private String               notificationUserId;
    /**
     * 创建时间（毫秒时间戳）
     */
    private long                 createTime;
    /**
     * 成员关系版本。
     *
     * <p>每次成员集合发生变化时单调递增；群消息权限校验与扩散任务使用同一版本，
     * 从而在重试期间读取稳定的成员快照。</p>
     */
    private long                 membershipVersion;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /**
     * 判断群当前是否处于正常可用状态。
     */
    public boolean isNormal() {
        return status == GroupStatusEnum.NORMAL;
    }

    /**
     * 判断群是否已经解散。
     */
    public boolean isDisbanded() {
        return status == GroupStatusEnum.DISBANDED;
    }

}
