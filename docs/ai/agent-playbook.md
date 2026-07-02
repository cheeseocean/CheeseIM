# CheeseIM Agent Playbook

This file turns prior collaboration patterns into a reusable operating prompt and a token-saving plan.

## Reusable Prompt

Use this prompt when another agent needs to continue work in this repository:

```text
You are working in the CheeseIM monorepo.

Start by reading README.md, README.en.md, and docs/client-runbook.md, then read the module docs for the area you are touching.
Treat docs/superpowers and older handoff notes as historical context only.

Use rtk-prefixed shell commands, prefer rg/rg --files for discovery, and stay at the repo root unless a task explicitly says otherwise.

Keep changes small, preserve existing repo patterns, and verify docs-only changes with git diff --check.
For Java changes, verify the smallest relevant Gradle task.
For Go changes, verify the smallest relevant go test target.

When reporting back, give the concrete file paths touched, what changed, what was verified, and what still needs attention.
```

## Your Working Preferences

- Prefer repository evidence before conclusions.
- Keep the first response short, then move directly into the work.
- When the task is about current project status, reconcile README and runbook facts against the codebase before answering.
- When documentation and implementation disagree, fix the document or the code instead of only describing the mismatch.
- Keep product-facing writing organized around user-facing behavior, not internal implementation detail.
- Use concrete dates when relative time is ambiguous.

## Behavior Constraints For Other Agents

- Do not guess at repo truth when a maintained file can confirm it.
- Do not treat historical plans as current truth without checking source files.
- Do not use destructive git operations.
- Do not leak unrelated refactors into a narrow task.
- Do not stop at analysis if the request is asking for an actual repo change.

## Token Saving Plan

1. Read the smallest authoritative set first: `README.md`, `README.en.md`, `docs/client-runbook.md`, and the module README for the touched area.
2. Use `rg --files` before broader searches so you only open the files you need.
3. Batch independent reads with parallel tool calls.
4. Narrow every search term to a module, class, command, or path.
5. Prefer targeted `sed -n` ranges over full-file reads once you know the section you need.
6. Avoid re-reading the same file unless the task scope changed.
7. For docs-only edits, stop at `git diff --check` instead of running a full build.
8. For code edits, verify the smallest affected boundary first, then expand only if the first check fails.
9. When a fact is likely to drift, verify it live instead of carrying old assumptions forward.
10. When a task is unclear, ask one focused question instead of exploring every branch.

## Short Execution Loop

1. Read the authoritative docs.
2. Inspect the exact files related to the task.
3. Make the smallest change.
4. Verify the smallest relevant target.
5. Report the exact outcome and file paths.

