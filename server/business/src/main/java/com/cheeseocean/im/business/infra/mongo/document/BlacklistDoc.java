package com.cheeseocean.im.business.infra.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户黑名单 MongoDB 持久化文档。集合：{@code blacklist}。
 */
@Document("blacklist")
@CompoundIndexes({
        @CompoundIndex(name = "uk_blacklist_pair", def = "{'ownerUserId': 1, 'blockUserId': 1}", unique = true)
})
public class BlacklistDoc {

    /** _id = "{ownerUserId}:{blockUserId}" */
    @Id
    private String id;

    @Indexed
    private String ownerUserId;

    private String blockUserId;
    private int addSource;
    private String operatorUserId;
    private String ex;
    private Long createdAt;

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

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
