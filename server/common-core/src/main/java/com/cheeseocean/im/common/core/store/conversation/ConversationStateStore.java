package com.cheeseocean.im.common.core.store.conversation;

public interface ConversationStateStore {

    void setConversationMinSeqIfAbsent(String conversationId, long seq);

    void setConversationMaxSeq(String conversationId, long seq);

    Long getConversationMaxSeq(String conversationId);

    void setUserMaxSeq(String userId, String conversationId, long seq);

    void setUserReadSeq(String userId, String conversationId, long seq);

    void incrementUnread(String userId, String conversationId);

    int getUnread(String userId, String conversationId);

    void setLastMessageSummary(String conversationId, String summary);

    String getLastMessageSummary(String conversationId);
}
