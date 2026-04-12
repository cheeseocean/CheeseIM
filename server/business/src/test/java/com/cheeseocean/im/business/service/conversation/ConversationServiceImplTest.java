package com.cheeseocean.im.business.service.conversation;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.template.QuickConfig;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        ConversationServiceImpl service = new ConversationServiceImpl(stateRepository, cacheManager);
        ReflectionTestUtils.setField(service, "conversationIdsCache", idsCache);

        List<String> result = service.getConversationIds("u1");

        assertEquals(List.of("c1", "c2"), result);
        assertEquals(java.util.ArrayList.class, result.getClass());
    }

    @Test
    void getConversationsShouldReturnRepositoryState() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        ConversationSettingsNotifier conversationSettingsNotifier = mock(ConversationSettingsNotifier.class);
        CacheManager cacheManager = mock(CacheManager.class);
        @SuppressWarnings("unchecked")
        Cache<String, UserConversation> detailCache = mock(Cache.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));

        UserConversation state = new UserConversation();
        state.setOwnerUserId("u1");
        state.setConversationId("c1");
        state.setConversationType(1);
        state.setTargetId("u2");
        state.setUnreadCount(7);

        when(stateRepository.findByIds("u1", List.of("c1"))).thenReturn(List.of(state));
        when(detailCache.getAll(anySet())).thenReturn(Map.of());

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, cacheManager
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
        ConversationSettingsNotifier conversationSettingsNotifier = mock(ConversationSettingsNotifier.class);
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
                stateRepository, cacheManager
        );
        ReflectionTestUtils.setField(service, "conversationDetailCache", detailCache);

        assertNull(service.getConversation("u1", "c1"));
    }

    @Test
    void recvMsgOptMethodsShouldReadFromStateAndUpdateFields() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        ConversationSettingsNotifier conversationSettingsNotifier = mock(ConversationSettingsNotifier.class);
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
                stateRepository, cacheManager
        );
        ReflectionTestUtils.setField(service, "conversationDetailCache", detailCache);
        ReflectionTestUtils.setField(service, "notNotifyConversationIdsCache", notNotifyCache);
        ReflectionTestUtils.setField(service, "conversationNotReceiveUserIdsCache", notReceiveCache);

        assertEquals(2, service.getReceiveOption("u1", "c1"));

        verify(stateRepository).updateFields("u1", "c1", Map.of("receiveOpt", 1));
        verify(conversationSettingsNotifier).notifyRecvMsgOptChanged("u1", "c1", 1);
        verify(detailCache, never()).removeAll(anySet());
    }

    @Test
    void createGroupChatConversationsShouldOnlyCreateMissingUsers() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        ConversationSettingsNotifier conversationSettingsNotifier = mock(ConversationSettingsNotifier.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));
        when(stateRepository.findExistingOwnerUserIds(List.of("u1", "u2", "u3"), "g:crew"))
                .thenReturn(List.of("u2"));

        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository, cacheManager
        );

        service.createGroupChatConversations("crew", "g:crew", List.of("u1", "u2", "u3", "u1"));

        verify(stateRepository, times(2)).createIfAbsent(any(UserConversation.class));
        verify(stateRepository).findExistingOwnerUserIds(List.of("u1", "u2", "u3"), "g:crew");
    }
}
