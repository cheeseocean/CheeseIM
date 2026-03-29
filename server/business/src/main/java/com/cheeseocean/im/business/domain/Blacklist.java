package com.cheeseocean.im.business.domain;

/**
 * 黑名单领域对象。
 *
 * <p>代表用户将某人加入黑名单的关系，单向持有（只影响 ownerUserId 一侧）。
 */
public class Blacklist {

    /** 文档唯一标识（"{ownerUserId}:{blockUserId}"） */
    private String id;

    /** 执行拉黑操作的用户 ID */
    private String ownerUserId;

    /** 被拉黑的用户 ID */
    private String blockUserId;

    /** 拉黑来源（业务方自定义，如 1=主动拉黑，2=举报触发） */
    private int addSource;

    /** 执行操作的操作者 ID（管理员代操作时与 ownerUserId 不同） */
    private String operatorUserId;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /** 拉黑时间（毫秒时间戳） */
    private long createdAt;

    // ── getters / setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getBlockUserId() { return blockUserId; }
    public void setBlockUserId(String blockUserId) { this.blockUserId = blockUserId; }

    public int getAddSource() { return addSource; }
    public void setAddSource(int addSource) { this.addSource = addSource; }

    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
