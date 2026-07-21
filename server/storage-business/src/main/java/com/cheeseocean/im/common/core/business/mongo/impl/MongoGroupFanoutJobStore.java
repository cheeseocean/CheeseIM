package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.mongo.document.group.GroupFanoutJobDoc;
import com.cheeseocean.im.common.core.store.fanout.GroupFanoutJobStatus;
import com.cheeseocean.im.common.core.store.fanout.GroupFanoutJobStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;

/**
 * Mongo 实现的群扩散任务进度存储。
 */
public class MongoGroupFanoutJobStore implements GroupFanoutJobStore {

    private final MongoTemplate mongoTemplate;
    private final long completedRetentionMillis;

    public MongoGroupFanoutJobStore(
            MongoTemplate mongoTemplate,
            @Value("${cheeseim.delivery.group-fanout.completed-retention-seconds:691200}")
            long completedRetentionSeconds) {
        this.mongoTemplate = mongoTemplate;
        this.completedRetentionMillis =
                Math.max(604_800L, completedRetentionSeconds) * 1_000L;
    }

    @Override
    public Claim claim(String jobId,
                       long membershipVersion,
                       String ownerToken,
                       long nowMillis,
                       long leaseMillis) {
        Criteria available = new Criteria().orOperator(
                Criteria.where("ownerToken").is(ownerToken),
                Criteria.where("leaseUntil").lte(nowMillis),
                Criteria.where("leaseUntil").exists(false));
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(jobId),
                Criteria.where("status").ne(GroupFanoutJobStatus.COMPLETED.getCode()),
                available));
        Update update = new Update()
                .setOnInsert("jobId", jobId)
                .setOnInsert("membershipVersion", membershipVersion)
                .setOnInsert("joinedVersion", Long.MIN_VALUE)
                .setOnInsert("userId", "")
                .setOnInsert("epochId", "")
                .set("status", GroupFanoutJobStatus.PROCESSING.getCode())
                .set("ownerToken", ownerToken)
                .set("leaseUntil", nowMillis + leaseMillis)
                .inc("generation", 1L);
        try {
            GroupFanoutJobDoc claimed = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true).upsert(true),
                    GroupFanoutJobDoc.class);
            if (claimed == null) {
                throw new IllegalStateException("Group fanout claim returned no state: " + jobId);
            }
            return toClaim(ClaimStatus.ACQUIRED, claimed);
        } catch (DuplicateKeyException busyOrCompleted) {
            GroupFanoutJobDoc current = mongoTemplate.findById(jobId, GroupFanoutJobDoc.class);
            if (current != null
                    && current.getStatus() == GroupFanoutJobStatus.COMPLETED.getCode()) {
                return toClaim(ClaimStatus.COMPLETED, current);
            }
            return current == null
                    ? new Claim(ClaimStatus.BUSY, 0L, 0L, 0L, Long.MIN_VALUE, "", "")
                    : toClaim(ClaimStatus.BUSY, current);
        }
    }

    @Override
    public boolean checkpoint(String jobId,
                              String ownerToken,
                              long generation,
                              long joinedVersion,
                              String userId,
                              String epochId,
                              long leaseUntil) {
        Query query = ownedProcessing(jobId, ownerToken, generation);
        Update update = new Update()
                .set("joinedVersion", joinedVersion)
                .set("userId", userId == null ? "" : userId)
                .set("epochId", epochId == null ? "" : epochId)
                .set("leaseUntil", leaseUntil);
        return mongoTemplate.updateFirst(query, update, GroupFanoutJobDoc.class)
                .getMatchedCount() == 1L;
    }

    @Override
    public boolean complete(String jobId,
                            String ownerToken,
                            long generation,
                            long completedAt) {
        Update update = new Update()
                .set("status", GroupFanoutJobStatus.COMPLETED.getCode())
                .set("ownerToken", "")
                .set("leaseUntil", 0L)
                .set("completedAt", completedAt)
                .set("expireAt", new Date(completedAt + completedRetentionMillis));
        return mongoTemplate.updateFirst(
                ownedProcessing(jobId, ownerToken, generation),
                update,
                GroupFanoutJobDoc.class).getMatchedCount() == 1L;
    }

    @Override
    public void release(String jobId, String ownerToken, long generation) {
        mongoTemplate.updateFirst(
                ownedProcessing(jobId, ownerToken, generation),
                new Update().set("ownerToken", "").set("leaseUntil", 0L),
                GroupFanoutJobDoc.class);
    }

    private Query ownedProcessing(String jobId, String ownerToken, long generation) {
        return Query.query(Criteria.where("_id").is(jobId)
                .and("status").is(GroupFanoutJobStatus.PROCESSING.getCode())
                .and("ownerToken").is(ownerToken)
                .and("generation").is(generation));
    }

    private Claim toClaim(ClaimStatus status, GroupFanoutJobDoc doc) {
        return new Claim(
                status,
                doc.getGeneration(),
                doc.getLeaseUntil(),
                doc.getMembershipVersion(),
                doc.getJoinedVersion(),
                doc.getUserId(),
                doc.getEpochId());
    }
}
