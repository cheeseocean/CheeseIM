package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.enums.DltRedriveStatus;
import com.cheeseocean.im.common.core.business.mongo.document.queue.DltRedriveAuditDoc;
import com.cheeseocean.im.common.core.queue.dlt.DltRedriveAuditStore;
import com.cheeseocean.im.common.core.queue.dlt.DltRedriveCommand;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Mongo 实现的 DLT 重放租约与审计。
 */
public class MongoDltRedriveAuditStore implements DltRedriveAuditStore {

    private final MongoTemplate mongoTemplate;

    public MongoDltRedriveAuditStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Claim claim(DltRedriveCommand command,
                       String checksum,
                       String ownerToken,
                       long nowMillis,
                       long leaseMillis) {
        validateExistingIdentity(command, checksum);
        Criteria available = new Criteria().orOperator(
                Criteria.where("leaseUntil").lte(nowMillis),
                Criteria.where("leaseUntil").exists(false));
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(command.operationId()),
                Criteria.where("status").ne(DltRedriveStatus.COMPLETED.getCode()),
                available));
        Update update = new Update()
                .setOnInsert("operationId", command.operationId())
                .setOnInsert("sourceTopic", command.sourceTopic())
                .setOnInsert("dltTopic", command.sourceTopic() + ".DLT")
                .setOnInsert("partition", command.partition())
                .setOnInsert("dltOffset", command.offset())
                .setOnInsert("checksum", checksum)
                .setOnInsert("operatorId", command.operatorId())
                .setOnInsert("reason", command.reason())
                .setOnInsert("createdAt", nowMillis)
                .set("status", DltRedriveStatus.PROCESSING.getCode())
                .set("ownerToken", ownerToken)
                .set("leaseUntil", nowMillis + leaseMillis)
                .set("error", "")
                .inc("generation", 1L);
        try {
            DltRedriveAuditDoc claimed = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true).upsert(true),
                    DltRedriveAuditDoc.class);
            if (claimed == null) {
                throw new IllegalStateException(
                        "DLT redrive claim returned no state: " + command.operationId());
            }
            return new Claim(
                    ClaimStatus.ACQUIRED,
                    claimed.getGeneration(),
                    claimed.getLeaseUntil());
        } catch (DuplicateKeyException busyOrCompleted) {
            validateExistingIdentity(command, checksum);
            DltRedriveAuditDoc current =
                    mongoTemplate.findById(command.operationId(), DltRedriveAuditDoc.class);
            if (current != null
                    && current.getStatus() == DltRedriveStatus.COMPLETED.getCode()) {
                return new Claim(ClaimStatus.COMPLETED, current.getGeneration(), 0L);
            }
            return new Claim(
                    ClaimStatus.BUSY,
                    current == null ? 0L : current.getGeneration(),
                    current == null ? 0L : current.getLeaseUntil());
        }
    }

    @Override
    public boolean complete(String operationId,
                            String ownerToken,
                            long generation,
                            long completedAt) {
        return mongoTemplate.updateFirst(
                owned(operationId, ownerToken, generation),
                new Update()
                        .set("status", DltRedriveStatus.COMPLETED.getCode())
                        .set("ownerToken", "")
                        .set("leaseUntil", 0L)
                        .set("completedAt", completedAt),
                DltRedriveAuditDoc.class).getMatchedCount() == 1L;
    }

    @Override
    public void fail(String operationId,
                     String ownerToken,
                     long generation,
                     long failedAt,
                     String error) {
        mongoTemplate.updateFirst(
                owned(operationId, ownerToken, generation),
                new Update()
                        .set("status", DltRedriveStatus.FAILED.getCode())
                        .set("ownerToken", "")
                        .set("leaseUntil", 0L)
                        .set("failedAt", failedAt)
                        .set("error", truncate(error, 512)),
                DltRedriveAuditDoc.class);
    }

    private Query owned(String operationId, String ownerToken, long generation) {
        return Query.query(Criteria.where("_id").is(operationId)
                .and("status").is(DltRedriveStatus.PROCESSING.getCode())
                .and("ownerToken").is(ownerToken)
                .and("generation").is(generation));
    }

    private void validateExistingIdentity(DltRedriveCommand command, String checksum) {
        DltRedriveAuditDoc existing =
                mongoTemplate.findById(command.operationId(), DltRedriveAuditDoc.class);
        if (existing == null) {
            return;
        }
        boolean same = java.util.Objects.equals(existing.getSourceTopic(), command.sourceTopic())
                && existing.getPartition() == command.partition()
                && existing.getDltOffset() == command.offset()
                && java.util.Objects.equals(existing.getChecksum(), checksum)
                && java.util.Objects.equals(existing.getOperatorId(), command.operatorId())
                && java.util.Objects.equals(existing.getReason(), command.reason());
        if (!same) {
            throw new IllegalArgumentException(
                    "operationId is already bound to another DLT record");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
