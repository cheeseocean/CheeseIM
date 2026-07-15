package com.cheeseocean.im.common.core.history;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.history.document.AttachmentMetadataDoc;
import com.cheeseocean.im.common.core.history.document.MessageBlockDoc;
import com.cheeseocean.im.common.core.history.document.MessageIdMappingDoc;
import com.cheeseocean.im.common.core.history.document.MessageMutationDoc;
import com.cheeseocean.im.common.core.history.document.MessageSlot;

import java.util.List;
import java.time.Instant;

/**
 * 消息历史的唯一持久化入口。
 *
 * <p>postbox 只读、postmaster 写入和撤回查询均经此接口，业务模块不得再直接使用 MongoTemplate。
 */
public interface MessageHistoryRepository {
    void persist(HistoryEvent event);
    List<MessageBlockDoc> findRecentBlocks(String conversationId, int limit, int maxWindows);
    List<MessageBlockDoc> findBlocksBySeqRange(String conversationId, long beginSeq, long endSeq);
    List<MessageIdMappingDoc> findRecentMappings(int limit);
    MessageSlot findSlot(String conversationId, long seq);
    AttachmentMetadataDoc findAttachmentMetadata(String attachmentId);
    List<MessageMutationDoc> findRevokedMutations(List<String> serverMsgIds);
    MessageIdMappingDoc findMappingByServerMessageId(String serverMsgId);
    MessageMutationDoc findMutationById(String mutationId);
    MessageMutationDoc upsertMutation(MessageMutationDoc mutation);
    List<MessageMutationDoc> findMutationsAfter(String conversationId, Instant afterCreatedAt,
                                                 String afterMutationId, int limit);
}
