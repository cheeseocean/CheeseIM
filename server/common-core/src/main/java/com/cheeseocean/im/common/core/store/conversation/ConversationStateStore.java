package com.cheeseocean.im.common.core.store.conversation;

public interface ConversationStateStore {

    void setConversationMinSeqIfAbsent(String conversationId, long seq);

    void setConversationMaxSeq(String conversationId, long seq);

    Long getConversationMaxSeq(String conversationId);

    void setUserMaxSeq(String userId, String conversationId, long seq);

    void setUserReadSeq(String userId, String conversationId, long seq);

    void incrementUnread(String userId, String conversationId);

    /** Atomically add {@code delta} to the unread counter in one operation. */
    default void incrementUnreadBy(String userId, String conversationId, int delta) {
        for (int i = 0; i < delta; i++) {
            incrementUnread(userId, conversationId);
        }
    }

    int getUnread(String userId, String conversationId);

    void setLastMessageSummary(String conversationId, String summary);

    String getLastMessageSummary(String conversationId);
}
