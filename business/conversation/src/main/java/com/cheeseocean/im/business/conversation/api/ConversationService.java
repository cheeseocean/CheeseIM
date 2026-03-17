package com.cheeseocean.im.business.conversation.api;


import com.cheeseocean.im.business.conversation.api.param.*;
import com.cheeseocean.im.common.entity.conversation.GetAllConversationsReq;
import com.cheeseocean.im.common.entity.conversation.GetAllConversationsResp;

import java.util.List;

/**
 * conversation service api
 * @author xxxcrel
 */
public interface ConversationService {

    /**
     * get conversation by conversation id
     * @param userID
     * @param conversationId
     * @return
     */
    Conversation getConversation(String userID, String conversationId);

    /**
     * get all conversation by conversation id list
     * @param userID
     * @param conversationIds null will get all conversation
     * @return conversation list
     */
    List<Conversation> getConversations(String userID, List<String> conversationIds);

    /**
     * get all conversation id list by user id
     * @param userID
     * @return conversation id list
     */
    List<String> getConversationIDs(String userID);

    /**
     * create single chat conversation
     * @param request
     */
    void createSingleChatConversation(CreateSingleChatReq request);

    /**
     * create group chat conversation
     * @param request
     */
    void createGroupChatConversation(CreateGroupChatReq request);

    Conversation createSingleConversation(String userID, String friendUserID);

    Conversation createGroupConversation(String userID, String groupID);

    GetAllConversationsResp getAllConversations(GetAllConversationsReq request);

    Boolean markConversationAsRead(String userID, String conversationID, Long seq);

    Boolean setConversationDraft(String userID, String conversationID, String draftText);

    Boolean deleteConversation(String userID, String conversationID);
}
