# IM Code Convergence Cleanup Design

## Context

The IM rebuild has landed on branch `1.0.0` and the validated flow now centers on:

- `postoffice` for connection handling and online route publication
- `postman` for delivery orchestration, idempotency, compensation, and ack/read/recall convergence
- `postbox` for message fact storage and inbox projection
- `push` for offline push decision and execution
- `common` for shared delivery contracts

The repository still contains older message-path code that predates the current design. Some of it is unused, some is only partially wired, and some conflicts with the current delivery model by keeping older DTOs, constants, and service abstractions alive.

## Goal

Aggressively converge the codebase toward the validated IM architecture by deleting obsolete code, eliminating duplicate contracts, and rewriting misleading module documentation while preserving the currently passing test suite.

## Non-Goals

- Re-architecting the validated IM flow again
- Replacing working modules with new implementations without a demonstrated simplification benefit
- Removing compatibility code still required by Spring, Dubbo, or the passing tests unless a replacement lands in the same batch

## Cleanup Policy

### Delete

Delete code when at least one of the following is true:

- It implements an older message path that is no longer part of the current architecture
- It duplicates a contract already replaced by the new delivery DTOs or APIs
- It is a half-finished service shell with no role in the passing test suite
- It materially misleads maintainers about the current architecture

### Keep Temporarily

Keep code temporarily when:

- It is still referenced by production code that remains in scope after the cleanup batch
- It is needed to bridge from old transport payloads to the new delivery model
- It is part of the currently passing smoke tests or module tests

Temporary compatibility should be narrow, explicit, and reevaluated after each batch.

## Recommended Approach

Use architecture-boundary convergence rather than blind unused-code deletion.

This means:

- keep the validated delivery path intact
- remove legacy services module by module
- rewrite remaining adapters to point at the new contracts
- verify each batch with targeted tests before moving on

This is preferred over raw reference-based cleanup because Spring and Dubbo wiring make static reference analysis unsafe on its own.

## Cleanup Targets

### Batch 1: Shared Contract Convergence

Focus on `common`.

Targets:

- duplicate constants under `common.constant.*` versus `common.constants.*`
- the old `common.service.ConversationService`
- message-path entities that survive only to support deleted legacy flows

Expected result:

- one shared contract surface for the IM path
- no duplicate message constant namespaces
- consumers explicitly depend on either the new delivery DTOs or a deliberate legacy bridge

### Batch 2: postbox Legacy Flow Removal

Focus on removing the older transfer pipeline that no longer matches the current design:

- `MessageTransferService`
- `MessageRouterService`
- `OnlineUserService`
- `MessageStatisticsService`
- `MessageTransferListener`
- `PostmanController` endpoints that only expose legacy transfer stats

Expected result:

- `postbox` owns storage only
- no second routing/delivery pipeline remains in the module
- tests cover only storage behavior

### Batch 3: postman Legacy API Removal

Focus on removing the older history/recall service shell centered on:

- `MessageService`
- `MessageServiceImpl`
- `MessageStorageService`
- `ConversationUtils`
- `ConversationSeq`
- `MessageMongo`

Expected result:

- `postman` exposes only the delivery-core implementation and related supporting services
- no competing legacy message lifecycle remains beside `MessageDeliveryServiceImpl`

### Batch 4: push and postoffice Drift Cleanup

Focus on residual code that still references the old `Message` event path where that path no longer matches the architecture.

Targets include:

- listeners or helpers wired to the old Kafka push payload if they are no longer part of the validated flow
- stale tests and docs describing the old flow

Expected result:

- push/offline path is documented and implemented in one coherent way
- transport-layer modules do not describe obsolete routing behavior

## Risks and Mitigations

### Reflection or config-only references

Risk:
Deleting a seemingly unused class that is still loaded by Spring or Dubbo.

Mitigation:
Search for Java references, YAML references, Dubbo exposure, and tests before deletion. Run module tests after each batch.

### Accidental removal of adapter code

Risk:
Deleting an old-looking type that is still the transport bridge into the new flow.

Mitigation:
Only remove a legacy type after its last consumer is migrated or removed in the same batch.

### Documentation drift after code deletion

Risk:
README files continue describing the deleted pipeline.

Mitigation:
Rewrite module READMEs in the same batch that removes the old code.

## Verification Strategy

After each cleanup batch, run the smallest relevant test set first, then broader regression:

- `./gradlew :common:test`
- `./gradlew :postbox:test`
- `./gradlew :postman:test`
- `./gradlew :push:test`
- `./gradlew :postoffice:test`
- `./gradlew test`

If a batch causes failure outside the intended cleanup boundary, stop and repair before proceeding.
