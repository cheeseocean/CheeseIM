package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 会话投递偏好的 conversation 维度读模型。集合：{@code conversation_delivery_preference}。
 *
 * <p>只保存非默认偏好；离线推送按 conversationId 查询时不再反扫 owner 维度会话当前态。</p>
 */
@Document("conversation_delivery_preference")
@CompoundIndexes({
        @CompoundIndex(name = "uk_conversation_owner_preference",
                def = "{'conversationId': 1, 'ownerUserId': 1}", unique = true),
        @CompoundIndex(name = "idx_conversation_option_owner",
                def = "{'conversationId': 1, 'receiveOption': 1, 'ownerUserId': 1}")
})
@Data
public class ConversationDeliveryPreferenceDoc {

    @Id
    private String id;
    private String conversationId;
    private String ownerUserId;
    private int receiveOption;
    private Instant updatedAt;
}
