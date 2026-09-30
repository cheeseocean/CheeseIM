package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.apiserver.auth.CurrentPrincipalArgumentResolver;
import com.cheeseocean.im.apiserver.exception.ApiExceptionHandler;
import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.enums.GroupStatusEnum;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.enums.NeedVerificationEnum;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupControllerTest {

    private ConversationService conversationService;
    private GroupMembershipQueryService groupMembershipQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        groupMembershipQueryService = mock(GroupMembershipQueryService.class);
        GroupController controller = new GroupController(groupMembershipQueryService, conversationService);
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("userB"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new CurrentPrincipalArgumentResolver(resolver))
                .build();
    }

    @Test
    void listShouldReturnCurrentUsersGroups() throws Exception {
        when(conversationService.getAllConversations("userB"))
                .thenReturn(List.of(groupConversation("crew"), groupConversation("design")));
        when(groupMembershipQueryService.isGroupMember("crew", "userB")).thenReturn(true);
        when(groupMembershipQueryService.isGroupMember("design", "userB")).thenReturn(false);
        when(groupMembershipQueryService.queryGroup("crew")).thenReturn(Optional.of(group("crew", "Crew")));

        mockMvc.perform(get("/api/im/groups")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value("crew"))
                .andExpect(jsonPath("$[0].groupName").value("Crew"));

        verify(conversationService).getAllConversations("userB");
        verify(groupMembershipQueryService).isGroupMember("crew", "userB");
        verify(groupMembershipQueryService).isGroupMember("design", "userB");
    }

    @Test
    void listShouldReturnErrorWhenConversationQueryFails() throws Exception {
        when(conversationService.getAllConversations("userB"))
                .thenThrow(new RpcException("conversation query unavailable"));

        mockMvc.perform(get("/api/im/groups")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/im/groups"));

        verify(conversationService).getAllConversations("userB");
        verifyNoInteractions(groupMembershipQueryService);
    }

    @Test
    void listShouldReturnErrorWhenLaterGroupQueryFails() throws Exception {
        when(conversationService.getAllConversations("userB"))
                .thenReturn(List.of(groupConversation("crew"), groupConversation("design")));
        when(groupMembershipQueryService.isGroupMember("crew", "userB")).thenReturn(true);
        when(groupMembershipQueryService.queryGroup("crew")).thenReturn(Optional.of(group("crew", "Crew")));
        when(groupMembershipQueryService.isGroupMember("design", "userB")).thenReturn(true);
        when(groupMembershipQueryService.queryGroup("design"))
                .thenThrow(new RpcException("group query unavailable"));

        mockMvc.perform(get("/api/im/groups")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").isMap())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/im/groups"));

        verify(groupMembershipQueryService).queryGroup("crew");
        verify(groupMembershipQueryService).queryGroup("design");
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
        conversation.setChatType(ChatType.GROUP.getCode());
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
