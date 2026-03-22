package com.cheeseocean.im.postbox.policy;

import com.cheeseocean.im.common.api.friend.FriendRelationService;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Component
public class DirectChatPolicy {

    @DubboReference(check = false)
    private FriendRelationService friendRelationService;

    public boolean canAccess(String conversationId, String userId) {
        if (conversationId == null || userId == null) {
            return false;
        }
        String peerUserId = ConversationIdUtil.peerUser(conversationId, userId);
        return peerUserId != null && friendRelationService != null && friendRelationService.areAcceptedFriends(userId, peerUserId);
    }
}
