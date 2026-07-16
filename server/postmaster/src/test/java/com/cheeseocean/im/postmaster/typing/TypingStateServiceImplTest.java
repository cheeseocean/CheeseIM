package com.cheeseocean.im.postmaster.typing;

import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.enums.TypingActionEnum;
import com.cheeseocean.im.common.api.permission.ConversationPermissionService;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import com.cheeseocean.im.common.api.rpc.ControlNotificationDispatcher;
import com.cheeseocean.im.common.core.store.typing.TypingStateStore;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TypingStateServiceImplTest {

    private GroupMembershipFacade groupMembershipFacade;
    private TypingStateStore typingStateStore;
    private ControlNotificationDispatcher dispatcher;
    private TypingStateServiceImpl service;

    @BeforeEach
    void setUp() {
        groupMembershipFacade = mock(GroupMembershipFacade.class);
        typingStateStore = mock(TypingStateStore.class);
        dispatcher = mock(ControlNotificationDispatcher.class);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());
        service = new TypingStateServiceImpl(groupMembershipFacade, typingStateStore, 4, 3);
        ReflectionTestUtils.setField(service, "conversationPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "controlNotificationDispatcher", dispatcher);
    }

    @Test
    void privateStartShouldDispatchOnlyWhenRedisAcceptsStateTransition() {
        when(typingStateStore.update("u1", "s:u1:u2", TypingActionEnum.START, 4)).thenReturn(true);

        assertNotNull(service.publish("u1", "s:u1:u2", TypingActionEnum.START, 4));

        verify(dispatcher).dispatch(any(ControlNotificationReq.class));
    }

    @Test
    void repeatedStartShouldBeThrottledWithoutDispatch() {
        when(typingStateStore.update(any(), any(), any(), anyInt())).thenReturn(false);

        assertNotNull(service.publish("u1", "s:u1:u2", TypingActionEnum.START, 4));

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void normalGroupWithinLimitShouldNotifyOtherMembers() {
        when(groupMembershipFacade.loadGroupType("g1")).thenReturn(GroupTypeEnum.NORMAL_GROUP);
        when(groupMembershipFacade.loadGroupMembers("g1")).thenReturn(List.of("u1", "u2", "u3"));
        when(typingStateStore.update(any(), any(), any(), anyInt())).thenReturn(true);

        assertNotNull(service.publish("u1", "g:g1", TypingActionEnum.START, 4));

        verify(dispatcher, times(2)).dispatch(any());
    }

    @Test
    void normalGroupOverLimitShouldBeDisabledBeforeRedisWrite() {
        when(groupMembershipFacade.loadGroupType("g1")).thenReturn(GroupTypeEnum.NORMAL_GROUP);
        when(groupMembershipFacade.loadGroupMembers("g1")).thenReturn(List.of("u1", "u2", "u3", "u4"));

        assertNull(service.publish("u1", "g:g1", TypingActionEnum.START, 4));

        verify(typingStateStore, never()).update(any(), any(), any(), anyInt());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void superGroupShouldBeDisabledWithoutLoadingMembers() {
        when(groupMembershipFacade.loadGroupType("g1")).thenReturn(GroupTypeEnum.SUPER_GROUP);

        assertNull(service.publish("u1", "g:g1", TypingActionEnum.START, 4));

        verify(groupMembershipFacade, never()).loadGroupMembers(any());
        verify(typingStateStore, never()).update(any(), any(), any(), anyInt());
    }
}
