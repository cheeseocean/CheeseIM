package com.cheeseocean.im.common.core.business.mongo.config;

import com.cheeseocean.im.common.core.business.mongo.impl.ConversationOffsetRangeRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.FriendRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.GroupApplicationRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.GroupMemberRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.GroupRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.UserConversationStateRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.UserRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.UserSyncCheckpointRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommonMongoPersistenceConfigurationTest {

    @Test
    void enableAnnotationShouldImportConfiguration() {
        Import importAnnotation = EnableCommonMongoPersistence.class.getAnnotation(Import.class);

        assertNotNull(importAnnotation);
        assertArrayEquals(new Class<?>[]{CommonMongoPersistenceConfiguration.class}, importAnnotation.value());
    }

    @Test
    void configurationShouldRegisterSharedMongoPackagesOnly() {
        EnableMongoRepositories mongoRepositories = CommonMongoPersistenceConfiguration.class.getAnnotation(EnableMongoRepositories.class);
        ComponentScan componentScan = CommonMongoPersistenceConfiguration.class.getAnnotation(ComponentScan.class);

        assertNotNull(mongoRepositories);
        assertArrayEquals(new String[]{"com.cheeseocean.im.common.core.business.mongo.repository"}, mongoRepositories.basePackages());
        assertNotNull(componentScan);
        assertArrayEquals(new String[]{"com.cheeseocean.im.common.core.business.mongo.impl"}, componentScan.basePackages());
    }

    @Test
    void repositoryImplementationsShouldNotBeDirectlyDiscoverableWithoutEnablement() {
        assertNull(ConversationOffsetRangeRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(FriendRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(GroupApplicationRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(GroupMemberRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(GroupRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(UserConversationStateRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(UserRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(UserSyncCheckpointRepositoryImpl.class.getAnnotation(Repository.class));
    }
}
