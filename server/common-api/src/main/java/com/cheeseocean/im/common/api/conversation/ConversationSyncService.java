package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.dto.conversation.ConversationReadSnapshot;
import com.cheeseocean.im.common.api.dto.conversation.PullMessages;
import com.cheeseocean.im.common.api.dto.conversation.SeqRangeRequest;

import java.util.List;
import java.util.Map;

/**
 * 会话消息同步服务。
 *
 * <p>统一承载客户端断线重连、前后台切换和 gap repair 场景下的
 * maxSeq/readSeq 查询与按 seq 范围拉取消息能力。
 */
public interface ConversationSyncService {

    /**
     * 查询用户可见会话的当前服务端最大序列号。
     *
     * <p>当 {@code conversationIds} 为空时，返回用户全部会话的最大序列号映射。
     */
    Map<String, Long> getConversationMaxSeqs(String userId, List<String> conversationIds);

    /**
     * 按会话维度的 seq 区间拉取历史消息。
     */
    PullMessages pullMessagesBySeqRanges(String userId, List<SeqRangeRequest> ranges, int limitPerConversation);

    /**
     * 查询用户可见会话的 readSeq/maxSeq/unread 快照。
     *
     * <p>当 {@code conversationIds} 为空时，返回用户全部会话的快照映射。
     */
    Map<String, ConversationReadSnapshot> getConversationReadSnapshots(String userId, List<String> conversationIds);

    /**
     * 确认用户在指定会话中的已读位点。
     */
    void ackReadSeq(String userId, String conversationId, long readSeq);
}
