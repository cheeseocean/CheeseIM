package com.cheeseocean.im.business;

import com.cheeseocean.im.common.core.business.mongo.config.EnableCommonMongoPersistence;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BusinessMongoPersistenceEnablementTest {

    @Test
    void businessShouldOptIntoSharedMongoPersistence() {
        assertNotNull(Business.class.getAnnotation(EnableCommonMongoPersistence.class));
        assertNull(Business.class.getAnnotation(EnableMongoRepositories.class));
    }
}
