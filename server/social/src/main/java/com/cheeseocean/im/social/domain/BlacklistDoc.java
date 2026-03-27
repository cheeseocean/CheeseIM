package com.cheeseocean.im.social.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("blacklist")
@CompoundIndexes({
        @CompoundIndex(name = "uk_blacklist_pair", def = "{'userId': 1, 'targetUserId': 1}", unique = true)
})
public class BlacklistDoc {

    @Id
    private String id;  // userId:targetUserId

    @Indexed
    private String userId;

    private String targetUserId;

    private Long createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
