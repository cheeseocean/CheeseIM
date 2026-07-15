package com.cheeseocean.im.common.core.history;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.history.document.AttachmentMetadataDoc;
import com.cheeseocean.im.common.core.history.document.MessageBlockDoc;
import com.cheeseocean.im.common.core.history.document.MessageIdMappingDoc;
import com.cheeseocean.im.common.core.history.document.MessageMutationDoc;
import com.cheeseocean.im.common.core.history.document.MessageSlot;
import com.cheeseocean.im.common.core.util.BlockIndexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Mongo 历史存储实现，集中维护 collection、索引和批量写语义。 */
@Repository
public class MongoMessageHistoryRepository implements MessageHistoryRepository {
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    public MongoMessageHistoryRepository(MongoTemplate mongoTemplate, ObjectMapper objectMapper) { this.mongoTemplate = mongoTemplate; this.objectMapper = objectMapper; }

    @Override public void persist(HistoryEvent event) {
        List<Message> messages = event.getMessages(); if (messages == null || messages.isEmpty()) return;
        BulkOperations mappings = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MessageIdMappingDoc.class);
        BulkOperations blocks = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MessageBlockDoc.class);
        BulkOperations attachments = null;
        for (Message message : messages) {
            Instant now = Instant.now(); String mappingId = event.getConversationId() + ":" + message.getClientMsgId();
            mappings.upsert(Query.query(Criteria.where("_id").is(mappingId)), new Update().setOnInsert("createdAt", now).set("conversationId", event.getConversationId()).set("clientMsgId", message.getClientMsgId()).set("serverMsgId", message.getServerMsgId()).set("seq", message.getSeq()).set("senderId", message.getSenderId()).set("sendTime", message.getSendTime()));
            if (message.getContentType() != null && message.getContentType().hasAttachment()) {
                String attachmentId = attachmentId(message.getContent());
                if (attachmentId != null) { if (attachments == null) attachments = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, AttachmentMetadataDoc.class);
                    attachments.upsert(Query.query(Criteria.where("_id").is(attachmentId)), new Update().setOnInsert("createdAt", now).set("conversationId", event.getConversationId()).set("serverMsgId", message.getServerMsgId()).set("clientMsgId", message.getClientMsgId()).set("seq", message.getSeq()).set("senderId", message.getSenderId()).set("contentType", message.getContentType().getCode()).set("sendTime", message.getSendTime())); }
            }
        }
        mappings.execute(); if (attachments != null) attachments.execute();
        for (Map.Entry<Long, List<Message>> entry : messages.stream().collect(Collectors.groupingBy(message -> BlockIndexUtil.blockNo(message.getSeq()))).entrySet()) {
            long blockNo = entry.getKey(); long start = blockNo * BlockIndexUtil.BLOCK_SIZE + 1L;
            Update update = new Update().setOnInsert("_id", event.getConversationId()+":"+blockNo).setOnInsert("conversationId", event.getConversationId()).setOnInsert("blockNo", blockNo).setOnInsert("createdAt", Instant.now()).setOnInsert("startSeq", start).setOnInsert("endSeq", start + BlockIndexUtil.BLOCK_SIZE - 1).set("updatedAt", Instant.now());
            entry.getValue().forEach(message -> update.set("messages." + BlockIndexUtil.index(message.getSeq()), slot(message)));
            blocks.upsert(Query.query(Criteria.where("_id").is(event.getConversationId()+":"+blockNo)), update);
        }
        blocks.execute();
    }
    @Override public List<MessageBlockDoc> findRecentBlocks(String conversationId, int limit, int maxWindows) {
        MessageBlockDoc latest = mongoTemplate.findOne(Query.query(Criteria.where("conversationId").is(conversationId)).with(Sort.by(Sort.Direction.DESC, "blockNo")).limit(1), MessageBlockDoc.class); if (latest == null || latest.getBlockNo() == null) return List.of();
        List<MessageBlockDoc> result = new ArrayList<>(); long cursor = latest.getBlockNo(); int windows = 0; int size = Math.max(1, (int) Math.ceil((double) limit / BlockIndexUtil.BLOCK_SIZE));
        while (cursor >= 0 && result.stream().mapToInt(block -> block.getMessages().size()).sum() < limit && windows++ < maxWindows) { long begin = Math.max(0, cursor-size+1); result.addAll(mongoTemplate.find(Query.query(Criteria.where("conversationId").is(conversationId).and("blockNo").gte(begin).lte(cursor)).with(Sort.by(Sort.Direction.DESC, "blockNo")), MessageBlockDoc.class)); cursor = begin-1; }
        return result;
    }
    @Override public List<MessageBlockDoc> findBlocksBySeqRange(String conversationId, long beginSeq, long endSeq) { return mongoTemplate.find(Query.query(Criteria.where("conversationId").is(conversationId).and("blockNo").gte(BlockIndexUtil.blockNo(beginSeq)).lte(BlockIndexUtil.blockNo(endSeq))).with(Sort.by(Sort.Direction.ASC, "blockNo")), MessageBlockDoc.class); }
    @Override public List<MessageIdMappingDoc> findRecentMappings(int limit) { return mongoTemplate.find(new Query().with(Sort.by(Sort.Direction.DESC, "sendTime")).limit(Math.max(1, limit)), MessageIdMappingDoc.class); }
    @Override public MessageSlot findSlot(String conversationId, long seq) { MessageBlockDoc block = mongoTemplate.findById(BlockIndexUtil.docId(conversationId, seq), MessageBlockDoc.class); if (block == null) return null; return block.getMessages().stream().filter(slot -> slot != null && Long.valueOf(seq).equals(slot.getSeq())).findFirst().orElse(null); }
    @Override public AttachmentMetadataDoc findAttachmentMetadata(String attachmentId) { return mongoTemplate.findById(attachmentId, AttachmentMetadataDoc.class); }
    @Override public List<MessageMutationDoc> findRevokedMutations(List<String> serverMsgIds) { Set<String> ids = new HashSet<>(); for (String id : serverMsgIds) if (id != null && !id.isBlank()) ids.add(id + ":REVOKED"); return ids.isEmpty() ? List.of() : mongoTemplate.find(Query.query(Criteria.where("_id").in(ids)), MessageMutationDoc.class); }
    @Override public MessageIdMappingDoc findMappingByServerMessageId(String serverMsgId) { return mongoTemplate.findOne(Query.query(Criteria.where("serverMsgId").is(serverMsgId)), MessageIdMappingDoc.class); }
    @Override public MessageMutationDoc findMutationById(String mutationId) { return mongoTemplate.findById(mutationId, MessageMutationDoc.class); }
    @Override public MessageMutationDoc upsertMutation(MessageMutationDoc mutation) {
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(mutation.getId())),
                new Update()
                        .setOnInsert("serverMsgId", mutation.getServerMsgId())
                        .setOnInsert("conversationId", mutation.getConversationId())
                        .setOnInsert("mutationType", mutation.getMutationType())
                        .setOnInsert("operatorUserId", mutation.getOperatorUserId())
                        .setOnInsert("operatorName", mutation.getOperatorName())
                        .setOnInsert("targetSenderId", mutation.getTargetSenderId())
                        .setOnInsert("targetSenderName", mutation.getTargetSenderName())
                        .setOnInsert("reason", mutation.getReason())
                        .setOnInsert("mutationVersion", mutation.getMutationVersion())
                        .setOnInsert("createdAt", mutation.getCreatedAt()),
                FindAndModifyOptions.options().upsert(true).returnNew(true), MessageMutationDoc.class);
    }
    @Override public List<MessageMutationDoc> findMutationsAfter(String conversationId, Instant afterCreatedAt,
                                                                   String afterMutationId, int limit) {
        Criteria afterCursor = new Criteria().orOperator(
                Criteria.where("createdAt").gt(afterCreatedAt),
                new Criteria().andOperator(Criteria.where("createdAt").is(afterCreatedAt),
                        Criteria.where("_id").gt(afterMutationId == null ? "" : afterMutationId)));
        return mongoTemplate.find(Query.query(Criteria.where("conversationId").is(conversationId)
                        .andOperator(afterCursor))
                .with(Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "_id")))
                .limit(limit), MessageMutationDoc.class);
    }
    private String attachmentId(byte[] content) { try { String id = content == null ? null : objectMapper.readTree(content).path("attachmentId").asText(null); return id == null || id.isBlank() ? null : id; } catch (Exception ignored) { return null; } }
    private MessageSlot slot(Message message) { MessageSlot slot = new MessageSlot(); slot.setSeq(message.getSeq()); slot.setClientMsgId(message.getClientMsgId()); slot.setServerMsgId(message.getServerMsgId()); slot.setSenderId(message.getSenderId()); slot.setReceiverId(message.getReceiverId()); slot.setGroupId(message.getGroupId()); slot.setSessionType(message.getChatType() == null ? null : message.getChatType().getCode()); slot.setContentType(message.getContentType() == null ? null : message.getContentType().getCode()); slot.setContent(message.getContent()); slot.setSendTime(message.getSendTime()); slot.setCreateTime(message.getCreateTime()); slot.setStatus(message.getStatus() == null ? null : message.getStatus().getCode()); slot.setPlatformType(message.getPlatformType() == null ? null : message.getPlatformType().getCode()); slot.setUniqueId(message.getUniqueId()); slot.setSource(message.getSource() == null ? null : message.getSource().getCode()); slot.setOptions(message.getOptions()); slot.setAttributes(message.getAttributes()); return slot; }
}
