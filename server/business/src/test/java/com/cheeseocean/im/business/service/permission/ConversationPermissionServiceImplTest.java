package com.cheeseocean.im.business.service.permission;

import com.cheeseocean.im.common.api.enums.ErrorCode;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConversationPermissionServiceImplTest {

    @ParameterizedTest
    @CsvSource({"A,s:A:B,true", "B,s:A:B,true", "C,s:A:B,false", "A,n:A,true", "B,n:A,false",
            "A,s:B:A,false", "A,s:A:B:extra,false", "A,s:A:B:,false", "A,unknown:A,false",
            "A,s:A:,false", "A,g:,false", "A,ng:,false"})
    void shouldAuthorizeOnlyCanonicalOwnedConversations(String userId, String conversationId, boolean allowed) {
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        ConversationPermissionServiceImpl service = new ConversationPermissionServiceImpl(members);

        assertThat(service.check(request(userId, conversationId)).isAllowed()).isEqualTo(allowed);
        verifyNoInteractions(members);
    }

    @ParameterizedTest
    @CsvSource({"g:crew", "ng:crew"})
    void shouldRecheckMembershipAfterLeavingGroup(String conversationId) {
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        when(members.existsByGroupAndUser("crew", "A")).thenReturn(true, false);
        ConversationPermissionServiceImpl service = new ConversationPermissionServiceImpl(members);

        assertThat(service.check(request("A", conversationId)).isAllowed()).isTrue();
        assertThat(service.check(request("A", conversationId)).isAllowed()).isFalse();
        verify(members, times(2)).existsByGroupAndUser("crew", "A");
    }

    @Test
    void shouldNotReusePreviousAllowWhenMembershipStorageFails() {
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        when(members.existsByGroupAndUser("crew", "A")).thenReturn(true)
                .thenThrow(new IllegalStateException("storage unavailable"));
        ConversationPermissionServiceImpl service = new ConversationPermissionServiceImpl(members);

        assertThat(service.check(request("A", "g:crew")).isAllowed()).isTrue();
        PermissionCheckResult failed = service.check(request("A", "g:crew"));
        assertThat(failed.isAllowed()).isFalse();
        assertThat(failed.getCode()).isEqualTo(Integer.toString(ErrorCode.INTERNAL_ERROR.getCode()));
    }

    @Test
    void shouldDenyMissingRequestOrIdentity() {
        ConversationPermissionServiceImpl service = new ConversationPermissionServiceImpl(mock(GroupMemberRepository.class));

        assertThat(service.check(null).isAllowed()).isFalse();
        assertThat(service.check(request(null, "s:A:B")).isAllowed()).isFalse();
        assertThat(service.check(request(" ", "s:A:B")).isAllowed()).isFalse();
        assertThat(service.check(request("A", null)).isAllowed()).isFalse();
    }

    private ConversationPermissionRequest request(String userId, String conversationId) {
        ConversationPermissionRequest request = new ConversationPermissionRequest();
        request.setUserId(userId);
        request.setConversationId(conversationId);
        return request;
    }
}
