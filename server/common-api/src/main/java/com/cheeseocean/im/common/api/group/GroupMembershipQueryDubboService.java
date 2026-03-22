package com.cheeseocean.im.common.api.group;

import java.util.List;

public interface GroupMembershipQueryDubboService {

    List<String> queryMembers(String conversationId);
}
