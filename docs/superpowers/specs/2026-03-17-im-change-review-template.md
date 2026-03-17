# IM Change Review Template

## Purpose

Use this template before implementing any change that touches the IM message mainline.

This template exists to force explicit answers about:

- ownership
- truth
- compensation
- explainability
- regression coverage

It should be filled out for:

- new IM features
- delivery semantic changes
- storage model changes
- ack/read/recall changes
- push strategy changes
- gateway behavior changes that affect message flow

## Required Inputs

- feature or change name
- affected modules
- user-visible behavior change
- failure scenarios
- verification plan

## Review Template

### 1. Change Summary

- Change name:
- Requested by:
- User-visible behavior:
- Affected modules:
- Out of scope:

### 2. Ownership

Answer all of the following:

1. Which module owns the new state?
2. Which module owns the transition logic?
3. Which module owns the stored truth?
4. Which module only observes or relays the result?

Expected pattern:

- transport and route state: `postoffice`
- delivery semantics: `postman`
- storage truth: `postbox`
- vendor touchpoints: `push`

If the ownership does not fit this pattern, explain why.

### 3. Identity and Idempotency

- What is the client identity for this flow?
- What is the server identity for this flow?
- What is the deduplication key?
- What retries can happen?
- How do duplicate requests converge?

Reject the change if it creates a second identity chain for an accepted logical send without a clear idempotency story.

### 4. Delivery State Impact

- Which delivery states can this change create?
- Which existing states can it transition from?
- Which transitions are newly introduced?
- Are the transitions replay-safe?
- Which transitions are terminal?

If state impact cannot be named explicitly, the design is incomplete.

### 5. Storage Impact

- Does this change modify message fact storage?
- Does this change modify inbox projection storage?
- Does this change modify both?
- Why is that split still valid after the change?

Never merge a change that silently collapses message fact and inbox projection responsibilities.

### 6. Compensation and Recovery

- What can fail halfway?
- How is incomplete work detected?
- Which module schedules compensation?
- What is retried?
- What becomes dead-letter or manual investigation?

If the change introduces an ambiguous failure mode without compensation, reject it.

### 7. Push Impact

- Can this change trigger offline push?
- Can it suppress push?
- Can it cancel an existing push attempt?
- Which state transition justifies that push behavior?

Push behavior must be derived from delivery truth, never invented independently.

### 8. Explainability

For one message affected by this change, answer:

- why was it accepted or rejected
- whether online routing existed
- whether inbox persistence happened
- whether push happened
- why the final state was reached

If the answer depends on hidden in-memory behavior or listener ordering, redesign the change.

### 9. Boundary Violations Check

Answer `yes` or `no`:

- Does this move message truth into `postoffice`?
- Does this move lifecycle truth into `push`?
- Does this bypass `postman` for delivery semantics?
- Does this add a second source of truth?
- Does this add a best-effort-only branch without compensation?

Any `yes` requires explicit architecture review before implementation.

### 10. Verification Plan

List the exact tests to add or update.

Required categories to consider:

- unit test for new state logic
- unit test for idempotency or duplicate handling
- unit test for compensation trigger or suppression
- storage regression test
- smoke or integration test if the cross-module path changed

Minimum template:

- New tests:
- Updated tests:
- Commands to run:

### 11. Merge Decision

- Safe to implement:
- Needs architecture review:
- Rejected:

Reason:

## Fast Review Checklist

Use this when the change is small but still touches the message path.

1. Does the change keep `postoffice` out of message truth?
2. Does the change keep `postman` as the delivery control plane?
3. Does the change preserve message fact vs inbox projection separation?
4. Does the change preserve replay safety?
5. Does the change preserve compensation for incomplete work?
6. Can the final outcome still be explained from stored state?

If any answer is `no` or `unclear`, stop and do the full review.
