package com.cheeseocean.im.common.core.business.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 好友申请 MongoDB 持久化文档。集合：{@code friend_requests}。
 * handleResult 存储整数 code（0=待处理，1=已同意，-1=已拒绝）。
 */
@Document("friend_requests")
@CompoundIndexes({
        @CompoundIndex(name = "uk_friend_request_pair", def = "{'fromUserId': 1, 'toUserId': 1}", unique = true),
        @CompoundIndex(name = "idx_friend_request_incoming", def = "{'toUserId': 1, 'handleResult': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_friend_request_outgoing", def = "{'fromUserId': 1, 'handleResult': 1, 'updatedAt': -1}")
})
public class FriendRequestDoc {

    @Id
    private String id;

    @Indexed
    private String fromUserId;

    @Indexed
    private String toUserId;

    /** 0=待处理，1=已同意，-1=已拒绝 */
    private int handleResult;

    private String reqMsg;
    private String handlerUserId;
    private String handleMsg;
    private long handleTime;
    private String ex;
    private long createTime;
    private long updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }

    public int getHandleResult() { return handleResult; }
    public void setHandleResult(int handleResult) { this.handleResult = handleResult; }

    public String getReqMsg() { return reqMsg; }
    public void setReqMsg(String reqMsg) { this.reqMsg = reqMsg; }

    public String getHandlerUserId() { return handlerUserId; }
    public void setHandlerUserId(String handlerUserId) { this.handlerUserId = handlerUserId; }

    public String getHandleMsg() { return handleMsg; }
    public void setHandleMsg(String handleMsg) { this.handleMsg = handleMsg; }

    public long getHandleTime() { return handleTime; }
    public void setHandleTime(long handleTime) { this.handleTime = handleTime; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
