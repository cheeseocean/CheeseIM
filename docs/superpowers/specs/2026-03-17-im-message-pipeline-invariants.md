# IM Message Pipeline Invariants

## Purpose

This document defines the non-negotiable invariants for the rebuilt CheeseIM message pipeline.

These invariants exist to keep the message path:

- maintainable
- compensable
- explainable

Any change that cannot preserve these invariants should be treated as an architecture change, not a routine feature addition.

## Scope

The invariants in this document apply to the core IM message path:

- `postoffice`
- `postman`
- `postbox`
- `push`
- shared contracts in `common`

They are stricter than general coding guidelines. They define ownership and truth boundaries.

## Invariants

### 1. `postoffice` owns connection facts, not message truth

`postoffice` may:

- authenticate and bind sessions
- manage Netty channels and connection lifecycle
- publish and query online route state
- normalize gateway requests and responses
- push to currently connected devices

`postoffice` must not:

- define final delivery success
- persist authoritative message lifecycle state
- decide recall, read, or ack convergence
- become the source of truth for offline recovery

If a message question starts with "what really happened", the answer must not live only in `postoffice`.

### 2. `postman` is the only delivery control plane

`postman` owns:

- send idempotency
- delivery state transitions
- delivery task truth
- timeout handling
- compensation scheduling
- ack, read, and recall convergence

No other module may introduce a competing delivery state machine.

If a feature changes delivery semantics, it must converge through `postman`.

### 3. `postbox` stores message fact and inbox projection separately

`postbox` must keep two distinct storage views:

- message fact: one durable logical message record
- inbox projection: per-user delivery and unread recovery view

These concerns must not collapse back into one overloaded model.

This separation is required because:

- message facts are written once and read for truth
- inbox projections are user-facing recovery state
- ack/read/recall must update user state without redefining message fact ownership

### 4. `push` is a downstream touchpoint, never a truth source

`push` may:

- decide whether an offline push attempt is still needed
- deduplicate push attempts
- execute vendor push
- cancel stale attempts after convergence

`push` must not:

- define whether a message is delivered
- define whether a message is read
- persist authoritative lifecycle state independently of `postman`
- reintroduce online-routing logic

Push success is only an attempt signal.

### 5. Every accepted logical send has one stable identity chain

For every accepted send:

- the client provides `clientMsgId`
- the system produces one stable `serverMsgId`
- retries converge to the same logical outcome

No new code may create a second identity path for the same logical send without going through the idempotency layer.

### 6. State transitions must be explicit and replay-safe

A delivery transition must:

- correspond to a named state
- be idempotent under retry
- be safe under duplicate event handling
- be attributable to one clear trigger

If an operation cannot be replayed safely, it is not ready for the message mainline.

### 7. Compensation is mandatory for incomplete online delivery

If online delivery can time out, fail, or become ambiguous, there must be a compensating path.

Compensation must:

- be scheduled from the delivery control plane
- run against delivery task truth
- be observable through logs and task state

Best-effort delivery without compensating recovery is not acceptable in the main path.

### 8. Ack, read, and recall must converge in one place

The interpretation of:

- `RECEIVED`
- `READ`
- `RECALL`

must converge through one control point in `postman`, with storage effects applied through `postbox`.

No gateway handler, provider callback, or sidecar service may independently redefine these semantics.

### 9. Gateway route state and delivery state must stay distinct

Online route state answers:

- where a user device appears reachable right now

Delivery state answers:

- what happened to a logical message

These are related but not interchangeable.

Any design that uses temporary route presence as final delivery truth is invalid.

### 10. Explanation must be reconstructible from code and stored state

For any message, the system should be able to explain:

- why it was accepted or deduplicated
- whether online routing existed
- whether inbox persistence happened
- whether push was triggered or suppressed
- why recall succeeded or failed

If the answer depends on implicit listener ordering or disappearing in-memory state, the design is drifting.

## Architecture Review Questions

Before merging a message-path change, answer these questions:

1. Which module owns the new state?
2. If the operation fails halfway, which module compensates it?
3. Which stored record explains the final outcome?
4. Does this introduce a second source of truth?
5. Does this make retries less deterministic?

If any answer is unclear, stop and redesign before implementation.

## Change Rejection Rules

Reject or escalate any change that does one of the following:

- moves delivery truth into `postoffice`
- moves lifecycle truth into `push`
- mixes inbox projection and message fact back into one overloaded model
- adds implicit listener chains as the only business explanation path
- bypasses idempotency for accepted sends
- adds non-compensated failure modes to the mainline

## Practical Interpretation

In day-to-day development, the safest default is:

- transport concerns go to `postoffice`
- delivery semantics go to `postman`
- persistence truth goes to `postbox`
- vendor touchpoints go to `push`

If a requirement does not fit cleanly, treat that as an architectural design problem rather than forcing it through an existing handler or listener.
