package com.cheeseocean.im.common.core.business.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 好友关系 MongoDB 持久化文档。集合：{@code friendships}。
 */
@Document("friendships")
@CompoundIndexes({
        @CompoundIndex(name = "uk_friendship_pair", def = "{'ownerUserId': 1, 'friendUserId': 1}", unique = true)
})
public class FriendshipDoc {

    @Id
    private String id;

    @Indexed
    private String ownerUserId;

    @Indexed
    private String friendUserId;

    private String remark;
    private int addSource;
    private String operatorUserId;
    private boolean isPinned;
    private String ex;
    private Long createdAt;

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

    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
