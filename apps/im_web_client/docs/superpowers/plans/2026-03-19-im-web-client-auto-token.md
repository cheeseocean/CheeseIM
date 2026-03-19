# IM Web Client Auto Token Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate the IM auth JWT on the client from `userID` and `platformID`, using a configurable secret key and expiration, so the login flow no longer requires manual token entry.

**Architecture:** Keep the existing `AuthSession` and websocket auth message unchanged, but change the login UI so it derives `token` at submit time. Add a focused JWT utility that mirrors the backend's HS256 payload shape and read its inputs from Vite env-backed config.

**Tech Stack:** React 18, TypeScript, Vite, Vitest

---

### Task 1: Cover auto token generation in the app login flow

**Files:**
- Modify: `src/test/App.test.tsx`

- [ ] **Step 1: Write the failing test**

Add a test that fills `WS URL`, `User ID`, and `Platform ID`, clicks `Connect`, and asserts `dependencies.connect` receives a session whose `token` is a generated JWT rather than typed input.

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- src/test/App.test.tsx`
Expected: FAIL because the form still requires manual token input and does not generate one.

- [ ] **Step 3: Write minimal implementation**

Update the login flow to generate the token before calling `onSubmit`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- src/test/App.test.tsx`
Expected: PASS

### Task 2: Add configurable JWT generation support

**Files:**
- Create: `src/features/auth/tokenConfig.ts`
- Create: `src/features/auth/tokenGenerator.ts`
- Modify: `src/features/auth/LoginView.tsx`

- [ ] **Step 1: Write the failing test**

Extend the login-flow test to assert the generated JWT decodes to `sub=userID`, `platformID`, and carries `iat`/`exp`.

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- src/test/App.test.tsx`
Expected: FAIL because the generated token logic does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Implement HS256 JWT signing using browser crypto-compatible utilities, add Vite env config readers, and remove the manual token field from the form.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- src/test/App.test.tsx`
Expected: PASS

### Task 3: Verify no regression in current behavior

**Files:**
- Verify only

- [ ] **Step 1: Run focused verification**

Run: `npm test -- src/test/App.test.tsx`
Expected: PASS

- [ ] **Step 2: Run broader verification**

Run: `npm test`
Expected: PASS
