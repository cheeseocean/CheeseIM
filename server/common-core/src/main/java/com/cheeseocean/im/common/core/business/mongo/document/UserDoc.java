package com.cheeseocean.im.common.core.business.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 用户基础信息 MongoDB 持久化文档。
 *
 * <p>集合名：{@code user}，userId 直接作为 _id。
 * 与领域对象 {@link com.cheeseocean.im.common.core.business.domain.User} 通过
 * {@link com.cheeseocean.im.common.core.business.mongo.impl.UserRepositoryImpl} 互相转换。
 */
@Document("user")
public class UserDoc {

    @Id
    private String userId;

    @Indexed
    private String nickname;

    private String faceUrl;
    private String ex;
    private int appManagerLevel;

    /** 全局免打扰设置，存储整数 code */
    private int globalRecvMsgOpt;

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

    public int getGlobalRecvMsgOpt() { return globalRecvMsgOpt; }
    public void setGlobalRecvMsgOpt(int globalRecvMsgOpt) { this.globalRecvMsgOpt = globalRecvMsgOpt; }

    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }
}
