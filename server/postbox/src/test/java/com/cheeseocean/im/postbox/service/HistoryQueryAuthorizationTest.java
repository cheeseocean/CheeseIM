package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.permission.ConversationPermissionService;
import com.cheeseocean.im.common.api.enums.ErrorCode;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.core.history.MessageHistoryRepository;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryQueryAuthorizationTest {
    @Test
    void permissionFailureAfterPreviousAllowShouldNotReadHistoryAgain() {
        MessageHistoryRepository history = mock(MessageHistoryRepository.class);
        when(history.findRecentBlocks("g:crew", 10, 16)).thenReturn(List.of());
        ConversationPermissionService permission = mock(ConversationPermissionService.class);
        when(permission.check(any())).thenReturn(PermissionCheckResult.allow())
                .thenThrow(new RpcException("permission unavailable"));
        HistoryQueryService service = new HistoryQueryService(history, mock(MessagePreviewResolver.class));
        ReflectionTestUtils.setField(service, "conversationPermissionService", permission);
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId("A");

        assertThat(service.getConversationMessages(session, "g:crew", 10)).isEmpty();
        assertThat(service.getConversationMessages(session, "g:crew", 10)).isEmpty();

        verify(history, times(1)).findRecentBlocks("g:crew", 10, 16);
    }

    @Test
    void missingOrDeniedPermissionShouldNotAccessPersistence() {
        MessageHistoryRepository history = mock(MessageHistoryRepository.class);
        HistoryQueryService service = new HistoryQueryService(history, mock(MessagePreviewResolver.class));
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId("C");
        assertThat(service.getConversationMessages(session, "s:A:B", 10)).isEmpty();
        ConversationPermissionService permission = mock(ConversationPermissionService.class);
        when(permission.check(any())).thenReturn(PermissionCheckResult.deny(
                Integer.toString(ErrorCode.CONVERSATION_ACCESS_DENIED.getCode()),
                ErrorCode.CONVERSATION_ACCESS_DENIED.getDesc())).thenReturn((PermissionCheckResult) null);
        ReflectionTestUtils.setField(service, "conversationPermissionService", permission);

        assertThat(service.getConversationMessages(session, "s:A:B", 10)).isEmpty();
        assertThat(service.getConversationMessages(session, "s:A:B", 10)).isEmpty();
        assertThat(service.getConversationMessages(null, "s:A:B", 10)).isEmpty();

        verifyNoInteractions(history);
    }
}
