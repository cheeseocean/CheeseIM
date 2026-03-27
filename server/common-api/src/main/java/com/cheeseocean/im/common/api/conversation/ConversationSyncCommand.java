package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.dto.message.SequencedMessage;

import java.util.List;

/**
 * Carries everything ConversationSyncDubboService needs to sync one conversation batch.
 *
 * @param conversationId     Physical conversation ID shared by all messages in the batch.
 * @param sessionType        SessionType code (1=single, 2=group, 3=notification).
 * @param newConversation    True when this batch contains the very first storage message
 *                           (seq == 1), meaning conversation records must be created.
 * @param latestMessage      The highest-seq message in the batch — used to populate
 *                           latestMsgSeq / latestMsg on the conversation record.
 * @param allParticipants    Every user who has a conversation view (targets + senders,
 *                           de-duplicated, ordered by first appearance).
 * @param senderIds          One entry per storage message in sequence order.
 *                           Used to compute per-participant unread delta:
 *                           unread(P) = count of senders != P.
 */
public record ConversationSyncCommand(
        String conversationId,
        int sessionType,
        boolean newConversation,
        SequencedMessage latestMessage,
        List<String> allParticipants,
        List<String> senderIds
) {}
