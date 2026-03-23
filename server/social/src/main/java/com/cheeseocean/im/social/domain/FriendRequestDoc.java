package com.cheeseocean.im.social.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("friend_requests")
@CompoundIndexes({
        @CompoundIndex(name = "uk_friend_request_pair", def = "{'fromUserId': 1, 'toUserId': 1}", unique = true),
        @CompoundIndex(name = "idx_friend_request_incoming", def = "{'toUserId': 1, 'status': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_friend_request_outgoing", def = "{'fromUserId': 1, 'status': 1, 'updatedAt': -1}")
})
public class FriendRequestDoc {

    @Id
    private String id;

    @Indexed
    private String fromUserId;

    @Indexed
    private String toUserId;

    private String status;
    private String requestMessage;
    private Long createdAt;
    private Long updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestMessage() {
        return requestMessage;
    }

    public void setRequestMessage(String requestMessage) {
        this.requestMessage = requestMessage;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
