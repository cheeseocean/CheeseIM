package com.cheeseocean.im.business.infra.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 群组 MongoDB 持久化文档。集合：{@code group}。
 * status / groupType / needVerification 存储整数 code。
 */
@Document("group")
public class GroupDoc {

    @Id
    private String groupId;

    @Indexed
    private String groupName;

    private String notification;
    private String introduction;
    private String faceUrl;
    private String ex;
    private int status;
    private String creatorUserId;
    private int groupType;
    private int needVerification;
    private int lookMemberInfo;
    private int applyMemberFriend;
    private long notificationUpdateTime;
    private String notificationUserId;
    private long createTime;

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

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(String creatorUserId) { this.creatorUserId = creatorUserId; }

    public int getGroupType() { return groupType; }
    public void setGroupType(int groupType) { this.groupType = groupType; }

    public int getNeedVerification() { return needVerification; }
    public void setNeedVerification(int needVerification) { this.needVerification = needVerification; }

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
