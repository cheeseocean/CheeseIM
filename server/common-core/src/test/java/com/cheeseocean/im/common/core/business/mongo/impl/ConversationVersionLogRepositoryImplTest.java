package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.ConversationVersionLog;
import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import com.cheeseocean.im.common.core.business.repository.ConversationVersionLogRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationVersionLogRepositoryImplTest {

    @Test
    void repositoryContractShouldExposeVersionStreamOperations() throws Exception {
        assertMethod(ConversationVersionLogRepository.class, ConversationVersionLog.class,
                "append", String.class, String.class, ConversationVersionOperation.class);
        assertMethod(ConversationVersionLogRepository.class, Optional.class,
                "findLatest", String.class);
        assertMethod(ConversationVersionLogRepository.class, List.class,
                "findAfter", String.class, String.class, long.class, int.class);
    }

    private static void assertMethod(Class<?> type, Class<?> returnType, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);
        assertEquals(returnType, method.getReturnType());
    }
}
