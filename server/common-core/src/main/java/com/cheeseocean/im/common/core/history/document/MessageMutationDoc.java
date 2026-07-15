package com.cheeseocean.im.common.core.history.document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
/** 撤回 overlay；不得物理修改 message_block。 */
@Document("message_mutation") @CompoundIndexes({@CompoundIndex(name="conversation_created_mutation_idx", def="{'conversationId': 1, 'createdAt': 1, '_id': 1}")}) public class MessageMutationDoc {
 @Id private String id; private String serverMsgId; private String conversationId; private Integer mutationType; private String operatorUserId; private String operatorName; private String targetSenderId; private String targetSenderName; private String reason; private Long mutationVersion; private Instant createdAt;
 public String getId(){return id;} public void setId(String v){id=v;} public String getServerMsgId(){return serverMsgId;} public void setServerMsgId(String v){serverMsgId=v;} public String getConversationId(){return conversationId;} public void setConversationId(String v){conversationId=v;} public Integer getMutationType(){return mutationType;} public void setMutationType(Integer v){mutationType=v;} public String getOperatorUserId(){return operatorUserId;} public void setOperatorUserId(String v){operatorUserId=v;} public String getOperatorName(){return operatorName;} public void setOperatorName(String v){operatorName=v;} public String getTargetSenderId(){return targetSenderId;} public void setTargetSenderId(String v){targetSenderId=v;} public String getTargetSenderName(){return targetSenderName;} public void setTargetSenderName(String v){targetSenderName=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;} public Long getMutationVersion(){return mutationVersion;} public void setMutationVersion(Long v){mutationVersion=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}
