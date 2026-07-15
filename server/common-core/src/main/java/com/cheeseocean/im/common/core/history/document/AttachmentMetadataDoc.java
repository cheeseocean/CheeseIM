package com.cheeseocean.im.common.core.history.document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
/** attachmentId 到消息的点查索引。 */
@Document("attachment_metadata") public class AttachmentMetadataDoc {
 @Id private String id; private String conversationId; private String serverMsgId; private String clientMsgId; private Long seq; private String senderId; private Integer contentType; private Long sendTime; private Instant createdAt;
 public String getId(){return id;} public void setId(String v){id=v;} public String getConversationId(){return conversationId;} public void setConversationId(String v){conversationId=v;} public String getServerMsgId(){return serverMsgId;} public void setServerMsgId(String v){serverMsgId=v;} public String getClientMsgId(){return clientMsgId;} public void setClientMsgId(String v){clientMsgId=v;} public Long getSeq(){return seq;} public void setSeq(Long v){seq=v;} public String getSenderId(){return senderId;} public void setSenderId(String v){senderId=v;} public Integer getContentType(){return contentType;} public void setContentType(Integer v){contentType=v;} public Long getSendTime(){return sendTime;} public void setSendTime(Long v){sendTime=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}
