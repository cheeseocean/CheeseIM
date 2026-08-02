# CheeseIM Server — Session Memory

## Project Structure
- Root: `/Users/xxxcrel/Develop/backend/java/CheeseIM/server`
- Modules (16): `common-api`, `common-core`, `infra-queue`, `infra-state`, `storage-history`, `storage-business`, `postoffice`, `postmaster`, `postbox`, `postman`, `authcenter`, `business`, `api-server`, `config`, `bootstrap-all`, `ops-cli`
- Main branch: `1.0.0`
- Build: Gradle multi-module (Java 17, Spring Boot 3.2.4, Dubbo 3.2.8)

## Architecture
Postal-system metaphor for the message pipeline:

```
Client ──TCP/WS──> postoffice ──> postbox ──ingress event──> postmaster ──delivery event──> postman ──> postoffice ──> Client
                                          │                       │                          │
                                          └──> Mongo history      └──> seq allocate         └──> APNs/FCM/Huawei/Xiaomi/JPush
```

Key pipeline (postmaster IngressEventListener):
1. Claim ingress inbox on stable `serverMsgId`（重放安全）
2. Group messages: defensive permission query, get groupType
3. `DefaultMessagePolicyEngine.decide` → `MessageRouteDecision`（persistHistory/notification/sendDelivery/needOfflinePush/senderSync）
4. Batch seq allocation via `ConversationSeqAllocator`（Redis Lua 状态机 + Mongo `$inc` 段预分配）
5. Publish history event（unordered bulk upsert: id mapping + message_block）
6. Delivery: NORMAL_GROUP → GROUP_FANOUT job（写扩散）; SUPER_GROUP → persist only（读扩散）
7. Broker ACK → inbox COMPLETED

## Key Infrastructure (post-2026-07 refactor)
- `common-core`: ports/models only（`CacheStore`, `QueueAdapter`, `ConversationSeqAllocator`, Repository ports）
- `infra-queue`: Kafka/Chronicle adapter runtime
- `infra-state`: Redis/RocksDB state/cache/seq adapters（package still `com.cheeseocean.im.common.core.*`, migration debt）
- `storage-history`: Message history Mongo adapter（`MessageHistoryRepository` impl）
- `storage-business`: Business Mongo adapter（user/friend/group/conversation Documents + impls）

## Key Files
- `common-core/.../logging/CommonLoggers.java` — centralized named Loggers per module
- `config/src/main/resources/logback-spring.xml` — per-module file appenders + console root
- `config/src/main/resources/common.yml` — shared config
- `postmaster/.../listener/IngressEventListener.java` — ingress pipeline（batch=500, per-conversation ordering）
- `postmaster/.../history/BlockHistoryPersistenceService.java` — MongoDB block storage（unordered bulkOps）
- `postmaster/.../service/GroupFanoutPlanner.java` — group fanout（NORMAL_GROUP write, SUPER_GROUP read）
- `business/.../conversation/ReadSeqPersistenceWriter.java` — async MongoDB write-behind for readSeq
- `business/.../conversation/DeliverySeqPersistenceWriter.java` — delivery seq write-behind
- `common-core/.../store/sequence/conversation/ConversationSeqAllocator.java` — seq allocation port

## Logging Convention
- Each module uses `CommonLoggers.<MODULE>` (e.g. `CommonLoggers.POSTMASTER`)
- Named loggers route to per-module files (postmaster.log, postoffice.log, etc.)
- `additivity=true` propagates all module logs to root → CONSOLE
- `com.cheeseocean.im.common` package → common.log (via package rule, not named logger)

## Conventions
- `@author xxxcrel` on all source files
- Chinese comments in core pipeline logic
- Constructor injection（禁止 `@Autowired` 字段注入）
- Domain objects must not import `org.springframework.data.*`
- seq allocation ONLY via `ConversationSeqAllocator`（禁止 INCRBY）
- Enum with stable `code`/`desc`/`fromCode` for persistence/wire/cross-process

## User Preferences
- Communication: concise, Chinese ok for inline comments
- Author tag: `@author xxxcrel`
- No emojis
- Commit message: English; Response language: Chinese
