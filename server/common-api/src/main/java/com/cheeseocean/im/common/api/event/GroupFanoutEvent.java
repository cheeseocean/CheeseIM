package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.dto.message.Message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 普通群写扩散任务。 */
public class GroupFanoutEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private String jobId;
    private String groupId;
    private String conversationId;
    private boolean createConversation;
    /**
     * 权限校验阶段读取到的成员关系版本；worker 按此版本读取不可变 epoch 快照。
     */
    private long membershipVersion;
    private List<Message> messages = new ArrayList<>();

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public boolean isCreateConversation() { return createConversation; }
    public void setCreateConversation(boolean createConversation) { this.createConversation = createConversation; }
    public long getMembershipVersion() { return membershipVersion; }
    public void setMembershipVersion(long membershipVersion) { this.membershipVersion = membershipVersion; }
    public List<Message> getMessages() { return new ArrayList<>(messages); }
    public void setMessages(List<Message> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }
}
