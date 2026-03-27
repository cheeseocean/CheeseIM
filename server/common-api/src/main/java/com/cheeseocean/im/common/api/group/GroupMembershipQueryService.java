package com.cheeseocean.im.common.api.group;

import java.util.List;

public interface GroupMembershipQueryService {

    List<String> queryMembers(String conversationId);
}
