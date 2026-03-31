package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.enums.HandleResultEnum;
import com.cheeseocean.im.common.core.business.domain.GroupApplication;
import com.cheeseocean.im.common.core.business.mongo.document.group.GroupRequestDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.GroupApplicationMongoRepository;
import com.cheeseocean.im.common.core.business.repository.GroupApplicationRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link GroupApplicationRepository} 的 MongoDB 实现。
 *
 * <p>以 "{userId}:{groupId}" 作为文档 _id（唯一索引约束），
 * handleResult 在存储时保存 int code，读取时通过 {@link HandleResultEnum#fromCode} 还原。
 */
public class GroupApplicationRepositoryImpl implements GroupApplicationRepository {

    private static final int PENDING = HandleResultEnum.PENDING.getCode();

    private final GroupApplicationMongoRepository groupApplicationMongoRepository;
    private final MongoTemplate mongoTemplate;

    public GroupApplicationRepositoryImpl(GroupApplicationMongoRepository groupApplicationMongoRepository,
                                          MongoTemplate mongoTemplate) {
        this.groupApplicationMongoRepository = groupApplicationMongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<GroupApplication> findByUserAndGroup(String userId, String groupId) {
        return groupApplicationMongoRepository.findByUserIdAndGroupId(userId, groupId)
                .map(this::toDomain);
    }

    @Override
    public List<GroupApplication> findPendingByGroupId(String groupId) {
        return groupApplicationMongoRepository
                .findByGroupIdAndHandleResultOrderByReqTimeDesc(groupId, PENDING)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<GroupApplication> findPendingByUserId(String userId) {
        return groupApplicationMongoRepository
                .findByUserIdAndHandleResultOrderByReqTimeDesc(userId, PENDING)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void save(GroupApplication application) {
        groupApplicationMongoRepository.save(toDoc(application));
    }

    @Override
    public void updateHandleResult(String userId, String groupId, int handleResult,
                                   String handleUserId, String handledMsg, long handledTime) {
        Query query = Query.query(
                Criteria.where("userId").is(userId).and("groupId").is(groupId));
        Update update = new Update()
                .set("handleResult", handleResult)
                .set("handleUserId", handleUserId)
                .set("handledMsg",   handledMsg)
                .set("handledTime",  handledTime);
        mongoTemplate.updateFirst(query, update, GroupRequestDoc.class);
    }

    // ── 转换方法 ─────────────────────────────────────────────────────────────

    private GroupApplication toDomain(GroupRequestDoc doc) {
        GroupApplication app = new GroupApplication();
        app.setId(doc.getId());
        app.setUserId(doc.getUserId());
        app.setGroupId(doc.getGroupId());
        app.setHandleResult(HandleResultEnum.fromCode(doc.getHandleResult()));
        app.setReqMsg(doc.getReqMsg());
        app.setHandledMsg(doc.getHandledMsg());
        app.setHandleUserId(doc.getHandleUserId());
        app.setHandledTime(doc.getHandledTime());
        app.setJoinSource(doc.getJoinSource());
        app.setInviterUserId(doc.getInviterUserId());
        app.setEx(doc.getEx());
        app.setReqTime(doc.getReqTime());
        return app;
    }

    private GroupRequestDoc toDoc(GroupApplication app) {
        GroupRequestDoc doc = new GroupRequestDoc();
        doc.setId(app.getId() != null
                ? app.getId()
                : app.getUserId() + ":" + app.getGroupId());
        doc.setUserId(app.getUserId());
        doc.setGroupId(app.getGroupId());
        doc.setHandleResult(app.getHandleResult() != null
                ? app.getHandleResult().getCode()
                : PENDING);
        doc.setReqMsg(app.getReqMsg());
        doc.setHandledMsg(app.getHandledMsg());
        doc.setHandleUserId(app.getHandleUserId());
        doc.setHandledTime(app.getHandledTime());
        doc.setJoinSource(app.getJoinSource());
        doc.setInviterUserId(app.getInviterUserId());
        doc.setEx(app.getEx());
        doc.setReqTime(app.getReqTime());
        return doc;
    }
}
