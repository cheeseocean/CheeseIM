package com.cheeseocean.im.postmaster.history;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.util.BlockIndexUtil;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BlockHistoryPersistenceService {

    private final MongoTemplate              mongoTemplate;
    private final MessageIdMappingRepository mappingRepository;

    public BlockHistoryPersistenceService(MongoTemplate mongoTemplate,
                                          MessageIdMappingRepository mappingRepository) {
        this.mongoTemplate = mongoTemplate;
        this.mappingRepository = mappingRepository;
    }

    public void persist(HistoryEvent event) {
        Map<Long, List<Message>> byBlock = event.getMessages().stream()
                .collect(Collectors.groupingBy(message -> BlockIndexUtil.blockNo(message.getSeq())));

        for (Map.Entry<Long, List<Message>> entry : byBlock.entrySet()) {
            persistBlock(event.getConversationId(), entry.getKey(), entry.getValue());
        }
    }

    private void persistBlock(String conversationId, long blockNo, List<Message> messages) {
        String docId = conversationId + ":" + blockNo;
        Query  query = Query.query(Criteria.where("_id").is(docId));
        Update update = new Update()
                .setOnInsert("_id", docId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("blockNo", blockNo)
                .setOnInsert("createdAt", Instant.now())
                .set("updatedAt", Instant.now());

        long startSeq = blockNo * BlockIndexUtil.BLOCK_SIZE + 1L;
        long endSeq   = startSeq + BlockIndexUtil.BLOCK_SIZE - 1L;
        update.setOnInsert("startSeq", startSeq);
        update.setOnInsert("endSeq", endSeq);

        for (Message message : messages) {
            update.set("messages." + BlockIndexUtil.index(message.getSeq()), toSlot(message));
            mappingRepository.save(toMapping(conversationId, message));
        }

        mongoTemplate.upsert(query, update, MessageBlockDoc.class);
    }

    private MessageSlot toSlot(Message message) {
        MessageSlot slot = new MessageSlot();
        slot.setSeq(message.getSeq());
        slot.setClientMsgId(message.getClientMsgId());
        slot.setServerMsgId(message.getServerMsgId());
        slot.setSenderId(message.getSenderId());
        slot.setReceiverId(message.getReceiverId());
        slot.setGroupId(message.getGroupId());
        slot.setSessionType(message.getSessionType() == null ? null : message.getSessionType().getCode());
        slot.setContentType(message.getContentType() == null ? null : message.getContentType().getCode());
        slot.setContent(message.getContent());
        slot.setSendTime(message.getSendTime());
        slot.setCreateTime(message.getCreateTime());
        slot.setStatus(message.getStatus() == null ? null : message.getStatus().getCode());
        slot.setPlatformType(message.getPlatformType() == null ? null : message.getPlatformType().getCode());
        slot.setUniqueId(message.getUniqueId());
        slot.setSource(message.getSource() == null ? null : message.getSource().getCode());
        slot.setOptions(message.getOptions());
        slot.setAttributes(message.getAttributes());
        return slot;
    }

    private MessageIdMappingDoc toMapping(String conversationId, Message message) {
        MessageIdMappingDoc mapping = new MessageIdMappingDoc();
        mapping.setId(conversationId + ":" + message.getClientMsgId());
        mapping.setConversationId(conversationId);
        mapping.setClientMsgId(message.getClientMsgId());
        mapping.setServerMsgId(message.getServerMsgId());
        mapping.setSeq(message.getSeq());
        mapping.setSenderId(message.getSenderId());
        mapping.setSendTime(message.getSendTime());
        mapping.setCreatedAt(Instant.now());
        return mapping;
    }
}
