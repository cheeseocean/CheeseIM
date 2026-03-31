package com.cheeseocean.im.common.core.business.domain;

/**
 * 好友关系领域对象。
 *
 * <p>好友关系采用双向写扩散：A-B 互为好友对应两条独立记录（A 视角、B 视角）。
 * 每条记录只对 ownerUserId 可见（如备注、置顶均为私有属性）。
 */
public class Friendship {

    /** 文档唯一标识（"{ownerUserId}:{friendUserId}"） */
    private String id;

    /** 关系所属者（"我"的视角） */
    private String ownerUserId;

    /** 好友的用户 ID */
    private String friendUserId;

    /** 好友备注名（仅对 ownerUserId 可见） */
    private String remark;

    /**
     * 加好友来源（业务方自定义，如 1=搜索，2=扫码，3=群内添加）。
     */
    private int addSource;

    /** 执行操作的操作者 ID（管理员代操作时与 ownerUserId 不同） */
    private String operatorUserId;

    /** 是否置顶该好友 */
    private boolean pinned;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /** 成为好友的时间（毫秒时间戳） */
    private long createdAt;

    // ── getters / setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getFriendUserId() { return friendUserId; }
    public void setFriendUserId(String friendUserId) { this.friendUserId = friendUserId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public int getAddSource() { return addSource; }
    public void setAddSource(int addSource) { this.addSource = addSource; }

    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
