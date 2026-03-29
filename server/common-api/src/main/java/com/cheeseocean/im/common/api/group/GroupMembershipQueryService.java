package com.cheeseocean.im.common.api.group;

import java.util.List;

public interface GroupMembershipQueryService {

    List<String> queryConversationMembers(String conversationId);

    List<String> queryGroupMembers(String groupId);

    boolean isGroupMember(String groupId, String userId);
}
