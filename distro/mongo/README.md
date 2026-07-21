# CheeseIM Mongo migration

`enable-im-sharding.js` 只面向已搭建完成的 MongoDB sharded cluster，并且必须通过 `mongos` 执行。
脚本不会搭建 config server、shard replica set 或备份体系。

## 执行前

1. 对目标数据库完成快照/备份，并确认恢复演练可用。
2. 所有服务节点先升级到包含 B-05 shard-key 查询的版本。
3. 停止业务写入或使用经验证的在线 reshard 流程；不要把首次大集合迁移当普通启动脚本。
4. 所有服务先升级到包含 `conversation_delivery_preference` 双写/读路径的版本；
   migration 会先从 `conversation.receiveOpt=BLOCK(1)` 回填该读模型，再分片两个集合。

## 执行

```bash
mongosh "mongodb://<mongos>/admin" \
  --eval 'var CHEESEIM_DB="cheese_im"' \
  distro/mongo/enable-im-sharding.js
```

脚本当前启用：

- `message_block`：`{conversationId: "hashed"}`；
- `message_id_mapping`：`{serverMsgId: "hashed"}`；
- `group_member_epoch`：`{groupId: 1}`，保留群内分页局部性和唯一约束；
- `group_fanout_job`：`{_id: "hashed"}`。
- `dlt_redrive_audit`：`{_id: "hashed"}`，以 operationId 精确查询和抢占租约；
- `conversation_delivery_preference`：`{conversationId: 1, ownerUserId: 1}`；
- `conversation`：`{ownerUserId: 1, conversationId: 1}`。

`dlt_redrive_audit` 默认不设置 TTL。它是变更审计而非临时任务状态，生产环境应先确定合规留存与归档策略，
再通过独立 migration 增加归档或 TTL，不能沿用 Kafka DLT retention 自动删除。

脚本先检查所有已有 shard key，再创建索引和执行 `shardCollection`。已有不兼容 shard key、同名异构索引、
非 mongos 连接都会失败，不会静默跳过。

## 执行后

- 用 `sh.status()` 与 `db.<collection>.getShardDistribution()` 核对 chunk 分布；
- 对历史写入、serverMsgId 反查、epoch 分页、fanout checkpoint、用户会话列表和离线推送偏好过滤做 smoke；
- 观察 targeted/scatter-gather、chunk migration、jumbo chunk、事务 abort 和 p99；
- 在完成真实集群 smoke 前，不得把 B-05 标记为生产验收完成。
