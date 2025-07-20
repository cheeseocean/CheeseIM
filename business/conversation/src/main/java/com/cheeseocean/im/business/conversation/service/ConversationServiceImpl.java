package com.cheeseocean.im.business.conversation.service;

import com.cheeseocean.im.business.conversation.api.ConversationService;
import com.cheeseocean.im.business.conversation.api.param.Conversation;
import com.cheeseocean.im.business.conversation.api.param.CreateGroupChatReq;
import com.cheeseocean.im.business.conversation.api.param.CreateSingleChatReq;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * conversation service implement
 * @author xxxcrel
 */
@Service
@DubboService(interfaceClass = ConversationService.class)
public class ConversationServiceImpl implements ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationServiceImpl.class);

    @Autowired
    private ConversationStorageService conversationStorageService;

    @Override
    public Conversation getConversation(String userID, String conversationId) {
        return null;
    }

    @Override
    public List<Conversation> getConversations(String userID, List<String> conversationIds) {
        return List.of();
    }

    @Override
    public List<String> getConversationIDs(String userID) {
        return List.of();
    }

    @Override
    public void createSingleChatConversation(CreateSingleChatReq request) {

    }

    @Override
    public void createGroupChatConversation(CreateGroupChatReq request) {

    }
}
