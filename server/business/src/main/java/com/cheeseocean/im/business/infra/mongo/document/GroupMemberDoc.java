package com.cheeseocean.im.business.infra.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 群成员 MongoDB 持久化文档。集合：{@code group_member}。
 * roleLevel 存储整数 code（1=成员，2=群主，3=管理员）。
 */
@Document("group_member")
@CompoundIndexes({
        @CompoundIndex(name = "uk_group_member", def = "{'groupId': 1, 'userId': 1}", unique = true),
        @CompoundIndex(name = "idx_group_role", def = "{'groupId': 1, 'roleLevel': 1}")
})
public class GroupMemberDoc {

    /** _id = "{groupId}:{userId}" */
    @Id
    private String id;

    @Indexed
    private String groupId;

    @Indexed
    private String userId;

    private String nickname;
    private String faceUrl;
    private int roleLevel;
    private int joinSource;
    private String inviterUserId;
    private String operatorUserId;
    private long muteEndTime;
    private String ex;
    private long joinTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getFaceUrl() { return faceUrl; }
    public void setFaceUrl(String faceUrl) { this.faceUrl = faceUrl; }

    public int getRoleLevel() { return roleLevel; }
    public void setRoleLevel(int roleLevel) { this.roleLevel = roleLevel; }

    public int getJoinSource() { return joinSource; }
    public void setJoinSource(int joinSource) { this.joinSource = joinSource; }

    public String getInviterUserId() { return inviterUserId; }
    public void setInviterUserId(String inviterUserId) { this.inviterUserId = inviterUserId; }

    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }

    public long getMuteEndTime() { return muteEndTime; }
    public void setMuteEndTime(long muteEndTime) { this.muteEndTime = muteEndTime; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getJoinTime() { return joinTime; }
    public void setJoinTime(long joinTime) { this.joinTime = joinTime; }
}
