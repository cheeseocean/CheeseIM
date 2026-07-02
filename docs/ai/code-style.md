# CheeseIM Code Style

This document records repo-specific conventions for the Java server, Go client SDK, CheeseBox, and documentation.

## Java Server

- Keep controller code in `server/api-server` thin.
- Controllers may handle REST request parsing, principal extraction, facade orchestration, and response mapping.
- Do not push HTTP request or response models down into domain services.
- Prefer small service classes with one clear responsibility.
- Keep module boundaries explicit: auth/session, business logic, gateway, ingress, orchestration, and delivery/push should remain separate concepts.
- Use the shared protocol and domain types from `server/common-api` instead of duplicating message or event models.
- When a change affects message flow or connection flow, verify the nearest module tests and the all-in-one path.

## Go

- Keep `sdks/go` reusable and transport-focused.
- Keep `apps/CheeseBox` app-specific state in the app layer.
- Prefer small packages with narrow interfaces.
- Avoid global state unless the package already uses it for a clear reason.
- Make context and cancellation explicit in network and IO code.
- Keep tests close to the behavior they protect.

## Documentation

- Write docs from the product and workflow perspective first.
- Keep `README.md` and `README.en.md` aligned on scope, status, and startup instructions.
- Treat historical plans and specs as background, not as the source of truth.
- Prefer concrete commands, file paths, and module names over abstract descriptions.
- When a doc describes current behavior, verify it against code or another maintained doc before changing it.

## Testing And Verification

- Docs-only changes: `git diff --check`
- Java changes: use the smallest relevant `./gradlew` task first, then expand only if needed
- Go changes: use the smallest relevant `go test` target first, then broaden only if needed
- Cross-module changes: verify the smallest path that crosses the boundary you changed

## Review Standard

Before handing off, check:

- names match the repo’s current module and path names
- commands are runnable from the repo root or from the documented module root
- examples are concrete, not generic
- no stale references to old client stacks or retired paths

