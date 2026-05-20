package com.cheeseocean.im.business.service.conversation;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.template.QuickConfig;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.dto.conversation.ConversationIncrementalSyncResult;
import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import com.cheeseocean.im.common.core.business.repository.ConversationVersionLogRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceImplTest {

    @Test
    void getConversationIdsShouldCopyCachedList() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        @SuppressWarnings("unchecked")
        Cache<String, List<String>> idsCache = mock(Cache.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));
        when(idsCache.get("u1")).thenReturn(List.of("c1", "c2"));

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, mock(ConversationVersionLogRepository.class), cacheManager
        );
        ReflectionTestUtils.setField(service, "conversationIdsCache", idsCache);

        List<String> result = service.getConversationIds("u1");

        assertEquals(List.of("c1", "c2"), result);
        assertEquals(java.util.ArrayList.class, result.getClass());
    }

    @Test
    void getConversationsShouldReturnRepositoryState() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        @SuppressWarnings("unchecked")
        Cache<String, UserConversation> detailCache = mock(Cache.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));

        UserConversation state = new UserConversation();
        state.setOwnerUserId("u1");
        state.setConversationId("c1");
        state.setChatType(1);
        state.setTargetId("u2");
        state.setUnreadCount(7);

        when(stateRepository.findByIds("u1", List.of("c1"))).thenReturn(List.of(state));
        when(detailCache.getAll(anySet())).thenReturn(Map.of());

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, mock(ConversationVersionLogRepository.class), cacheManager
        );
        ReflectionTestUtils.setField(service, "conversationDetailCache", detailCache);

        UserConversation result = service.getConversations("u1", List.of("c1")).get(0);

        assertEquals(7, result.getUnreadCount());
        assertEquals("u2", result.getTargetId());
        verify(detailCache).putAll(anyMap());
    }

    @Test
    void getConversationShouldReturnNullWhenStateMissing() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        @SuppressWarnings("unchecked")
        Cache<String, UserConversation> detailCache = mock(Cache.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));

        when(stateRepository.findOne("u1", "c1")).thenReturn(null);
        when(detailCache.computeIfAbsent(eq("u1:c1"), any())).thenAnswer(invocation -> {
            Function<String, UserConversation> loader = invocation.getArgument(1);
            return loader.apply("u1:c1");
        });

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, mock(ConversationVersionLogRepository.class), cacheManager
        );
        ReflectionTestUtils.setField(service, "conversationDetailCache", detailCache);

        assertNull(service.getConversation("u1", "c1"));
    }

    @Test
    void recvMsgOptMethodsShouldReadFromState() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        @SuppressWarnings("unchecked")
        Cache<String, UserConversation> detailCache = mock(Cache.class);
        @SuppressWarnings("unchecked")
        Cache<String, List<String>> notNotifyCache = mock(Cache.class);
        @SuppressWarnings("unchecked")
        Cache<String, List<String>> notReceiveCache = mock(Cache.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));

        UserConversation state = new UserConversation();
        state.setReceiveOpt(2);
        when(stateRepository.findOne("u1", "c1")).thenReturn(state);
        when(detailCache.computeIfAbsent(eq("u1:c1"), any())).thenAnswer(invocation -> {
            Function<String, UserConversation> loader = invocation.getArgument(1);
            return loader.apply("u1:c1");
        });

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, mock(ConversationVersionLogRepository.class), cacheManager
        );
        ReflectionTestUtils.setField(service, "conversationDetailCache", detailCache);
        ReflectionTestUtils.setField(service, "notNotifyConversationIdsCache", notNotifyCache);
        ReflectionTestUtils.setField(service, "conversationNotReceiveUserIdsCache", notReceiveCache);

        assertEquals(2, service.getReceiveOption("u1", "c1"));

        verify(stateRepository, never()).updateFields(eq("u1"), eq("c1"), anyMap());
        verify(detailCache, never()).removeAll(anySet());
    }

    @Test
    void createGroupChatConversationsShouldOnlyCreateMissingUsers() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        ConversationVersionLogRepository versionLogRepository = mock(ConversationVersionLogRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));
        when(stateRepository.findExistingOwnerUserIds(List.of("u1", "u2", "u3"), "g:crew"))
                .thenReturn(List.of("u2"));

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, versionLogRepository, cacheManager
        );

        service.createGroupChatConversations("crew", "g:crew", List.of("u1", "u2", "u3", "u1"));

        verify(stateRepository, times(2)).createIfAbsent(any(UserConversation.class));
        verify(versionLogRepository).append("u1", "g:crew", ConversationVersionOperation.INSERT);
        verify(versionLogRepository).append("u3", "g:crew", ConversationVersionOperation.INSERT);
        verify(stateRepository).findExistingOwnerUserIds(List.of("u1", "u2", "u3"), "g:crew");
    }

    @Test
    void deleteConversationShouldRemoveOnlyOwnerConversationAndAppendDeleteVersion() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        ConversationVersionLogRepository versionLogRepository = mock(ConversationVersionLogRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, versionLogRepository, cacheManager
        );

        service.deleteConversation("u1", "s:u1:u2");

        verify(stateRepository).delete("u1", "s:u1:u2");
        verify(versionLogRepository).append("u1", "s:u1:u2", ConversationVersionOperation.DELETE);
    }

    @Test
    void syncConversationsShouldReturnFullWhenClientVersionMissing() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        ConversationVersionLogRepository versionLogRepository = mock(ConversationVersionLogRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));

        UserConversation conversation = new UserConversation();
        conversation.setOwnerUserId("u1");
        conversation.setConversationId("c1");
        when(stateRepository.findAll("u1")).thenReturn(List.of(conversation));

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, versionLogRepository, cacheManager
        );

        ConversationIncrementalSyncResult result = service.syncConversations("u1", "", 0, 0);

        assertTrue(result.isFull());
        assertEquals(List.of(conversation), result.getInsert());
        assertTrue(result.getUpdate().isEmpty());
        assertTrue(result.getDelete().isEmpty());
    }

    @Test
    void syncConversationsShouldReturnIncrementalUpdatesWhenVersionMatches() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        ConversationVersionLogRepository versionLogRepository = mock(ConversationVersionLogRepository.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));

        com.cheeseocean.im.common.api.business.domain.ConversationVersionLog latest =
                new com.cheeseocean.im.common.api.business.domain.ConversationVersionLog();
        latest.setOwnerUserId("u1");
        latest.setVersionId("v1");
        latest.setVersion(2);
        when(versionLogRepository.findLatest("u1")).thenReturn(java.util.Optional.of(latest));

        com.cheeseocean.im.common.api.business.domain.ConversationVersionLog changed =
                new com.cheeseocean.im.common.api.business.domain.ConversationVersionLog();
        changed.setOwnerUserId("u1");
        changed.setConversationId("c2");
        changed.setVersionId("v1");
        changed.setVersion(2);
        changed.setOperation(ConversationVersionOperation.UPDATE);
        when(versionLogRepository.findAfter("u1", "v1", 1, 200)).thenReturn(List.of(changed));

        UserConversation conversation = new UserConversation();
        conversation.setOwnerUserId("u1");
        conversation.setConversationId("c2");
        when(stateRepository.findByIds("u1", List.of("c2"))).thenReturn(List.of(conversation));

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, versionLogRepository, cacheManager
        );

        ConversationIncrementalSyncResult result = service.syncConversations("u1", "v1", 1, -1);

        assertFalse(result.isFull());
        assertEquals("v1", result.getVersionId());
        assertEquals(2, result.getVersion());
        assertEquals(List.of(conversation), result.getUpdate());
        assertTrue(result.getInsert().isEmpty());
        assertTrue(result.getDelete().isEmpty());
    }
}
