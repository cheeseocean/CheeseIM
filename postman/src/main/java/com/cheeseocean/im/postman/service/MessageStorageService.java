package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.postman.entity.MessageMongo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface MessageStorageService {

    Long generateSeq(String conversationId);

    List<MessageMongo> getMessagesBySeqRange(String conversationId, Long startSeq, Long endSeq);

    Page<MessageMongo> getConversationHistory(String conversationId, Pageable pageable);

    Page<MessageMongo> getSingleChatHistory(String userId1, String userId2, Pageable pageable);

    Page<MessageMongo> getGroupChatHistory(String groupId, Pageable pageable);

    Page<MessageMongo> searchUserMessages(String userId, String keyword, Pageable pageable);

    Optional<MessageMongo> getMessageByServerMsgID(String serverMsgId);

    default Optional<MessageMongo> findByServerMsgID(String serverMsgId) {
        return getMessageByServerMsgID(serverMsgId);
    }

    boolean markMessagesAsRead(String userId, List<String> serverMsgIDs);

    default boolean markMessagesAsRead(List<String> serverMsgIDs) {
        return markMessagesAsRead(null, serverMsgIDs);
    }

    boolean revokeMessage(String userId, String serverMsgId);

    boolean deleteMessage(String serverMsgId);
}
