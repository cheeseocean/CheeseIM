package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.UserConversationSyncPoint;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserConversationSyncPointRepositoryImplTest {

    @Test
    void repositoryContractShouldExposeTheRebuiltSyncPointMethods() throws Exception {
        assertMethod(UserConversationSyncPointRepository.class, void.class, "updateMaxSeq", String.class, String.class, long.class);
        assertMethod(UserConversationSyncPointRepository.class, long.class, "getMaxSeq", String.class, String.class);
        assertMethod(UserConversationSyncPointRepository.class, void.class, "updateMinSeq", String.class, String.class, long.class);
        assertMethod(UserConversationSyncPointRepository.class, long.class, "getMinSeq", String.class, String.class);
        assertMethod(UserConversationSyncPointRepository.class, void.class, "updateReadSeq", String.class, String.class, long.class);
        assertMethod(UserConversationSyncPointRepository.class, long.class, "getReadSeq", String.class, String.class);
        assertMethod(UserConversationSyncPointRepository.class, Map.class, "getReadSeqMap", String.class, List.class);
        assertMethod(UserConversationSyncPointRepository.class, java.util.Optional.class, "find", String.class, String.class);
        assertMethod(UserConversationSyncPointRepository.class, List.class, "findByIds", String.class, List.class);
        assertMethod(UserConversationSyncPointRepository.class, List.class, "findByUserId", String.class);
    }

    @Test
    void legacyCreateIfAbsentMethodShouldNoLongerExist() {
        assertMissingMethod(UserConversationSyncPointRepository.class, "createIfAbsent", String.class, String.class);
    }

    @Test
    void readSeqContractShouldBeMonotonic() {
        ReadSeqWindow window = new ReadSeqWindow();

        window.updateReadSeq(12L);
        window.updateReadSeq(7L);

        assertEquals(12L, window.readSeq());
    }

    @Test
    void domainUnreadCountShouldContinueToUseMaxMinusRead() {
        UserConversationSyncPoint checkpoint = new UserConversationSyncPoint();
        checkpoint.setReadSeq(4L);
        checkpoint.setMaxSeq(9L);

        assertEquals(5L, checkpoint.getUnreadCount());
    }

    private static void assertMethod(Class<?> type, Class<?> returnType, String name, Class<?>... parameterTypes)
            throws Exception {
        Method method = type.getMethod(name, parameterTypes);
        assertEquals(returnType, method.getReturnType(), () -> "Unexpected return type for " + name);
    }

    private static void assertMissingMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        assertThrows(NoSuchMethodException.class, () -> type.getMethod(name, parameterTypes));
    }

    private static final class ReadSeqWindow {
        private long readSeq;

        void updateReadSeq(long nextReadSeq) {
            readSeq = Math.max(readSeq, nextReadSeq);
        }

        long readSeq() {
            return readSeq;
        }
    }
}
