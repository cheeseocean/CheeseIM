package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.mongo.document.conversation.DeviceConversationDeliveryDoc;
import com.cheeseocean.im.common.core.business.repository.DeviceConversationDeliveryRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/** Mongo 设备送达水位实现，只允许使用 $max 前进。 */
public class DeviceConversationDeliveryRepositoryImpl implements DeviceConversationDeliveryRepository {
    private final MongoTemplate mongoTemplate;

    public DeviceConversationDeliveryRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void updateDeliveredSeq(String userId, String deviceId, String conversationId, long deliveredSeq) {
        String id = userId + ":" + deviceId + ":" + conversationId;
        Update update = new Update()
                .setOnInsert("_id", id).setOnInsert("userId", userId)
                .setOnInsert("deviceId", deviceId).setOnInsert("conversationId", conversationId)
                .max("deliveredSeq", deliveredSeq);
        mongoTemplate.upsert(Query.query(Criteria.where("_id").is(id)), update, DeviceConversationDeliveryDoc.class);
    }
}
