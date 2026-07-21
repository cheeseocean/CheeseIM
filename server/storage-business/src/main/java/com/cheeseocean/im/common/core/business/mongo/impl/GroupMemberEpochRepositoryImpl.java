package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.GroupMemberEpoch;
import com.cheeseocean.im.common.core.business.mongo.document.group.GroupMemberEpochDoc;
import com.cheeseocean.im.common.core.business.repository.GroupMemberEpochRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

/**
 * {@link GroupMemberEpochRepository} 的 MongoDB 实现。
 */
public class GroupMemberEpochRepositoryImpl implements GroupMemberEpochRepository {

    private final MongoTemplate mongoTemplate;

    public GroupMemberEpochRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void saveBaseline(List<GroupMemberEpoch> epochs) {
        upsertEpochs(epochs);
    }

    @Override
    public void openAll(List<GroupMemberEpoch> epochs) {
        upsertEpochs(epochs);
    }

    private void upsertEpochs(List<GroupMemberEpoch> epochs) {
        if (epochs == null || epochs.isEmpty()) {
            return;
        }
        BulkOperations bulk = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, GroupMemberEpochDoc.class);
        int operationCount = 0;
        for (GroupMemberEpoch epoch : epochs) {
            if (epoch == null || epoch.getEpochId() == null) {
                continue;
            }
            bulk.upsert(
                    Query.query(Criteria.where("_id").is(epoch.getEpochId())
                            .and("groupId").is(epoch.getGroupId())),
                    new Update()
                            .setOnInsert("epochId", epoch.getEpochId())
                            .setOnInsert("groupId", epoch.getGroupId())
                            .setOnInsert("userId", epoch.getUserId())
                            .setOnInsert("joinedVersion", epoch.getJoinedVersion())
                            .setOnInsert("leftVersionExclusive", epoch.getLeftVersionExclusive()));
            operationCount++;
        }
        if (operationCount > 0) {
            bulk.execute();
        }
    }

    @Override
    public void closeAll(String groupId, List<String> userIds, long leftVersionExclusive) {
        if (groupId == null || groupId.isBlank() || userIds == null || userIds.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("groupId").is(groupId)
                .and("userId").in(userIds)
                .and("leftVersionExclusive").is(Long.MAX_VALUE));
        mongoTemplate.updateMulti(
                query,
                new Update().set("leftVersionExclusive", leftVersionExclusive),
                GroupMemberEpochDoc.class);
    }

    @Override
    public List<GroupMemberEpoch> findPage(String groupId,
                                           long snapshotVersion,
                                           long afterJoinedVersion,
                                           String afterUserId,
                                           String afterEpochId,
                                           int limit) {
        int pageLimit = Math.min(2_000, Math.max(1, limit));
        String userCursor = afterUserId == null ? "" : afterUserId;
        String epochCursor = afterEpochId == null ? "" : afterEpochId;
        Criteria visibleAtSnapshot = Criteria.where("groupId").is(groupId)
                .and("joinedVersion").lte(snapshotVersion)
                .and("leftVersionExclusive").gt(snapshotVersion);
        Criteria cursor = new Criteria().orOperator(
                Criteria.where("joinedVersion").gt(afterJoinedVersion),
                new Criteria().andOperator(
                        Criteria.where("joinedVersion").is(afterJoinedVersion),
                        Criteria.where("userId").gt(userCursor)),
                new Criteria().andOperator(
                        Criteria.where("joinedVersion").is(afterJoinedVersion),
                        Criteria.where("userId").is(userCursor),
                        Criteria.where("epochId").gt(epochCursor))
        );
        Query query = Query.query(new Criteria().andOperator(visibleAtSnapshot, cursor))
                .with(Sort.by(
                        Sort.Order.asc("joinedVersion"),
                        Sort.Order.asc("userId"),
                        Sort.Order.asc("epochId")))
                .limit(pageLimit + 1);
        query.fields()
                .include("epochId")
                .include("groupId")
                .include("userId")
                .include("joinedVersion")
                .include("leftVersionExclusive");
        return mongoTemplate.find(query, GroupMemberEpochDoc.class)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private GroupMemberEpoch toDomain(GroupMemberEpochDoc doc) {
        GroupMemberEpoch epoch = new GroupMemberEpoch();
        epoch.setEpochId(doc.getEpochId());
        epoch.setGroupId(doc.getGroupId());
        epoch.setUserId(doc.getUserId());
        epoch.setJoinedVersion(doc.getJoinedVersion());
        epoch.setLeftVersionExclusive(doc.getLeftVersionExclusive());
        return epoch;
    }
}
