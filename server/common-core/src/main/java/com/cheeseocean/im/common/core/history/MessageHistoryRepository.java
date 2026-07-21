package com.cheeseocean.im.common.core.history;

import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.history.model.AttachmentMetadata;
import com.cheeseocean.im.common.core.history.model.MessageBlock;
import com.cheeseocean.im.common.core.history.model.MessageIdMapping;
import com.cheeseocean.im.common.core.history.model.MessageMutation;
import com.cheeseocean.im.common.core.history.model.MessageSlot;

import java.util.List;
import java.time.Instant;

/**
 * 消息历史的唯一持久化入口。
 *
 * <p>postbox 只读、postmaster 写入和撤回查询均经此接口，业务模块不得再直接使用 MongoTemplate。
 */
public interface MessageHistoryRepository {
    void persist(HistoryEvent event);
    List<MessageBlock> findRecentBlocks(String conversationId, int limit, int maxWindows);
    List<MessageBlock> findBlocksBySeqRange(String conversationId, long beginSeq, long endSeq);
    List<MessageIdMapping> findRecentMappings(int limit);
    MessageSlot findSlot(String conversationId, long seq);
    AttachmentMetadata findAttachmentMetadata(String attachmentId);
    List<MessageMutation> findRevokedMutations(List<String> serverMsgIds);
    MessageIdMapping findMappingByServerMessageId(String serverMsgId);
    MessageMutation findMutationById(String mutationId);
    MessageMutation upsertMutation(MessageMutation mutation);
    List<MessageMutation> findMutationsAfter(String conversationId, Instant afterCreatedAt,
                                              String afterMutationId, int limit);
}
