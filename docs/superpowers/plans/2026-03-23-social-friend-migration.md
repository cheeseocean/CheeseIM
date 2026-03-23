# Social Friend Migration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move friend-request and friendship management from `authcenter` into a new `social` module with Mongo as the source of truth and Redis as cache/index.

**Architecture:** `social` becomes the owner of relationship-domain HTTP and Dubbo APIs. Friend requests and friendships persist in Mongo documents; Redis is used only for fast list/index lookups and refreshed from write operations. `authcenter` keeps authentication only and stops exposing or storing friendship data.

**Tech Stack:** Spring Boot 3.2, Spring Web, Spring Data MongoDB, Spring Data Redis, Dubbo, JUnit 5, Mockito

---

### Task 1: Create `social` module wiring

**Files:**
- Create: `server/social/build.gradle`
- Create: `server/social/src/main/java/com/cheeseocean/im/social/Social.java`
- Create: `server/config/src/main/resources/application-social.yml`
- Create: `server/config/src/main/resources/module-social.yml`
- Modify: `server/settings.gradle`
- Modify: `server/bootstrap-all/build.gradle`
- Modify: `server/bootstrap-all/src/main/java/com/cheeseocean/im/bootstrap/all/AllInOneApplication.java`

- [ ] Add the new module to Gradle and bootstrap wiring.
- [ ] Give `social` Mongo, Redis, Web, Validation, and Dubbo dependencies.
- [ ] Make `bootstrap-all` scan and load `social` beans and Mongo repositories.
- [ ] Add standalone config entries for the `social` app.

### Task 2: Move friend service tests to `social`

**Files:**
- Create: `server/social/src/test/java/com/cheeseocean/im/social/service/FriendServiceTest.java`
- Modify: `server/authcenter/src/test/java/com/cheeseocean/im/authcenter/service/FriendServiceTest.java`

- [ ] Copy the existing friend service behavior tests to `social`.
- [ ] Change imports to the new package names.
- [ ] Run the `social` friend service test first and confirm it fails because implementation does not exist yet.
- [ ] Remove the obsolete `authcenter` friend service test once the new one passes.

### Task 3: Implement Mongo-backed friend domain in `social`

**Files:**
- Create: `server/social/src/main/java/com/cheeseocean/im/social/domain/FriendRequestDoc.java`
- Create: `server/social/src/main/java/com/cheeseocean/im/social/domain/FriendshipDoc.java`
- Create: `server/social/src/main/java/com/cheeseocean/im/social/repository/FriendRequestMongoRepository.java`
- Create: `server/social/src/main/java/com/cheeseocean/im/social/repository/FriendshipMongoRepository.java`
- Create: `server/social/src/main/java/com/cheeseocean/im/social/repository/FriendRepository.java`
- Create: `server/social/src/main/java/com/cheeseocean/im/social/service/FriendService.java`

- [ ] Write repository/service code for:
  - `listFriends`
  - `listIncomingRequests`
  - `listOutgoingRequests`
  - `sendFriendRequest`
  - `acceptFriendRequest`
  - `rejectFriendRequest`
  - `cancelFriendRequest`
  - `areAcceptedFriends`
- [ ] Persist primary state in Mongo.
- [ ] Refresh Redis indexes after writes.
- [ ] Preserve current HTTP/Dubbo behavior for request validation and response payloads.

### Task 4: Expose `social` HTTP API and remove `authcenter` ownership

**Files:**
- Create: `server/social/src/main/java/com/cheeseocean/im/social/controller/FriendController.java`
- Create: `server/social/src/main/java/com/cheeseocean/im/social/model/AddFriendRequest.java`
- Create: `server/social/src/main/java/com/cheeseocean/im/social/model/FriendRequestActionRequest.java`
- Modify: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/auth/AccessTokenService.java` (if needed for cross-module injection only)
- Delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/controller/FriendController.java`
- Delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/service/FriendService.java`
- Delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/repository/FriendRepository.java`
- Delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/model/AddFriendRequest.java`
- Delete: `server/authcenter/src/main/java/com/cheeseocean/im/authcenter/model/FriendRequestActionRequest.java`

- [ ] Recreate the same `/api/im/friends/**` HTTP surface in `social`.
- [ ] Reuse the existing access-token validation service from `authcenter` instead of duplicating auth logic.
- [ ] Remove friend-domain ownership from `authcenter`.

### Task 5: Verification and cleanup

**Files:**
- Modify: `server/authcenter/build.gradle` (if Redis-only friend dependencies become unnecessary)
- Modify: tests as needed

- [ ] Run `./gradlew :social:test` and confirm green.
- [ ] Run `./gradlew :authcenter:test` and fix fallout.
- [ ] Run `./gradlew :bootstrap-all:test` if available or at least compile affected modules.
- [ ] Verify no friend-domain production classes remain under `authcenter`.
