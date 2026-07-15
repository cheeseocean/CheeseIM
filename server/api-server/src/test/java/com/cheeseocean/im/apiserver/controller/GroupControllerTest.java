package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.apiserver.auth.CurrentPrincipalArgumentResolver;
import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.enums.GroupStatusEnum;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.enums.NeedVerificationEnum;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupControllerTest {

    @Test
    void listShouldReturnCurrentUsersGroups() throws Exception {
        ConversationService conversationService = mock(ConversationService.class);
        GroupMembershipQueryService groupMembershipQueryService = mock(GroupMembershipQueryService.class);
        when(conversationService.getAllConversations("userB"))
                .thenReturn(List.of(groupConversation("crew"), groupConversation("design")));
        when(groupMembershipQueryService.isGroupMember("crew", "userB")).thenReturn(true);
        when(groupMembershipQueryService.isGroupMember("design", "userB")).thenReturn(false);
        when(groupMembershipQueryService.queryGroup("crew")).thenReturn(Optional.of(group("crew", "Crew")));

        GroupController controller = new GroupController(groupMembershipQueryService);
        ReflectionTestUtils.setField(controller, "conversationService", conversationService);
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("userB"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentPrincipalArgumentResolver(resolver))
                .build();

        mockMvc.perform(get("/api/im/groups")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value("crew"))
                .andExpect(jsonPath("$[0].groupName").value("Crew"));

        verify(conversationService).getAllConversations("userB");
        verify(groupMembershipQueryService).isGroupMember("crew", "userB");
        verify(groupMembershipQueryService).isGroupMember("design", "userB");
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setTenantId("tenant_01");
        session.setSessionId("sess_01");
        session.setDeviceId("dev_01");
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    private static UserConversation groupConversation(String groupId) {
        UserConversation conversation = new UserConversation();
        conversation.setConversationId("g:" + groupId);
        conversation.setTargetId(groupId);
        conversation.setChatType(2);
        return conversation;
    }

    private static Group group(String groupId, String name) {
        Group group = new Group();
        group.setGroupId(groupId);
        group.setGroupName(name);
        group.setAvatarUrl("https://cdn.example.com/" + groupId + ".png");
        group.setStatus(GroupStatusEnum.NORMAL);
        group.setGroupType(GroupTypeEnum.NORMAL_GROUP);
        group.setNeedVerification(NeedVerificationEnum.REQUIRED);
        return group;
    }
}
