package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.ConversationRange;
import com.cheeseocean.im.common.core.business.repository.ConversationRangeRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationRangeRepositoryImplTest {

    @Test
    void repositoryContractShouldExposeTheRangeMethods() throws Exception {
        assertMethod(ConversationRangeRepository.class, long.class, "allocate", String.class, long.class);
        assertMethod(ConversationRangeRepository.class, void.class, "setMaxSeq", String.class, long.class);
        assertMethod(ConversationRangeRepository.class, long.class, "getMaxSeq", String.class);
        assertMethod(ConversationRangeRepository.class, void.class, "setMinSeq", String.class, long.class);
        assertMethod(ConversationRangeRepository.class, long.class, "getMinSeq", String.class);
        assertMethod(ConversationRangeRepository.class, ConversationRange.class, "find", String.class);
    }

    private static void assertMethod(Class<?> type, Class<?> returnType, String name, Class<?>... parameterTypes)
            throws Exception {
        Method method = type.getMethod(name, parameterTypes);
        assertEquals(returnType, method.getReturnType(), () -> "Unexpected return type for " + name);
    }
}
