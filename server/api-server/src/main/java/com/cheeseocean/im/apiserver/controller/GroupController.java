package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.group.GroupMembershipQueryService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/im/groups")
public class GroupController {

    private final GroupMembershipQueryService groupMembershipQueryService;
    private final ConversationService conversationService;

    /** 群查询与会话查询均由 consumer 装配，避免依赖同 JVM 的业务实现。 */
    public GroupController(GroupMembershipQueryService groupMembershipQueryService,
                           ConversationService conversationService) {
        this.groupMembershipQueryService = groupMembershipQueryService;
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<GroupSummaryResponse> list(SessionPrincipal session) {
        List<GroupSummaryResponse> responses = new ArrayList<>();
        for (UserConversation conversation : conversationService.getAllConversations(session.getUserId())) {
            String groupId = resolveGroupId(conversation);
            if (groupId == null || !groupMembershipQueryService.isGroupMember(groupId, session.getUserId())) {
                continue;
            }
            Optional<Group> group = groupMembershipQueryService.queryGroup(groupId);
            if (group.isEmpty()) {
                continue;
            }
            responses.add(new GroupSummaryResponse(groupId, group.get().getGroupName(), group.get().getAvatarUrl()));
        }
        return responses;
    }

    private String resolveGroupId(UserConversation conversation) {
        if (conversation == null) {
            return null;
        }
        if (conversation.getTargetId() != null && !conversation.getTargetId().isBlank()
                && conversation.getChatType() == ChatType.GROUP.getCode()) {
            return conversation.getTargetId();
        }
        String conversationId = conversation.getConversationId();
        if (conversationId == null) {
            return null;
        }
        if (conversationId.startsWith("g:")) {
            return conversationId.substring("g:".length());
        }
        return null;
    }

    public record GroupSummaryResponse(String groupId, String groupName, String avatarUrl) {
    }
}
