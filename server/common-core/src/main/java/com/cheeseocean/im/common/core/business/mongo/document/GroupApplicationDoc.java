package com.cheeseocean.im.common.core.business.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 入群申请 MongoDB 持久化文档。集合：{@code group_request}。
 * handleResult 存储整数 code（0=待处理，1=已同意，-1=已拒绝）。
 */
@Document("group_request")
@CompoundIndexes({
        @CompoundIndex(name = "uk_group_request", def = "{'userId': 1, 'groupId': 1}", unique = true),
        @CompoundIndex(name = "idx_group_pending", def = "{'groupId': 1, 'handleResult': 1, 'reqTime': -1}")
})
public class GroupApplicationDoc {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String groupId;

    private int handleResult;
    private String reqMsg;
    private String handledMsg;
    private String handleUserId;
    private long handledTime;
    private int joinSource;
    private String inviterUserId;
    private String ex;
    private long reqTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public int getHandleResult() { return handleResult; }
    public void setHandleResult(int handleResult) { this.handleResult = handleResult; }

    public String getReqMsg() { return reqMsg; }
    public void setReqMsg(String reqMsg) { this.reqMsg = reqMsg; }

    public String getHandledMsg() { return handledMsg; }
    public void setHandledMsg(String handledMsg) { this.handledMsg = handledMsg; }

    public String getHandleUserId() { return handleUserId; }
    public void setHandleUserId(String handleUserId) { this.handleUserId = handleUserId; }

    public long getHandledTime() { return handledTime; }
    public void setHandledTime(long handledTime) { this.handledTime = handledTime; }

    public int getJoinSource() { return joinSource; }
    public void setJoinSource(int joinSource) { this.joinSource = joinSource; }

    public String getInviterUserId() { return inviterUserId; }
    public void setInviterUserId(String inviterUserId) { this.inviterUserId = inviterUserId; }

    public String getEx() { return ex; }
    public void setEx(String ex) { this.ex = ex; }

    public long getReqTime() { return reqTime; }
    public void setReqTime(long reqTime) { this.reqTime = reqTime; }
}
