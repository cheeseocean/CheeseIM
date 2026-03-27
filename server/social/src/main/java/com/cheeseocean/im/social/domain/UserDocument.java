package com.cheeseocean.im.social.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 用户基础信息 MongoDB 文档。
 *
 * <p>集合名：{@code user}
 * 主键：userId（直接作为 _id，避免额外唯一索引）。
 *
 * <p>注意：globalRecvMsgOpt 存储在独立的 {@link UserSettingsDoc} 文档中，
 * 本文档只存用户身份信息。
 */
@Document("user")
public class UserDocument {

    /** 用户 ID，直接作为 MongoDB _id */
    @Id
    private String userId;

    /** 用户昵称 */
    @Indexed
    private String nickname;

    /** 头像 URL */
    private String faceUrl;

    /** 扩展字段（JSON 字符串） */
    private String ex;

    /**
     * 管理员级别。
     * 0=普通用户，1=超管，>=2 为通知/系统账号。
     */
    private int appManagerLevel;

    /** 创建时间 */
    private Instant createTime;

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

    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }
}
