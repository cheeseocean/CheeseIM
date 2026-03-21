package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.InboxMessage;
import com.cheeseocean.im.common.entity.StoredMessage;
import com.cheeseocean.im.common.dto.HistoryTask;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.entity.ConversationReadCursorDocument;
import com.cheeseocean.im.postbox.repository.ConversationReadCursorRepository;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class MessageStoreServiceImpl implements MessageStoreService {

    private final MessageDocumentRepository messageRepository;
    private final InboxDocumentRepository inboxRepository;
    private final ConversationReadCursorRepository readCursorRepository;
    private final HistoryTaskPersistenceService historyTaskPersistenceService;

    public MessageStoreServiceImpl(MessageDocumentRepository messageRepository,
                                   InboxDocumentRepository inboxRepository,
                                   ConversationReadCursorRepository readCursorRepository,
                                   HistoryTaskPersistenceService historyTaskPersistenceService) {
        this.messageRepository = messageRepository;
        this.inboxRepository = inboxRepository;
        this.readCursorRepository = readCursorRepository;
        this.historyTaskPersistenceService = historyTaskPersistenceService;
    }

    public MessageStoreServiceImpl(MessageDocumentRepository messageRepository,
                                   InboxDocumentRepository inboxRepository,
                                   ConversationReadCursorRepository readCursorRepository) {
        this(messageRepository, inboxRepository, readCursorRepository, null);
    }

    public MessageStoreServiceImpl(MessageDocumentRepository messageRepository,
                                   InboxDocumentRepository inboxRepository) {
        this(messageRepository, inboxRepository, null, null);
    }

    public MessageStoreServiceImpl(MessageDocumentRepository messageRepository,
                                   InboxDocumentRepository inboxRepository,
                                   HistoryTaskPersistenceService historyTaskPersistenceService) {
        this(messageRepository, inboxRepository, null, historyTaskPersistenceService);
    }

    @Override
    public StoredMessage saveMessage(StoredMessage message) {
        MessageDocument saved = messageRepository.save(toMessageDocument(message));
        return toStoredMessage(saved);
    }

    @Override
    public long saveOfflineMessage(MessageProto message) {
        HistoryTask task = HistoryTask.singleFromProto(message);
        return historyTaskPersistenceService.persist(task).firstStoredSequence();
    }

    @Override
    public List<Long> saveOfflineMessages(MessageProto message, List<String> receiverIds) {
        HistoryTask task = HistoryTask.singleFromProto(message);
        task.setReceiverId(null);
        task.setTargetUserIds(receiverIds);
        return historyTaskPersistenceService.persist(task).getStoredSequences();
    }

    @Override
    public List<InboxMessage> getOfflineMessages(String userId, int limit) {
        return inboxRepository.findByUserIdAndReadIsFalseOrderBySequenceAsc(userId).stream()
                .limit(limit)
                .map(this::toInboxMessage)
                .toList();
    }

    @Override
    public void markDelivered(String userId, String serverMsgId) {
        inboxRepository.findById(userId + ":" + serverMsgId)
                .ifPresent(item -> {
                    item.setDeliveredAt(Instant.now());
                    inboxRepository.save(item);
                });
    }

    @Override
    public DeliveryResult applyAck(DeliveryAck ack) {
        InboxDocument inbox = inboxRepository.findById(ack.getUserId() + ":" + ack.getServerMsgId()).orElse(null);
        DeliveryResult result = new DeliveryResult();
        result.setServerMsgId(ack.getServerMsgId());

        if ("READ".equals(ack.getAckType())) {
            markConversationRead(ack);
            result.setSuccess(true);
            result.setStatus(DeliveryState.READ.name());
            result.setState(DeliveryState.READ);
            return result;
        }

        if ("RECEIVED".equals(ack.getAckType())) {
            if (inbox != null && inbox.getDeliveredAt() == null) {
                inbox.setDeliveredAt(resolveAckTime(ack));
                inboxRepository.save(inbox);
            }
            result.setSuccess(true);
            if (inbox != null && inbox.isRead()) {
                result.setStatus(DeliveryState.READ.name());
                result.setState(DeliveryState.READ);
            } else {
                result.setStatus(DeliveryState.ONLINE_CONFIRMED.name());
                result.setState(DeliveryState.ONLINE_CONFIRMED);
            }
            return result;
        }

        if ("RECALL".equals(ack.getAckType())) {
            if (inbox != null && inbox.isRead()) {
                result.setSuccess(false);
                result.setStatus("RECALL_REJECTED_AFTER_READ");
                result.setState(DeliveryState.FAILED_FINAL);
                return result;
            }
            MessageDocument message = messageRepository.findByServerMsgId(ack.getServerMsgId());
            if (message != null) {
                message.setContent("[消息已撤回]");
                messageRepository.save(message);
            }
            result.setSuccess(true);
            result.setStatus(DeliveryState.RECALLED.name());
            result.setState(DeliveryState.RECALLED);
            return result;
        }

        result.setSuccess(false);
        result.setStatus("UNSUPPORTED_ACK");
        result.setState(DeliveryState.FAILED_FINAL);
        return result;
    }

    private void markConversationRead(DeliveryAck ack) {
        Instant ackTime = resolveAckTime(ack);
        if (ack.getConversationId() != null && ack.getSeq() != null) {
            List<InboxDocument> inboxes = inboxRepository.findByUserIdAndConversationIdOrderBySequenceDesc(
                    ack.getUserId(), ack.getConversationId());
            for (InboxDocument item : inboxes) {
                if (item.getSequence() != null && item.getSequence() <= ack.getSeq() && !item.isRead()) {
                    item.setRead(true);
                    if (item.getDeliveredAt() == null) {
                        item.setDeliveredAt(ackTime);
                    }
                    inboxRepository.save(item);
                }
            }
            advanceReadCursor(ack.getUserId(), ack.getConversationId(), ack.getSeq(), ackTime);
            return;
        }
        if (ack.getServerMsgId() == null) {
            return;
        }
        inboxRepository.findById(ack.getUserId() + ":" + ack.getServerMsgId()).ifPresent(item -> {
            item.setRead(true);
            if (item.getDeliveredAt() == null) {
                item.setDeliveredAt(ackTime);
            }
            inboxRepository.save(item);
            advanceReadCursor(item.getUserId(), item.getConversationId(), item.getSequence(), ackTime);
        });
    }

    private void advanceReadCursor(String userId, String conversationId, Long seq, Instant updatedAt) {
        if (readCursorRepository == null || userId == null || conversationId == null || seq == null) {
            return;
        }
        ConversationReadCursorDocument existing = readCursorRepository.findByUserIdAndConversationId(userId, conversationId);
        if (existing != null && existing.getReadSeq() != null && existing.getReadSeq() >= seq) {
            return;
        }
        ConversationReadCursorDocument cursor = existing == null ? new ConversationReadCursorDocument() : existing;
        cursor.setId(userId + ":" + conversationId);
        cursor.setUserId(userId);
        cursor.setConversationId(conversationId);
        cursor.setReadSeq(seq);
        cursor.setUpdatedAt(updatedAt);
        readCursorRepository.save(cursor);
    }

    private Instant resolveAckTime(DeliveryAck ack) {
        return ack.getEventTime() == null ? Instant.now() : Instant.ofEpochMilli(ack.getEventTime());
    }

    private InboxMessage toInboxMessage(InboxDocument inbox) {
        InboxMessage message = new InboxMessage();
        message.setUserId(inbox.getUserId());
        message.setServerMsgId(inbox.getServerMsgId());
        message.setConversationId(inbox.getConversationId());
        message.setSequence(inbox.getSequence());
        message.setRead(inbox.isRead());
        message.setAvailableAt(inbox.getCreatedAt());
        return message;
    }

    private MessageDocument toMessageDocument(StoredMessage message) {
        MessageDocument document = new MessageDocument();
        document.setServerMsgId(message.getServerMsgId());
        document.setClientMsgId(message.getClientMsgId());
        document.setConversationId(message.getConversationId());
        document.setSenderId(message.getSenderId());
        document.setReceiverId(message.getReceiverId());
        document.setContent(message.getContent());
        document.setContentType(message.getContentType());
        document.setAttachedInfo(message.getAttachedInfo());
        document.setCreatedAt(message.getCreatedAt() == null ? Instant.now() : message.getCreatedAt());
        return document;
    }

    private MessageDocument toMessageDocument(MessageProto message) {
        MessageDocument document = new MessageDocument();
        document.setServerMsgId(message.getServerMsgId());
        document.setClientMsgId(message.getClientMsgId());
        document.setConversationId(message.getConversationId());
        document.setSenderId(message.getSenderId());
        document.setReceiverId(message.getReceiverId());
        document.setContent(message.getContent());
        document.setContentType(message.getContentType());
        document.setAttachedInfo(message.getAttachedInfo());
        document.setSequence(message.getSequence());
        document.setCreatedAt(Instant.now());
        return document;
    }

    private StoredMessage toStoredMessage(MessageDocument document) {
        StoredMessage message = new StoredMessage();
        message.setServerMsgId(document.getServerMsgId());
        message.setClientMsgId(document.getClientMsgId());
        message.setConversationId(document.getConversationId());
        message.setSenderId(document.getSenderId());
        message.setReceiverId(document.getReceiverId());
        message.setContent(document.getContent());
        message.setContentType(document.getContentType());
        message.setAttachedInfo(document.getAttachedInfo());
        message.setCreatedAt(document.getCreatedAt());
        return message;
    }
}
