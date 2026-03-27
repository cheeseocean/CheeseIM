package com.cheeseocean.im.common.api.dto.user;

/**
 * 用户基础信息 DTO。
 * 用于跨模块 Dubbo 调用传输，不暴露 MongoDB 文档细节。
 */
public class UserInfoDTO {

    /** 用户 ID */
    private String userId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String faceUrl;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /**
     * 管理员级别。
     * 0=普通用户，1=超级管理员，2+=通知/系统账号
     */
    private int appManagerLevel;

    /** 创建时间（毫秒时间戳） */
    private long createTime;

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

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
