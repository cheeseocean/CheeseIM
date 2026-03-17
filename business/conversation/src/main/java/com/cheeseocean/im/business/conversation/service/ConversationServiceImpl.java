package com.cheeseocean.im.business.conversation.service;

import com.cheeseocean.im.business.conversation.api.ConversationService;
import com.cheeseocean.im.business.conversation.api.param.Conversation;
import com.cheeseocean.im.business.conversation.api.param.CreateGroupChatReq;
import com.cheeseocean.im.business.conversation.api.param.CreateSingleChatReq;
import com.cheeseocean.im.common.entity.conversation.GetAllConversationsReq;
import com.cheeseocean.im.common.entity.conversation.GetAllConversationsResp;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    @Override
    public Conversation getConversation(String userID, String conversationId) {
        return conversations.get(key(userID, conversationId));
    }

    @Override
    public List<Conversation> getConversations(String userID, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return conversations.values().stream()
                    .filter(conversation -> userID.equals(conversation.getUserID()))
                    .toList();
        }
        return conversationIds.stream()
                .map(conversationId -> conversations.get(key(userID, conversationId)))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public List<String> getConversationIDs(String userID) {
        return getConversations(userID, null).stream()
                .map(Conversation::getConversationID)
                .toList();
    }

    @Override
    public void createSingleChatConversation(CreateSingleChatReq request) {
        createSingleConversation(request.getSenderId(), request.getReceiverId());
    }

    @Override
    public void createGroupChatConversation(CreateGroupChatReq request) {
        if (request.getUserIDs() != null) {
            for (String userID : request.getUserIDs()) {
                createGroupConversation(userID, request.getGroupID());
            }
        }
    }

    @Override
    public Conversation createSingleConversation(String userID, String friendUserID) {
        Conversation conversation = baseConversation(userID);
        conversation.setUserID(userID);
        conversation.setConversationID("single_" + userID + "_" + friendUserID);
        conversations.put(key(userID, conversation.getConversationID()), conversation);
        return conversation;
    }

    @Override
    public Conversation createGroupConversation(String userID, String groupID) {
        Conversation conversation = baseConversation(userID);
        conversation.setUserID(userID);
        conversation.setGroupID(groupID);
        conversation.setConversationID("group_" + groupID);
        conversations.put(key(userID, conversation.getConversationID()), conversation);
        return conversation;
    }

    @Override
    public GetAllConversationsResp getAllConversations(GetAllConversationsReq request) {
        GetAllConversationsResp response = new GetAllConversationsResp();
        response.setConversations(new ArrayList<>(getConversations(request.getUserID(), null)));
        return response;
    }

    @Override
    public Boolean markConversationAsRead(String userID, String conversationID, Long seq) {
        return conversations.containsKey(key(userID, conversationID));
    }

    @Override
    public Boolean setConversationDraft(String userID, String conversationID, String draftText) {
        Conversation conversation = conversations.get(key(userID, conversationID));
        if (conversation == null) {
            return false;
        }
        conversation.setDraftText(draftText);
        return true;
    }

    @Override
    public Boolean deleteConversation(String userID, String conversationID) {
        return conversations.remove(key(userID, conversationID)) != null;
    }

    private Conversation baseConversation(String userID) {
        Conversation conversation = new Conversation();
        conversation.setOwnerUserID(userID);
        conversation.setUserID(userID);
        conversation.setCreateTime(LocalDateTime.now());
        return conversation;
    }

    private String key(String userID, String conversationID) {
        return userID + ":" + conversationID;
    }
}
