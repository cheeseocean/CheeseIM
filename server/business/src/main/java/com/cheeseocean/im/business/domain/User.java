package com.cheeseocean.im.business.domain;

/**
 * 用户领域对象。
 *
 * <p>表达用户核心业务属性，不依赖任何持久化框架。
 * 全局消息接收设置 {@link #globalRecvMsgOpt} 与用户身份信息合并存储。
 */
public class User {

    /** 用户唯一标识 */
    private String userId;

    /** 用户昵称 */
    private String nickname;

    /** 头像 URL */
    private String faceUrl;

    /** 业务扩展字段（JSON 字符串） */
    private String ex;

    /**
     * 管理员级别。
     * 0=普通用户，1=超管，≥2=通知/系统账号。
     */
    private int appManagerLevel;

    /**
     * 全局消息接收设置。
     * 0=正常接收，1=不收消息，2=收不提醒。
     * 取值见 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt}。
     */
    private int globalRecvMsgOpt;

    /** 注册时间（毫秒时间戳） */
    private long createTime;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /** 是否为管理员（含超管和通知账号） */
    public boolean isAdmin() {
        return appManagerLevel >= 1;
    }

    /** 是否为通知/系统账号 */
    public boolean isNotificationAccount() {
        return appManagerLevel >= 2;
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getFaceUrl() { return faceUrl; }
    public void setFaceUrl(String faceUrl) { this.faceUrl = faceUrl; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public int getAppManagerLevel() { return appManagerLevel; }
    public void setAppManagerLevel(int appManagerLevel) { this.appManagerLevel = appManagerLevel; }

    public int getGlobalRecvMsgOpt() { return globalRecvMsgOpt; }
    public void setGlobalRecvMsgOpt(int globalRecvMsgOpt) { this.globalRecvMsgOpt = globalRecvMsgOpt; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
