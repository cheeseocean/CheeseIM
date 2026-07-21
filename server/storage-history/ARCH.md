# storage-history/ARCH.md — 消息历史 Mongo adapter

> 状态：权威。修改本模块前先读根 `AGENTS.md`、`server/AGENTS.md` 与本文件。

## 1. 职责

storage-history 是 `MessageHistoryRepository` 的 Mongo 实现模块，只拥有：

- `message_block`、`message_id_mapping`、`attachment_metadata`、`message_mutation` Document；
- 历史批量 upsert、block range、mapping、attachment、mutation cursor 查询；
- Mongo Document/BSON 到 `common-core.history.model` 的转换；
- `HistoryMongoAutoConfiguration` 条件装配。

postbox 和 postmaster 显式依赖本模块。其它服务不应仅为“可能用历史”引入它。

## 2. 依赖方向

```text
postbox/postmaster
        │
        ├──> common-core: MessageHistoryRepository + history.model
        └──> storage-history: Mongo adapter（运行时实现）
                         └──> common-core/common-api
```

- common-core 不得反向依赖 storage-history；
- feature 源码不得 import `storage.history.mongo.document` 或 `org.bson`；
- port/model 不得 import Spring Data；
- `verifyHistoryArchitectureBoundary` 是构建门禁。

## 3. 装配

模块是 library，禁用 bootJar。Spring Boot 通过
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 加载
`HistoryMongoAutoConfiguration`；只有存在 `MongoTemplate` 且调用方没有自定义
`MessageHistoryRepository` 时才创建默认 adapter。

不要让 postbox/postmaster 扫描 storage 包，也不要把 adapter 再放回 common-core 全量 component scan。

## 4. 数据不变量

- `message_block` 以 conversationId 为 shard key，upsert/点查必须显式携带 conversationId；
- `message_id_mapping` 以 serverMsgId 为 shard key，upsert 查询必须携带 serverMsgId；
- 历史写使用 unordered bulk，不能恢复循环逐条 save；
- mutation 以确定性 `{serverMsgId}:REVOKED` ID 和 `$setOnInsert` 幂等写入；
- BSON Binary 必须在 adapter 边界转为 `byte[]`，不得泄漏到 postbox；
- collection/index 改动同步 `distro/mongo/enable-im-sharding.js` 与灾备检查。

## 5. 验证

```bash
./gradlew verifyHistoryArchitectureBoundary
./gradlew :storage-history:compileJava :postbox:compileJava :postmaster:compileJava
```

当前阶段按用户约定可暂不跑测试，但正式合并前需补 adapter contract test 与真实 mongos targeted query smoke。
