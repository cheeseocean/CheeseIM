# storage-business/ARCH.md — 业务 Mongo adapter 事实快照

> 用户、好友、群、会话、控制事件、fanout job 与 DLT 审计的 Mongo 持久化实现。

## 边界

- Repository/store port 与事务抽象保留在 common-core，领域对象保留在 common-api/common-core；
- 本模块拥有 Mongo Document、MongoTemplate 实现、索引注解和事务管理器装配；
- authcenter/business/postmaster/postman 只依赖 port，不得 import `mongo.document` / `mongo.impl`；
- 模块是共享 library，不是独立服务，不增加 RPC；集合名、`_id`、shard key 和事务语义不变。

## 自动装配

- `CommonMongoPersistenceConfiguration` 通过 `AutoConfiguration.imports` 加载；
- 只有存在 MongoTemplate 时才注册 adapter 和事务执行器；调用方不再使用自定义 enable 注解；
- cluster 默认启用 Mongo transaction，all-in-one 默认关闭，可由 `cheeseim.mongo.transactions-enabled` 覆盖；
- DLT Kafka 运维端口额外要求 `cheeseim.queue.dlt.operations.enabled=true`，只有 ops-cli 默认开启。

## 不变量

- upsert/query 必须携带已声明 shard key；确定性 `_id` 不等于 mongos 可推导路由；
- max/read/delivery 水位只能单调更新，禁止恢复 read-modify-write；
- 群当前成员与 member epoch 在同一事务边界维护；
- Document 不得返回到 feature，新增查询先扩 port/domain model；
- cluster migration 与索引以 `distro/mongo` 为权威，注解只服务本地发现。

## 当前迁移债务

Java package 暂保留历史前缀 `com.cheeseocean.im.common.core.business.mongo`，避免本次物理迁移同时产生
大规模无语义 import diff；源码所有权已经由 Gradle module 和构建门禁强制。后续 package 改名应作为纯机械任务，
不得与集合或查询语义修改混在一次变更中。
