# API Server Module Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a unified `api-server` module that owns all HTTP controllers, keep `authcenter` as an auth domain module, and remove controller/web concerns from `business` and `authcenter`.

**Architecture:** Create a new Spring Boot-facing module for REST entrypoints and move all current controllers plus HTTP-only facades/session resolver into it. Keep `authcenter` responsible for login/session/ws-ticket services, keep `business` responsible for IM business services, and let `bootstrap-all` depend on `api-server` instead of scanning REST endpoints from multiple modules.

**Tech Stack:** Gradle multi-module Java, Spring Boot Web, Dubbo, existing `authcenter/business/postbox/postoffice` services.

---

> Spec status: the approved design discussion exists in chat, but the spec file `docs/superpowers/specs/2026-04-10-api-server-authcenter-boundary-design.md` is not present in the workspace. Before implementation commits, recreate that spec file from the approved design so the plan and implementation artifacts stay aligned.

### File Structure

**New module**
- Create: `server/api-server/build.gradle`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/ApiServerApplication.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/auth/AccessTokenSessionResolver.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/AuthController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/WsTicketController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/FriendController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/BlacklistController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/GroupController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/ConversationController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/UserSettingsController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/facade/ConversationFacade.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/ApiExceptionHandler.java`

**Move/retire from existing modules**
- Modify then delete: `server/business/src/main/java/com/cheeseocean/im/business/controller/*.java`
- Modify then delete: `server/business/src/main/java/com/cheeseocean/im/business/auth/AccessTokenSessionResolver.java`
- Modify then delete: `server/business/src/main/java/com/cheeseocean/im/business/facade/ConversationFacade.java`
- Modify then delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/controller/AuthController.java`
- Modify then delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/controller/WsTicketController.java`

**Build and wiring**
- Modify: `server/settings.gradle`
- Modify: `server/bootstrap-all/build.gradle`
- Modify: `server/business/build.gradle`
- Modify: `server/authcenter/build.gradle`

**Tests**
- Create: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/AuthControllerTest.java`
- Create: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/WsTicketControllerTest.java`
- Create: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/FriendControllerTest.java`
- Create: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/BlacklistControllerTest.java`
- Create: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/GroupControllerTest.java`
- Create: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/ConversationControllerTest.java`
- Create: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/UserSettingsControllerTest.java`
- Create: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/auth/AccessTokenSessionResolverTest.java`

### Task 1: Restore The Missing Spec Artifact

**Files:**
- Create: `docs/superpowers/specs/2026-04-10-api-server-authcenter-boundary-design.md`

- [ ] **Step 1: Write the spec file from the approved design**

Include:
- current module problem statement
- recommended `api-server` module
- reasons to keep `authcenter` as a separate auth domain module
- migration scope list
- dependency direction

- [ ] **Step 2: Verify the file exists**

Run: `ls docs/superpowers/specs/2026-04-10-api-server-authcenter-boundary-design.md`
Expected: file path printed

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-04-10-api-server-authcenter-boundary-design.md
git commit -m "docs: add api-server boundary design spec"
```

### Task 2: Add The New Module Skeleton

**Files:**
- Create: `server/api-server/build.gradle`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/ApiServerApplication.java`
- Modify: `server/settings.gradle`
- Modify: `server/bootstrap-all/build.gradle`

- [ ] **Step 1: Add the module to Gradle settings**

Update `server/settings.gradle`:

```gradle
include 'api-server'
```

- [ ] **Step 2: Create `server/api-server/build.gradle`**

Use minimal dependencies:

```gradle
dependencies {
    implementation project(':common-api')
    implementation project(':common-core')
    implementation project(':config')
    implementation project(':authcenter')
    implementation project(':business')
    implementation project(':postbox')

    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

- [ ] **Step 3: Create the module bootstrap class**

```java
@SpringBootApplication
public class ApiServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiServerApplication.class, args);
    }
}
```

- [ ] **Step 4: Point `bootstrap-all` at `api-server`**

In `server/bootstrap-all/build.gradle`, add `implementation project(':api-server')`.

- [ ] **Step 5: Run compile for new module graph**

Run: `cd server && ./gradlew :api-server:compileJava :bootstrap-all:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add server/settings.gradle server/api-server server/bootstrap-all/build.gradle
git commit -m "feat: add api-server module skeleton"
```

### Task 3: Move Auth HTTP Entry Points Into API Server

**Files:**
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/AuthController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/WsTicketController.java`
- Modify: `server/authcenter/build.gradle`
- Delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/controller/AuthController.java`
- Delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/controller/WsTicketController.java`
- Test: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/AuthControllerTest.java`
- Test: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/WsTicketControllerTest.java`

- [ ] **Step 1: Copy the controllers into `api-server` package**

Keep existing request/response types and injected services:
- `SessionLifecycleService`
- `SessionIssueService`

- [ ] **Step 2: Write controller tests in the new module**

Cover:
- login success
- refresh success
- logout success
- ws-ticket issue success
- `IllegalStateException -> 400`

- [ ] **Step 3: Remove the old authcenter controller classes**

Delete only after new tests compile.

- [ ] **Step 4: Remove `spring-boot-starter-web` need from `authcenter` if unused**

Confirm `server/authcenter/build.gradle` does not need web starter after controller removal.

- [ ] **Step 5: Run focused tests**

Run: `cd server && ./gradlew :api-server:test --tests com.cheeseocean.im.apiserver.controller.AuthControllerTest --tests com.cheeseocean.im.apiserver.controller.WsTicketControllerTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/AuthController.java \
        server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/WsTicketController.java \
        server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/AuthControllerTest.java \
        server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/WsTicketControllerTest.java \
        server/authcenter/build.gradle
git commit -m "feat: move auth controllers to api-server"
```

### Task 4: Move Shared HTTP Session Resolver Into API Server

**Files:**
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/auth/AccessTokenSessionResolver.java`
- Test: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/auth/AccessTokenSessionResolverTest.java`
- Delete: `server/business/src/main/java/com/cheeseocean/im/business/auth/AccessTokenSessionResolver.java`

- [ ] **Step 1: Move the resolver without changing logic**

Keep bean name stable if existing wiring depends on it:

```java
@Component("socialAccessTokenSessionResolver")
```

- [ ] **Step 2: Add focused unit test**

Cover:
- bearer token parsing
- invalid header rejection
- service invocation path

- [ ] **Step 3: Update imports in controllers that used the old resolver**

All new `api-server` controllers should depend on the moved resolver.

- [ ] **Step 4: Run compile**

Run: `cd server && ./gradlew :api-server:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add server/api-server/src/main/java/com/cheeseocean/im/apiserver/auth/AccessTokenSessionResolver.java \
        server/api-server/src/test/java/com/cheeseocean/im/apiserver/auth/AccessTokenSessionResolverTest.java
git commit -m "feat: move access token resolver to api-server"
```

### Task 5: Move Conversation HTTP Facade And Controllers Into API Server

**Files:**
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/facade/ConversationFacade.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/ConversationController.java`
- Test: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/ConversationControllerTest.java`
- Delete: `server/business/src/main/java/com/cheeseocean/im/business/facade/ConversationFacade.java`
- Delete: `server/business/src/main/java/com/cheeseocean/im/business/controller/ConversationController.java`

- [ ] **Step 1: Move facade first**

Keep the facade as HTTP orchestration only:
- resolve access token session
- call conversation list service
- call history query service

- [ ] **Step 2: Move controller and keep routes identical**

Routes to preserve:
- `GET /api/im/conversations`
- `GET /api/im/conversations/all`
- `GET /api/im/conversations/{conversationId}`
- `GET /api/im/conversations/batch`
- `GET /api/im/conversations/ids`
- `GET /api/im/conversations/ids/hash`
- `GET /api/im/conversations/not-notify`
- `GET /api/im/conversations/pinned`
- `PUT /api/im/conversations`
- `GET /api/im/conversations/{conversationId}/messages`

- [ ] **Step 3: Port existing controller tests into `api-server`**

Use current assertions from the existing business test as the baseline.

- [ ] **Step 4: Delete old business facade/controller**

- [ ] **Step 5: Run focused tests**

Run: `cd server && ./gradlew :api-server:test --tests com.cheeseocean.im.apiserver.controller.ConversationControllerTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add server/api-server/src/main/java/com/cheeseocean/im/apiserver/facade/ConversationFacade.java \
        server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/ConversationController.java \
        server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/ConversationControllerTest.java
git commit -m "feat: move conversation http entrypoints to api-server"
```

### Task 6: Move Remaining Business Controllers Into API Server

**Files:**
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/FriendController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/BlacklistController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/GroupController.java`
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/UserSettingsController.java`
- Test: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/FriendControllerTest.java`
- Test: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/BlacklistControllerTest.java`
- Test: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/GroupControllerTest.java`
- Test: `server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller/UserSettingsControllerTest.java`
- Delete:
  - `server/business/src/main/java/com/cheeseocean/im/business/controller/FriendController.java`
  - `server/business/src/main/java/com/cheeseocean/im/business/controller/BlacklistController.java`
  - `server/business/src/main/java/com/cheeseocean/im/business/controller/GroupController.java`
  - `server/business/src/main/java/com/cheeseocean/im/business/controller/UserSettingsController.java`

- [ ] **Step 1: Move each controller without changing URL contracts**

- [ ] **Step 2: Write/port controller tests**

Cover at least:
- auth resolution
- null/invalid request handling
- domain service invocation
- HTTP status mapping

- [ ] **Step 3: Delete old controller classes**

- [ ] **Step 4: Run module tests**

Run: `cd server && ./gradlew :api-server:test`
Expected: BUILD SUCCESSFUL or only pre-existing unrelated failures explicitly documented

- [ ] **Step 5: Commit**

```bash
git add server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller \
        server/api-server/src/test/java/com/cheeseocean/im/apiserver/controller
git commit -m "feat: move business controllers to api-server"
```

### Task 7: Remove Web Concerns From Business And Authcenter

**Files:**
- Modify: `server/business/build.gradle`
- Modify: `server/authcenter/build.gradle`

- [ ] **Step 1: Remove `spring-boot-starter-web` from `business` if no remaining web classes need it**

- [ ] **Step 2: Remove any no-longer-needed web dependency from `authcenter`**

- [ ] **Step 3: Re-run compile for affected modules**

Run: `cd server && ./gradlew :business:compileJava :authcenter:compileJava :api-server:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add server/business/build.gradle server/authcenter/build.gradle
git commit -m "refactor: remove web dependencies from domain modules"
```

### Task 8: Add Shared REST Exception Handling In API Server

**Files:**
- Create: `server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/ApiExceptionHandler.java`

- [ ] **Step 1: Introduce `@RestControllerAdvice`**

Handle at minimum:
- `IllegalStateException -> 400`
- `IllegalArgumentException -> 400`

Use a consistent response body:

```java
Map.of("code", 40001, "message", e.getMessage())
```

- [ ] **Step 2: Remove duplicated `@ExceptionHandler` methods from controllers where possible**

- [ ] **Step 3: Run controller tests**

Run: `cd server && ./gradlew :api-server:test`
Expected: BUILD SUCCESSFUL or documented unrelated failures only

- [ ] **Step 4: Commit**

```bash
git add server/api-server/src/main/java/com/cheeseocean/im/apiserver/controller/ApiExceptionHandler.java
git commit -m "refactor: centralize api exception handling"
```

### Task 9: Final Integration Verification

**Files:**
- Modify: `server/bootstrap-all/build.gradle` if needed for final wiring

- [ ] **Step 1: Run full compile for deployment path**

Run: `cd server && ./gradlew :common-api:compileJava :common-core:compileJava :authcenter:compileJava :business:compileJava :postbox:compileJava :postoffice:compileJava :api-server:compileJava :bootstrap-all:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run targeted controller tests**

Run: `cd server && ./gradlew :api-server:test`
Expected: BUILD SUCCESSFUL or documented pre-existing unrelated failures only

- [ ] **Step 3: Smoke-check boot wiring**

If local environment is available:

Run: `cd server && ./gradlew :bootstrap-all:bootRun`
Expected: application starts and exposes both `/api/auth/*` and `/api/im/*`

- [ ] **Step 4: Commit**

```bash
git add server
git commit -m "refactor: centralize http entrypoints in api-server"
```
