package com.cheeseocean.im.common.core.business.domain;

import com.cheeseocean.im.common.api.enums.HandleResultEnum;

/**
 * 入群申请领域对象。
 *
 * <p>记录用户主动申请加群或被邀请入群的完整审批流程。
 * 处理结果用 {@link HandleResultEnum} 枚举表达。
 */
public class GroupRequest {

    /** 文档唯一标识 */
    private String id;

    /** 申请人用户 ID */
    private String userId;

    /** 目标群组 ID */
    private String groupId;

    /** 处理结果：PENDING / ACCEPTED / REJECTED */
    private HandleResultEnum handleResult;

    /** 申请附言 */
    private String reqMsg;

    /** 处理说明 */
    private String handledMsg;

    /** 实际处理者用户 ID */
    private String handleUserId;

    /** 处理时间（毫秒时间戳），0 表示尚未处理 */
    private long handledTime;

    /** 申请来源方式（业务方自定义） */
    private int joinSource;

    /** 邀请人用户 ID（被动邀请时填写） */
    private String inviterUserId;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /** 申请提交时间（毫秒时间戳） */
    private long reqTime;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /** 是否为待处理状态 */
    public boolean isPending() {
        return handleResult == HandleResultEnum.PENDING;
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public HandleResultEnum getHandleResult() { return handleResult; }
    public void setHandleResult(HandleResultEnum handleResult) { this.handleResult = handleResult; }

    public String getReqMsg() { return reqMsg; }
    public void setReqMsg(String reqMsg) { this.reqMsg = reqMsg; }

    public String getHandledMsg() { return handledMsg; }
    public void setHandledMsg(String handledMsg) { this.handledMsg = handledMsg; }

    public String getHandleUserId() { return handleUserId; }
    public void setHandleUserId(String handleUserId) { this.handleUserId = handleUserId; }

    public long getHandledTime() { return handledTime; }
    public void setHandledTime(long handledTime) { this.handledTime = handledTime; }

    public int getJoinSource() { return joinSource; }
    public void setJoinSource(int joinSource) { this.joinSource = joinSource; }

    public String getInviterUserId() { return inviterUserId; }
    public void setInviterUserId(String inviterUserId) { this.inviterUserId = inviterUserId; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getReqTime() { return reqTime; }
    public void setReqTime(long reqTime) { this.reqTime = reqTime; }
}
