package com.cheeseocean.im.common.core.business.domain;

import com.cheeseocean.im.common.api.enums.HandleResultEnum;

/**
 * 好友申请领域对象。
 *
 * <p>每对用户之间最多保留一条申请记录，重新申请时覆盖旧记录。
 * 处理结果用 {@link HandleResultEnum} 枚举表达，避免魔法值。
 */
public class FriendRequest {

    /** 文档唯一标识（"{fromUserId}:{toUserId}"） */
    private String id;

    /** 发起申请的用户 ID */
    private String fromUserId;

    /** 接收申请的用户 ID */
    private String toUserId;

    /**
     * 处理结果。
     * PENDING=待处理，ACCEPTED=已同意，REJECTED=已拒绝。
     */
    private HandleResultEnum handleResult;

    /** 申请附言 */
    private String reqMsg;

    /** 实际处理者用户 ID */
    private String handlerUserId;

    /** 处理回复/原因 */
    private String handleMsg;

    /** 处理时间（毫秒时间戳），0 表示尚未处理 */
    private long handleTime;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /** 申请提交时间（毫秒时间戳） */
    private long createTime;

    /** 最近更新时间（毫秒时间戳） */
    private long updatedAt;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /** 是否为待处理状态 */
    public boolean isPending() {
        return handleResult == HandleResultEnum.PENDING;
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }

    public HandleResultEnum getHandleResult() { return handleResult; }
    public void setHandleResult(HandleResultEnum handleResult) { this.handleResult = handleResult; }

    public String getReqMsg() { return reqMsg; }
    public void setReqMsg(String reqMsg) { this.reqMsg = reqMsg; }

    public String getHandlerUserId() { return handlerUserId; }
    public void setHandlerUserId(String handlerUserId) { this.handlerUserId = handlerUserId; }

    public String getHandleMsg() { return handleMsg; }
    public void setHandleMsg(String handleMsg) { this.handleMsg = handleMsg; }

    public long getHandleTime() { return handleTime; }
    public void setHandleTime(long handleTime) { this.handleTime = handleTime; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
