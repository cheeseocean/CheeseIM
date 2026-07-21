package com.cheeseocean.im.storage.history.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** 消息撤回/编辑 overlay Mongo 文档；不得物理修改 message_block。 */
@Document("message_mutation")
@CompoundIndexes({
        @CompoundIndex(name = "conversation_created_mutation_idx",
                def = "{'conversationId': 1, 'createdAt': 1, '_id': 1}")
})
public class MessageMutationDoc {
    @Id private String id;
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
    public void setId(String value) { id = value; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String value) { serverMsgId = value; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String value) { conversationId = value; }
    public Integer getMutationType() { return mutationType; }
    public void setMutationType(Integer value) { mutationType = value; }
    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String value) { operatorUserId = value; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String value) { operatorName = value; }
    public String getTargetSenderId() { return targetSenderId; }
    public void setTargetSenderId(String value) { targetSenderId = value; }
    public String getTargetSenderName() { return targetSenderName; }
    public void setTargetSenderName(String value) { targetSenderName = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public Long getMutationVersion() { return mutationVersion; }
    public void setMutationVersion(Long value) { mutationVersion = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
