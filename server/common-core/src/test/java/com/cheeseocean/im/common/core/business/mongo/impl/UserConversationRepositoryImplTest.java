package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserConversationRepositoryImplTest {

    @Test
    void repositoryContractShouldExposeTheRebuiltConversationMethods() throws Exception {
        assertMethod(UserConversationRepository.class, void.class, "createIfAbsent", UserConversation.class);
        assertMethod(UserConversationRepository.class, void.class, "saveAll", List.class);
        assertMethod(UserConversationRepository.class, void.class, "updateFields", String.class, String.class, Map.class);
        assertMethod(UserConversationRepository.class, void.class, "updateBatchFields", List.class, String.class, Map.class);
        assertMethod(UserConversationRepository.class, UserConversation.class, "findOne", String.class, String.class);
        assertMethod(UserConversationRepository.class, List.class, "findByIds", String.class, List.class);
        assertMethod(UserConversationRepository.class, List.class, "findAll", String.class);
        assertMethod(UserConversationRepository.class, List.class, "findConversationIds", String.class);
        assertMethod(UserConversationRepository.class, List.class, "findNotReceiveUserIds", String.class, List.class);
        assertMethod(UserConversationRepository.class, List.class, "findPinnedConversationIds", String.class);
    }

    @Test
    void legacyConversationRepositoryMethodsShouldNoLongerExist() {
        assertMissingMethod(UserConversationRepository.class, "clearUnread", String.class, String.class);
        assertMissingMethod(UserConversationRepository.class, "getReceiveOption", String.class, String.class);
        assertMissingMethod(UserConversationRepository.class, "setReceiveOption", String.class, String.class, int.class);
        assertMissingMethod(UserConversationRepository.class, "upsertFields", String.class, String.class, int.class, String.class, Map.class);
    }

    private static void assertMethod(Class<?> type, Class<?> returnType, String name, Class<?>... parameterTypes)
            throws Exception {
        Method method = type.getMethod(name, parameterTypes);
        assertEquals(returnType, method.getReturnType(), () -> "Unexpected return type for " + name);
    }

    private static void assertMissingMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        assertThrows(NoSuchMethodException.class, () -> type.getMethod(name, parameterTypes));
    }
}
