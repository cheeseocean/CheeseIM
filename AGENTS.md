# CheeseIM Agent Guide

This repository is an IM platform monorepo with a Java server, a Go client SDK, and the CheeseBox TUI client. Treat this file as the entry point for any agent working in the repo.

## Start Here

Read these files first when you need repo truth:

- `README.md`
- `README.en.md`
- `docs/client-runbook.md`
- the module README or design doc for the area you are touching

Treat `docs/superpowers/**`, `server/docs/superpowers/**`, and old handoff notes as reference material only. They are useful for background, but they do not override the current code or the root README files.

## Repo Facts

- `server/bootstrap-all` is the recommended local integration entry point.
- `apps/CheeseBox` is the primary real client for end-to-end verification.
- `apps/CheeseWeb` is experimental and not the main integration path.
- `sdks/go` is shared client infrastructure and should stay reusable.
- `README.md` and `README.en.md` should stay aligned on project facts.

## Working Rules

- Run shell commands from the repo root: `/home/crel/develop/java/CheeseIM`.
- Prefix shell commands with `rtk` unless you have a concrete reason not to.
- Prefer `rg` and `rg --files` for discovery.
- If `rtk` cannot express the search, use the raw tool directly and keep the query narrow.
- Do not use destructive git commands such as `git reset --hard` or `git checkout --`.
- Do not revert user changes that you did not make.
- If you touch docs only, verify with `git diff --check`.

## Change Policy

- Make the smallest change that solves the request.
- Preserve existing repo patterns instead of inventing a new style.
- Keep HTTP models in `server/api-server`; lower layers should return domain objects or simple results.
- Keep long-connection protocol work aligned with `common-api/src/main/proto/message_protocol.proto`.
- Treat `bootstrap-all` as the default dev path unless the task explicitly targets split-module deployment.
- Update both `README.md` and `README.en.md` when changing user-facing repo facts.

## Verification

- Docs-only change: `git diff --check`
- Java change: targeted `./gradlew` task for the touched module
- Go SDK / CheeseBox change: targeted `go test ./...` from the affected module root
- Cross-cutting change: run the smallest end-to-end check that proves the behavior

## Handoff Style

When you finish a task, report:

1. What changed
2. What you verified
3. What remains risky or untested
4. The exact file paths touched

