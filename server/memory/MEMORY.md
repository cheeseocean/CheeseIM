# CheeseIM Server — Session Memory

## Project Structure
- Root: `/Users/xxxcrel/Develop/backend/java/CheeseIM/server`
- Modules: `common-api`, `common-core`, `postmaster`, `postoffice`, `postman`, `postbox`, `postmaster`, `authcenter`, `social`, `bootstrap-all`, `config`
- Main branch: `1.0.0`
- Build: Maven multi-module

## Architecture
Based on OpenIM Go reference (`/Users/xxxcrel/Develop/backend/go/Open-IM-Server`).

Key pipeline (postmaster IngressEventListener):
1. Pre-process READ_RECEIPT → `MessageStateService.processReadReceipts()`
2. Categorize by `persistHistory()` → storage vs notStorage
3. Fast-push notStorage before seq allocation
4. `ConversationSeqService.allocateBatch()` — single INCRBY
5. Bind seq + split by `notification()` → regularProcessed / notificationProcessed
6. `MessageStateService.applyBatch()` — batched Redis writes
7. Publish single `HistoryEvent` for the whole batch
8. If regularProcessed non-empty: `createIfNew` → delivery (allProcessed) → `sync`; else notification-only delivery

## Key Files
- `common-core/.../logging/CommonLoggers.java` — centralized named Loggers per module
- `config/src/main/resources/logback-spring.xml` — per-module file appenders + console root
- `config/src/main/resources/common.yml` — shared config (logging now deferred to logback-spring.xml)
- `postmaster/.../listener/IngressEventListener.java` — ingress pipeline (Chinese comments)
- `postmaster/.../service/MessageStateService.java` — Redis state, processReadReceipts, applyBatch
- `postmaster/.../service/ConversationService.java` — createIfNew, sync
- `postmaster/.../conversation/ReadSeqPersistenceWriter.java` — async MongoDB write-behind for readSeq
- `postmaster/.../history/BlockHistoryPersistenceService.java` — MongoDB block storage

## Logging Convention
- Each module uses `CommonLoggers.<MODULE>` (e.g. `CommonLoggers.POSTMASTER`)
- Named loggers route to per-module files (postmaster.log, postoffice.log, etc.)
- `additivity=true` propagates all module logs to root → CONSOLE
- `com.cheeseocean.im.common` package → common.log (via package rule, not named logger)
- common-core infra classes (serializers, queue adapters) keep `LoggerFactory.getLogger(Class)` — covered by package rule

## Conventions
- `@author xxxcrel` on all source files (no `@author CheeseIM` remaining)
- Chinese comments in core pipeline logic (IngressEventListener, etc.)
- No `LoggerFactory.getLogger(Class.class)` in module sources — only in CommonLoggers itself

## Completed Cleanup (session 1.0.0)
- Deleted 10 dead-code files: ReceiptAckRpc, ReceiptAckReq, ReceiptAckRpcImpl,
  ConversationReceiptService, DeliveryTask, DeliveryResult, MessageIdempotencyService,
  SendMessageCommand, MessageSendPermissionChecker, MessageFlowMetrics
- Created CommonLoggers.java + logback-spring.xml
- Updated all module loggers to CommonLoggers (postmaster/postoffice/postman/postbox)
- Replaced all `@author CheeseIM` → `@author xxxcrel` across 32 files
- Cleaned redundant `logging.pattern.*` from common.yml
- Implemented: processReadReceipts, ReadSeqPersistenceWriter, applyBatch,
  incrementUnreadBy (Redis INCRBY), createIfNew (P0 fix before delivery),
  notification/regular message split in pipeline

## User Preferences
- Communication: concise, Chinese ok for inline comments, English for Javadoc
- Author tag: `@author xxxcrel`
- No emojis
