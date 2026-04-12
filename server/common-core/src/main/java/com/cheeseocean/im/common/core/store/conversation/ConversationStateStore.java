package com.cheeseocean.im.common.core.store.conversation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface ConversationStateStore {

    void setConversationMinSeqIfAbsent(String conversationId, long seq);

    void setConversationMaxSeq(String conversationId, long seq);

    Long getConversationMaxSeq(String conversationId);

    void setUserMaxSeq(String userId, String conversationId, long seq);

    Long getUserMaxSeq(String userId, String conversationId);

    void setUserReadSeq(String userId, String conversationId, long seq);

    Long getUserReadSeq(String userId, String conversationId);

    void incrementUnread(String userId, String conversationId);

    /** Atomically add {@code delta} to the unread counter in one operation. */
    default void incrementUnreadBy(String userId, String conversationId, int delta) {
        for (int i = 0; i < delta; i++) {
            incrementUnread(userId, conversationId);
        }
    }

    int getUnread(String userId, String conversationId);

    void setUnread(String userId, String conversationId, int unreadCount);

    void setLastMessageSummary(String conversationId, String summary);

    String getLastMessageSummary(String conversationId);

    default Map<String, String> getLastMessageSummaries(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String conversationId : conversationIds) {
            if (conversationId == null || conversationId.isBlank()) {
                continue;
            }
            String summary = getLastMessageSummary(conversationId);
            if (summary != null) {
                result.put(conversationId, summary);
            }
        }
        return result;
    }
}
