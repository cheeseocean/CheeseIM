package com.cheeseocean.im.common.core.store.conversation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface ConversationStateStore {

    /** 已读状态原子推进结果。 */
    record ReadState(long readSeq, int unread, boolean changed) {
    }

    void setConversationMinSeqIfAbsent(String conversationId, long seq);

    void setConversationMaxSeq(String conversationId, long seq);

    Long getConversationMaxSeq(String conversationId);

    void setUserMaxSeq(String userId, String conversationId, long seq);

    /** 原子推进用户 maxSeq，并按实际新增 seq 差值增加未读。 */
    void advanceUserMaxSeq(String userId, String conversationId, long maxSeq, boolean countUnread);

    Long getUserMaxSeq(String userId, String conversationId);

    void setUserReadSeq(String userId, String conversationId, long seq);

    Long getUserReadSeq(String userId, String conversationId);

    /**
     * 原子推进用户已读水位并按当前最大消息序号重算未读数。
     *
     * <p>{@code knownReadSeq} 与 {@code knownMaxSeq} 用于热状态缺失时从持久层引导状态；
     * 实现必须保证已读水位不回退，并返回存储层实际采用的结果。
     */
    ReadState advanceReadState(String userId, String conversationId, long requestedReadSeq,
                               long knownReadSeq, long knownMaxSeq);

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
