package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ErrorCode;
import com.cheeseocean.im.common.api.exception.BusinessException;
import com.cheeseocean.im.common.core.business.repository.ConversationDeliveryPreferenceRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationVersionLogRepository;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import com.cheeseocean.im.common.core.cache.CacheRegion;
import com.cheeseocean.im.common.core.cache.CacheStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话配置授权边界测试。
 *
 * @author wxc
 */
class ConversationServiceImplTest {

    @Test
    void shouldRejectConversationIdNotOwnedByCurrentUser() {
        Fixtures fixtures = fixtures();
        SetConversationRequest request = request(ChatType.PRIVATE, "s:attacker:victim", "victim");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixtures.service.setConversations(List.of("user-a"), request));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
        verify(fixtures.stateRepository, never()).createIfAbsent(any());
    }

    @Test
    void shouldRejectGroupConfigurationForNonMember() {
        Fixtures fixtures = fixtures();
        when(fixtures.groupMemberRepository.existsByGroupAndUser("group-a", "user-a")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixtures.service.setConversations(
                        List.of("user-a"), request(ChatType.GROUP, "g:group-a", "group-a")));

        assertEquals(ErrorCode.GROUP_NOT_MEMBER, exception.getErrorCode());
        verify(fixtures.stateRepository, never()).createIfAbsent(any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Fixtures fixtures() {
        UserConversationRepository stateRepository = mock(UserConversationRepository.class);
        GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
        CacheStore cacheStore = mock(CacheStore.class);
        CacheRegion cacheRegion = mock(CacheRegion.class);
        when(cacheStore.region(anyString(), any(Class.class), any())).thenReturn(cacheRegion);
        when(cacheStore.listRegion(anyString(), any(Class.class), any())).thenReturn(cacheRegion);
        ConversationServiceImpl service = new ConversationServiceImpl(
                stateRepository,
                mock(ConversationVersionLogRepository.class),
                mock(ConversationDeliveryPreferenceRepository.class),
                groupMemberRepository,
                cacheStore);
        return new Fixtures(service, stateRepository, groupMemberRepository);
    }

    private SetConversationRequest request(ChatType type, String conversationId, String targetId) {
        SetConversationRequest request = new SetConversationRequest();
        request.setConversationType(type.getCode());
        request.setConversationId(conversationId);
        request.setTargetId(targetId);
        request.setPinned(true);
        return request;
    }

    private record Fixtures(ConversationServiceImpl service,
                            UserConversationRepository stateRepository,
                            GroupMemberRepository groupMemberRepository) {
    }
}
