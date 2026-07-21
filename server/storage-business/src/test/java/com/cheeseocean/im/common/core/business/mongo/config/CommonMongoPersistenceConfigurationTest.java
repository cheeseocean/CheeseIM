package com.cheeseocean.im.common.core.business.mongo.config;

import com.cheeseocean.im.common.core.business.mongo.impl.UserConversationSyncPointRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.BlacklistRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.FriendRequestRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.FriendshipRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.GroupRequestRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.GroupMemberRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.GroupRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.UserConversationRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.UserRepositoryImpl;
import com.cheeseocean.im.common.core.business.mongo.impl.UserSecurityStateRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommonMongoPersistenceConfigurationTest {

    @Test
    void configurationShouldRegisterSharedMongoPackagesOnly() {
        ComponentScan componentScan = CommonMongoPersistenceConfiguration.class.getAnnotation(ComponentScan.class);

        assertNotNull(componentScan);
        assertArrayEquals(new String[]{"com.cheeseocean.im.common.core.business.mongo.impl"}, componentScan.basePackages());
    }

    @Test
    void repositoryImplementationsShouldNotBeDirectlyDiscoverableWithoutEnablement() {
        assertNull(UserConversationSyncPointRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(BlacklistRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(FriendRequestRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(FriendshipRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(GroupRequestRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(GroupMemberRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(GroupRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(UserConversationRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(UserRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(UserSecurityStateRepositoryImpl.class.getAnnotation(Repository.class));
        assertNull(UserConversationSyncPointRepositoryImpl.class.getAnnotation(Repository.class));
    }
}
