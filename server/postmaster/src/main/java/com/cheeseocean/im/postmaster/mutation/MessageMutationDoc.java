package com.cheeseocean.im.postmaster.mutation;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 消息撤回 overlay；原始 message_block 永不因撤回而改写。
 */
@Document("message_mutation")
@CompoundIndexes({
        @CompoundIndex(name = "conversation_created_mutation_idx",
                def = "{'conversationId': 1, 'createdAt': 1, '_id': 1}")
})
public class MessageMutationDoc {

    @Id
    private String id;
    private String serverMsgId;
    private String conversationId;
    private Integer mutationType;
    private String operatorUserId;
    private String operatorName;
    private String targetSenderId;
    private String targetSenderName;
    private String reason;
    private Long mutationVersion;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Integer getMutationType() { return mutationType; }
    public void setMutationType(Integer mutationType) { this.mutationType = mutationType; }
    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getTargetSenderId() { return targetSenderId; }
    public void setTargetSenderId(String targetSenderId) { this.targetSenderId = targetSenderId; }
    public String getTargetSenderName() { return targetSenderName; }
    public void setTargetSenderName(String targetSenderName) { this.targetSenderName = targetSenderName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getMutationVersion() { return mutationVersion; }
    public void setMutationVersion(Long mutationVersion) { this.mutationVersion = mutationVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
