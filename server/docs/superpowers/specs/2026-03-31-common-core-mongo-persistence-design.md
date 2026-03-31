# Common-Core Mongo Persistence Design

## Context

Current Mongo domain models, Spring Data repositories, and repository implementations live under the `business` module. This creates an unnecessary module boundary for data that is not inherently owned by `business`.

Examples:

- `UserConversationState`
- `ConversationOffsetRange`
- `UserSyncCheckpoint`
- user, group, group member, friend, and application persistence models

These models may need to be used from multiple modules such as `business` and `postoffice`. Today that either forces cross-module RPC or duplicate access logic.

The goal is to move the shared Mongo persistence layer into `common-core` while keeping both startup modes valid:

- single module startup
- unified multi-module startup

The user explicitly wants:

1. all Mongo-related domain objects to move
2. all related repository interfaces to move
3. all Mongo default implementations to move
4. explicit enablement, not implicit auto-scan

## Goals

1. Provide one shared Mongo persistence layer in `common-core`
2. Allow both `business` and `postoffice` to inject the same repository abstractions
3. Keep module startup explicit and predictable
4. Avoid coupling `common-core` to higher-level business services

## Non-Goals

1. Moving business service orchestration into `common-core`
2. Moving Dubbo service APIs into `common-core`
3. Changing the current Mongo schema or collection names
4. Changing module startup topology beyond persistence enablement

## Recommended Approach

Move the shared business persistence layer into `common-core` and expose it through an explicit opt-in annotation.

The shared layer includes:

- domain models
- Mongo document classes
- Spring Data Mongo repository interfaces
- repository abstractions
- default Mongo-backed repository implementations

The shared layer does not include:

- business application services
- workflow orchestration
- module-specific facades

## Package Layout

Create a shared package family under `common-core`:

- `com.cheeseocean.im.common.core.business.domain`
- `com.cheeseocean.im.common.core.business.mongo.document`
- `com.cheeseocean.im.common.core.business.mongo.repository`
- `com.cheeseocean.im.common.core.business.repository`
- `com.cheeseocean.im.common.core.business.mongo.impl`
- `com.cheeseocean.im.common.core.business.mongo.config`

This keeps persistence-oriented shared code together and avoids leaving it under a module-specific namespace like `business`.

## Enablement Model

Add an explicit annotation in `common-core`:

- `@EnableCommonMongoPersistence`

This annotation should import a dedicated configuration class that does only persistence registration:

- `@EnableMongoRepositories(...)`
- `@ComponentScan(...)` limited to shared Mongo impl packages
- optional `@Import(...)` for persistence support beans if needed later

Modules opt in explicitly:

- `business` startup class enables it
- `postoffice` startup class enables it if it needs direct persistence access

This avoids accidental bean registration through broad package scanning and keeps single-module startup deterministic.

## Ownership Boundaries

`common-core` becomes the owner of shared data access for foundational IM business data.

`business` remains the owner of:

- business workflows
- conversation orchestration
- group/friendship business rules
- Dubbo providers and application services

`postoffice` may consume shared persistence abstractions directly when it needs low-level state access, such as conversation sync points or conversation state records.

## Migration Scope

Move all existing Mongo persistence pieces from `business`:

### Domain Models

- `Blacklist`
- `ConversationOffsetRange`
- `FriendRequest`
- `Friendship`
- `Group`
- `GroupApplication`
- `GroupMember`
- `User`
- `UserConversationState`
- `UserSyncCheckpoint`

### Mongo Documents

- all classes under `business/.../infra/mongo/document`

### Spring Data Repositories

- all classes under `business/.../infra/mongo/repository`

### Repository Abstractions

- all business repository interfaces currently tied to these persistence models

### Default Implementations

- all classes under `business/.../infra/mongo/impl`

## Dependency Rules

After migration:

1. `common-core` must not depend on `business`
2. `business` depends on `common-core`
3. `postoffice` depends on `common-core`
4. shared repository interfaces must no longer import any `business` package

This is the main structural requirement. If even one shared abstraction still points back to `business`, the layering is wrong.

## Startup Compatibility

### Single Module Startup

Each module explicitly enables shared Mongo persistence only if it needs it.

That means:

- running `business` alone still works
- running `postoffice` alone still works if it enables and configures Mongo

### Unified Startup

Unified startup works because each module imports the same shared persistence layer rather than maintaining competing bean definitions.

To avoid duplicate beans:

- remove old `business` persistence beans after migration
- do not leave parallel implementations in `business`

## Risks

### 1. Duplicate Bean Registration

If `business` keeps old repository implementations while `common-core` registers new ones, startup will fail or inject ambiguous beans.

Mitigation:

- migrate, do not copy
- remove old beans in the same change set

### 2. Common-Core Becoming a Dumping Ground

If business services or orchestration also move, `common-core` will lose its role as shared foundation.

Mitigation:

- limit migration to shared persistence and data abstractions
- reject service-layer moves in this refactor

### 3. Broken Imports During Migration

This migration touches many packages and imports.

Mitigation:

- move layer by layer
- compile after each slice
- migrate repository interfaces before impls

## Testing Strategy

Verification should be done in three levels:

1. `common-core` compile verification after each slice
2. `business` compile verification after package and import migration
3. `postoffice` compile verification after consuming shared repositories

Additional checks:

- context startup test for a module enabling `@EnableCommonMongoPersistence`
- no duplicate bean registration
- repository impl wiring still resolves `MongoTemplate`, Spring Data repositories, and cache helpers

## Implementation Plan Outline

1. Introduce `common-core` shared package structure
2. Move domain models
3. Move repository interfaces
4. Move Mongo documents
5. Move Spring Data repository interfaces
6. Move Mongo-backed repository implementations
7. Add `@EnableCommonMongoPersistence`
8. Update `business` startup to opt in
9. Update `postoffice` startup to opt in if needed
10. Remove old `business` persistence packages
11. Run compile and focused startup verification

## Decision

Proceed with a full migration of the shared Mongo persistence layer into `common-core`, using explicit opt-in enablement through `@EnableCommonMongoPersistence`.
