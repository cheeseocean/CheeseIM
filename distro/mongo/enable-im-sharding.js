/*
 * CheeseIM Mongo 分片与索引 migration。
 *
 * 运行方式：
 *   mongosh "mongodb://<mongos>/admin" \
 *     --eval 'var CHEESEIM_DB="cheese_im"' \
 *     distro/mongo/enable-im-sharding.js
 *
 * 约束：
 * - 只能连接 mongos；脚本拒绝在 standalone / replica-set member 上伪执行。
 * - 可重复执行；已有不兼容 shard key 或同名异构索引时立即失败。
 * - conversation 只在 conversation_delivery_preference 回填完成后分片。
 */

(function () {
    "use strict";

    var databaseName = typeof CHEESEIM_DB === "string" && CHEESEIM_DB.length > 0
        ? CHEESEIM_DB
        : "cheese_im";
    var admin = db.getSiblingDB("admin");
    var hello = admin.runCommand({hello: 1});
    if (!hello.ok || hello.msg !== "isdbgrid") {
        throw new Error("B05_NOT_MONGOS: enable-im-sharding.js must run through mongos");
    }

    var appDb = db.getSiblingDB(databaseName);
    var configDb = db.getSiblingDB("config");

    function namespace(collectionName) {
        return databaseName + "." + collectionName;
    }

    function sameKey(left, right) {
        return EJSON.stringify(left) === EJSON.stringify(right);
    }

    function currentSharding(collectionName) {
        return configDb.collections.findOne({_id: namespace(collectionName), dropped: {$ne: true}});
    }

    function preflightShardKey(collectionName, expectedKey) {
        var current = currentSharding(collectionName);
        if (current && !sameKey(current.key, expectedKey)) {
            throw new Error(
                "B05_INCOMPATIBLE_SHARD_KEY: " + namespace(collectionName)
                + " current=" + EJSON.stringify(current.key)
                + " expected=" + EJSON.stringify(expectedKey)
            );
        }
    }

    function ensureCollection(collectionName) {
        if (!appDb.getCollectionNames().includes(collectionName)) {
            assert.commandWorked(appDb.createCollection(collectionName));
        }
    }

    function ensureIndex(collectionName, keys, options) {
        ensureCollection(collectionName);
        appDb.getCollection(collectionName).createIndex(keys, options);
    }

    function ensureSharded(collectionName, shardKey) {
        if (currentSharding(collectionName)) {
            print("B05_ALREADY_SHARDED " + namespace(collectionName));
            return;
        }
        assert.commandWorked(sh.shardCollection(namespace(collectionName), shardKey));
        print("B05_SHARDED " + namespace(collectionName) + " " + EJSON.stringify(shardKey));
    }

    // 先完成全量 preflight，避免执行一半才发现已有环境与计划不兼容。
    preflightShardKey("message_block", {conversationId: "hashed"});
    preflightShardKey("message_id_mapping", {serverMsgId: "hashed"});
    preflightShardKey("group_member_epoch", {groupId: 1});
    preflightShardKey("group_fanout_job", {_id: "hashed"});
    preflightShardKey("dlt_redrive_audit", {_id: "hashed"});
    preflightShardKey("conversation", {ownerUserId: 1, conversationId: 1});
    preflightShardKey(
        "conversation_delivery_preference",
        {conversationId: 1, ownerUserId: 1}
    );
    var invalidBlockedPreferences = appDb.getCollection("conversation").countDocuments({
        receiveOpt: 1,
        $or: [
            {conversationId: {$exists: false}},
            {conversationId: null},
            {ownerUserId: {$exists: false}},
            {ownerUserId: null}
        ]
    });
    if (invalidBlockedPreferences > 0) {
        throw new Error(
            "B05_INVALID_CONVERSATION_PREFERENCE_ROWS count=" + invalidBlockedPreferences
        );
    }

    assert.commandWorked(admin.runCommand({enableSharding: databaseName}));

    ensureIndex(
        "message_block",
        {conversationId: "hashed"},
        {name: "shard_message_block_conversation"}
    );
    ensureIndex(
        "message_block",
        {conversationId: 1, blockNo: -1},
        {name: "idx_message_block_conversation_block"}
    );

    ensureIndex(
        "message_id_mapping",
        {serverMsgId: "hashed"},
        {name: "shard_message_mapping_server"}
    );
    ensureIndex(
        "message_id_mapping",
        {conversationId: 1, seq: 1},
        {name: "idx_message_mapping_conversation_seq"}
    );

    ensureIndex(
        "group_member_epoch",
        {groupId: 1},
        {name: "shard_group_member_epoch_group"}
    );
    ensureIndex(
        "group_member_epoch",
        {groupId: 1, userId: 1, joinedVersion: 1},
        {name: "uk_group_user_joined", unique: true}
    );
    ensureIndex(
        "group_member_epoch",
        {groupId: 1, userId: 1, leftVersionExclusive: 1},
        {name: "uk_group_user_epoch_end", unique: true}
    );
    ensureIndex(
        "group_member_epoch",
        {groupId: 1, joinedVersion: 1, userId: 1, epochId: 1, leftVersionExclusive: 1},
        {name: "idx_group_snapshot_page"}
    );

    ensureIndex(
        "group_fanout_job",
        {_id: "hashed"},
        {name: "shard_group_fanout_job_id"}
    );
    ensureIndex(
        "group_fanout_job",
        {status: 1, leaseUntil: 1},
        {name: "idx_fanout_status_lease"}
    );
    ensureIndex(
        "group_fanout_job",
        {expireAt: 1},
        {name: "expireAt", expireAfterSeconds: 0}
    );

    ensureIndex(
        "dlt_redrive_audit",
        {_id: "hashed"},
        {name: "shard_dlt_redrive_audit_id"}
    );
    ensureIndex(
        "dlt_redrive_audit",
        {dltTopic: 1, partition: 1, dltOffset: 1, createdAt: -1},
        {name: "idx_dlt_record_created"}
    );
    ensureIndex(
        "dlt_redrive_audit",
        {status: 1, leaseUntil: 1},
        {name: "idx_dlt_status_lease"}
    );

    ensureIndex(
        "conversation_delivery_preference",
        {conversationId: 1, ownerUserId: 1},
        {name: "uk_conversation_owner_preference", unique: true}
    );
    ensureIndex(
        "conversation_delivery_preference",
        {conversationId: 1, receiveOption: 1, ownerUserId: 1},
        {name: "idx_conversation_option_owner"}
    );
    ensureIndex(
        "conversation",
        {ownerUserId: 1, conversationId: 1},
        {name: "uniq_owner_conversation", unique: true}
    );
    ensureIndex(
        "conversation",
        {ownerUserId: 1, updatedAt: -1},
        {name: "owner_updated"}
    );
    ensureIndex(
        "conversation",
        {ownerUserId: 1, pinned: -1, updatedAt: -1},
        {name: "owner_pinned_updated"}
    );

    // 应用版本必须先双写该读模型；此处为存量 BLOCK(1) 偏好做幂等基线回填。
    appDb.getCollection("conversation").aggregate([
        {$match: {receiveOpt: 1}},
        {$project: {
            _id: 1,
            conversationId: 1,
            ownerUserId: 1,
            receiveOption: {$literal: 1},
            updatedAt: {$ifNull: ["$updatedAt", "$createdAt"]}
        }},
        {$merge: {
            into: "conversation_delivery_preference",
            on: ["conversationId", "ownerUserId"],
            whenMatched: "replace",
            whenNotMatched: "insert"
        }}
    ]).toArray();

    ensureSharded("message_block", {conversationId: "hashed"});
    ensureSharded("message_id_mapping", {serverMsgId: "hashed"});
    ensureSharded("group_member_epoch", {groupId: 1});
    ensureSharded("group_fanout_job", {_id: "hashed"});
    ensureSharded("dlt_redrive_audit", {_id: "hashed"});
    ensureSharded(
        "conversation_delivery_preference",
        {conversationId: 1, ownerUserId: 1}
    );
    ensureSharded("conversation", {ownerUserId: 1, conversationId: 1});

    print("B05_COMPLETE database=" + databaseName);
}());
