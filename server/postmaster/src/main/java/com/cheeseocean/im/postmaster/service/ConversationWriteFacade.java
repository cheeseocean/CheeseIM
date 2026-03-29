package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.conversation.ConversationWriteService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationWriteFacade {

    @DubboReference(check = false)
    private ConversationWriteService conversationWriteService;

    public ConversationWriteFacade() {
    }

    ConversationWriteFacade(ConversationWriteService conversationWriteService) {
        this.conversationWriteService = conversationWriteService;
    }

    public void createSingleChatConversation(String senderId, String recvId,
                                             String conversationId, int conversationType) {
        conversationWriteService.createSingleChatConversation(
                senderId, recvId, conversationId, conversationType);
    }

    public void createGroupChatConversations(String groupId, String conversationId, List<String> userIds) {
        conversationWriteService.createGroupChatConversations(groupId, conversationId, userIds);
    }
}
