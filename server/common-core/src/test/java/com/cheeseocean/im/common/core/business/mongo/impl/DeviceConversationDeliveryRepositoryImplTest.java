package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.mongo.document.conversation.DeviceConversationDeliveryDoc;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeviceConversationDeliveryRepositoryImplTest {
    @Test
    void shouldPersistDeviceHighWatermarkWithMongoMax() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        new DeviceConversationDeliveryRepositoryImpl(mongo)
                .updateDeliveredSeq("u2", "ios-1", "s:u1:u2", 12L);

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).upsert(org.mockito.ArgumentMatchers.any(Query.class), update.capture(),
                eq(DeviceConversationDeliveryDoc.class));
        Document max = (Document) update.getValue().getUpdateObject().get("$max");
        assertEquals(12L, max.get("deliveredSeq"));
    }
}
