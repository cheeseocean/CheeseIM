package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/** 用户设备维度的会话送达高水位持久化文档。 */
@Document("device_conversation_delivery")
@CompoundIndexes(@CompoundIndex(name = "user_device_conversation", def = "{'userId':1,'deviceId':1,'conversationId':1}", unique = true))
@Data
public class DeviceConversationDeliveryDoc {
    @Id private String id;
    private String userId;
    private String deviceId;
    private String conversationId;
    private long deliveredSeq;
}
