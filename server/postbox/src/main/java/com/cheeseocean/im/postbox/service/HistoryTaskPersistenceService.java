package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.dto.HistoryTask;
import com.cheeseocean.im.postbox.entity.InboxDocument;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.repository.InboxDocumentRepository;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class HistoryTaskPersistenceService {

    private final MessageDocumentRepository messageRepository;
    private final InboxDocumentRepository inboxRepository;

    public HistoryTaskPersistenceService(MessageDocumentRepository messageRepository,
                                         InboxDocumentRepository inboxRepository) {
        this.messageRepository = messageRepository;
        this.inboxRepository = inboxRepository;
    }

    public PersistedHistory persist(HistoryTask task) {
        UpsertedMessage upsertedMessage = upsertMessage(task);
        List<Long> storedSequences = new ArrayList<>();
        boolean newInboxCreated = false;
        for (String receiverId : resolveReceivers(task)) {
            UpsertedInbox upsertedInbox = upsertInbox(upsertedMessage.message(), receiverId);
            InboxDocument inbox = upsertedInbox.inbox();
            storedSequences.add(inbox.getSequence() == null ? 0L : inbox.getSequence());
            newInboxCreated = newInboxCreated || upsertedInbox.created();
        }
        return new PersistedHistory(upsertedMessage.message(), storedSequences, upsertedMessage.created() || newInboxCreated);
    }

    private UpsertedMessage upsertMessage(HistoryTask task) {
        if (messageRepository.existsById(task.getMessageId())) {
            MessageDocument existing = messageRepository.findById(task.getMessageId())
                    .orElseGet(() -> messageRepository.findByServerMsgId(task.getMessageId()));
            return new UpsertedMessage(existing, false);
        }
        MessageDocument document = new MessageDocument();
        document.setServerMsgId(task.getMessageId());
        document.setClientMsgId(task.getClientMsgId());
        document.setConversationId(task.getConversationId());
        document.setSenderId(task.getSenderId());
        document.setReceiverId(task.getReceiverId());
        document.setContent(task.getContent());
        document.setContentType(task.getContentType());
        document.setAttachedInfo(task.getAttachedInfo());
        document.setSequence(task.getConversationSeq());
        document.setCreatedAt(Instant.now());
        return new UpsertedMessage(messageRepository.save(document), true);
    }

    private UpsertedInbox upsertInbox(MessageDocument message, String receiverId) {
        String id = receiverId + ":" + message.getServerMsgId();
        if (inboxRepository.existsById(id)) {
            return new UpsertedInbox(inboxRepository.findById(id).orElseThrow(), false);
        }
        InboxDocument inbox = new InboxDocument();
        inbox.setId(id);
        inbox.setUserId(receiverId);
        inbox.setServerMsgId(message.getServerMsgId());
        inbox.setConversationId(message.getConversationId());
        inbox.setSequence(message.getSequence());
        inbox.setRead(false);
        inbox.setDeliveredAt(null);
        inbox.setCreatedAt(Instant.now());
        return new UpsertedInbox(inboxRepository.save(inbox), true);
    }

    private List<String> resolveReceivers(HistoryTask task) {
        if (task.getReceiverId() != null && !task.getReceiverId().isBlank()) {
            return List.of(task.getReceiverId());
        }
        return task.getTargetUserIds() == null ? List.of() : task.getTargetUserIds();
    }

    public static final class PersistedHistory {
        private final MessageDocument message;
        private final List<Long> storedSequences;
        private final boolean newlyPersisted;

        public PersistedHistory(MessageDocument message, List<Long> storedSequences, boolean newlyPersisted) {
            this.message = message;
            this.storedSequences = List.copyOf(storedSequences);
            this.newlyPersisted = newlyPersisted;
        }

        public MessageDocument getMessage() {
            return message;
        }

        public List<Long> getStoredSequences() {
            return storedSequences;
        }

        public long firstStoredSequence() {
            return storedSequences.isEmpty() ? 0L : storedSequences.get(0);
        }

        public boolean isNewlyPersisted() {
            return newlyPersisted;
        }
    }

    public record UpsertedMessage(MessageDocument message, boolean created) {
    }

    public record UpsertedInbox(InboxDocument inbox, boolean created) {
    }
}
