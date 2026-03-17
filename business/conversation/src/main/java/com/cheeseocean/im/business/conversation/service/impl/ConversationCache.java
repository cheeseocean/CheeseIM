package com.cheeseocean.im.business.conversation.service.impl;

import com.cheeseocean.im.business.conversation.api.param.Conversation;
import com.cheeseocean.im.business.conversation.entity.VersionLogMongo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationCache {

    private final Map<String, Conversation> conversationCache = new ConcurrentHashMap<>();
    private final Map<String, List<Conversation>> userConversationCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userConversationIdsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> userConversationHashCache = new ConcurrentHashMap<>();
    private final Map<String, VersionLogMongo> versionLogCache = new ConcurrentHashMap<>();
    private final Map<String, Long> userMaxVersionCache = new ConcurrentHashMap<>();

    public Conversation getConversation(String ownerUserId, String conversationId) {
        return conversationCache.get(key(ownerUserId, conversationId));
    }

    public void setConversation(Conversation conversation) {
        if (conversation != null) {
            conversationCache.put(key(conversation.getOwnerUserID(), conversation.getConversationID()), conversation);
        }
    }

    public List<Conversation> getUserConversations(String ownerUserId) {
        return userConversationCache.get(ownerUserId);
    }

    public void setUserConversations(String ownerUserId, List<Conversation> conversations) {
        if (conversations == null) {
            userConversationCache.remove(ownerUserId);
            return;
        }
        userConversationCache.put(ownerUserId, List.copyOf(conversations));
    }

    public void deleteUserConversations(String ownerUserId) {
        userConversationCache.remove(ownerUserId);
    }

    public List<String> getUserConversationIDs(String userId) {
        return userConversationIdsCache.get(userId);
    }

    public void setUserConversationIDs(String userId, List<String> conversationIds) {
        if (conversationIds == null) {
            userConversationIdsCache.remove(userId);
            return;
        }
        userConversationIdsCache.put(userId, List.copyOf(conversationIds));
    }

    public Long getUserConversationIDsHash(String ownerUserId) {
        return userConversationHashCache.get(ownerUserId);
    }

    public void setUserConversationIDsHash(String ownerUserId, Long hash) {
        if (hash == null) {
            userConversationHashCache.remove(ownerUserId);
            return;
        }
        userConversationHashCache.put(ownerUserId, hash);
    }

    public VersionLogMongo getVersionLog(String userId, Long version) {
        return versionLogCache.get(key(userId, String.valueOf(version)));
    }

    public void setVersionLog(VersionLogMongo versionLog) {
        if (versionLog != null) {
            versionLogCache.put(key(versionLog.getUserID(), String.valueOf(versionLog.getVersion())), versionLog);
        }
    }

    public Long getUserMaxVersion(String userId) {
        return userMaxVersionCache.get(userId);
    }

    public void setUserMaxVersion(String userId, Long version) {
        if (version == null) {
            userMaxVersionCache.remove(userId);
            return;
        }
        userMaxVersionCache.put(userId, version);
    }

    private String key(String left, String right) {
        return left + ":" + right;
    }
}
