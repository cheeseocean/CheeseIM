# Java TCP Client Demo Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java TCP client demo that can log in with username/password, connect to the current server over the custom TCP protocol, authenticate, and exchange one-to-one text messages through a CLI.

**Architecture:** Create a standalone app under `apps/im_java_client_demo` with a reusable `client-core` module and a thin `cli-demo` shell. The client core owns HTTP login, TCP protocol encode/decode, socket lifecycle, auth/send flows, and inbound event dispatch; the CLI module only handles commands and terminal presentation.

**Tech Stack:** Gradle multi-module Java app, JDK networking (`Socket`, `HttpClient`), Jackson, JUnit 5

---

## File Structure

### New modules

- Create: `apps/im_java_client_demo/build.gradle`
- Create: `apps/im_java_client_demo/client-core/build.gradle`
- Create: `apps/im_java_client_demo/cli-demo/build.gradle`
- Modify: `settings.gradle`

### `client-core`

- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/auth/AuthHttpClient.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/auth/AuthLoginRequest.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/auth/AuthLoginResponse.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/protocol/TcpPacket.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/protocol/TcpPacketCodec.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/protocol/TcpMessageTypes.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/session/ClientSession.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/session/ConnectionState.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/IncomingMessageListener.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/RequestTracker.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/TcpImClient.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/TcpClientConfig.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/PayloadFactory.java`

### `cli-demo`

- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/CliApplication.java`
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/CliCommandLoop.java`
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/ConsolePrinter.java`
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/DemoState.java`
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/ParsedCommand.java`

### Tests

- Create: `apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/protocol/TcpPacketCodecTest.java`
- Create: `apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/auth/AuthHttpClientTest.java`
- Create: `apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/tcp/RequestTrackerTest.java`
- Create: `apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/tcp/TcpImClientTest.java`
- Create: `apps/im_java_client_demo/cli-demo/src/test/java/com/cheeseocean/im/client/cli/CliCommandLoopTest.java`

### Docs

- Create: `apps/im_java_client_demo/README.md`

## Task 1: Register the new app modules

**Files:**
- Create: `apps/im_java_client_demo/build.gradle`
- Create: `apps/im_java_client_demo/client-core/build.gradle`
- Create: `apps/im_java_client_demo/cli-demo/build.gradle`
- Modify: `settings.gradle`

- [ ] **Step 1: Add module includes to settings**

Add exact module paths in `settings.gradle`:

```groovy
include ':apps:im_java_client_demo'
include ':apps:im_java_client_demo:client-core'
include ':apps:im_java_client_demo:cli-demo'
```

- [ ] **Step 2: Add parent app build file**

Create `apps/im_java_client_demo/build.gradle` with:

```groovy
subprojects {
    apply plugin: 'java'

    group = 'com.cheeseocean.im'
    version = '1.0.0'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    repositories {
        mavenCentral()
    }
}
```

- [ ] **Step 3: Add `client-core` build file**

Create `apps/im_java_client_demo/client-core/build.gradle` with:

```groovy
dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.2'

    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.mockito:mockito-core:5.12.0'
}

test {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Add `cli-demo` build file**

Create `apps/im_java_client_demo/cli-demo/build.gradle` with:

```groovy
application {
    mainClass = 'com.cheeseocean.im.client.cli.CliApplication'
}

dependencies {
    implementation project(':apps:im_java_client_demo:client-core')
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.2'

    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

test {
    useJUnitPlatform()
}
```

- [ ] **Step 5: Verify module graph**

Run: `./gradlew projects`
Expected: the new `:apps:im_java_client_demo:*` modules appear

- [ ] **Step 6: Commit**

```bash
git add settings.gradle apps/im_java_client_demo
git commit -m "feat: add java client demo modules"
```

## Task 2: Build the protocol codec with tests first

**Files:**
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/protocol/TcpPacket.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/protocol/TcpPacketCodec.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/protocol/TcpMessageTypes.java`
- Test: `apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/protocol/TcpPacketCodecTest.java`

- [ ] **Step 1: Write the failing codec test**

Add tests that assert:

```java
@Test
void encodeAndDecodeShouldRoundTripAuthPacket() { }

@Test
void readPacketShouldHandleHeaderThenPayloadFromStream() { }

@Test
void operationIdShouldBeClampedToSixteenBytes() { }
```

- [ ] **Step 2: Run codec test to verify red state**

Run: `./gradlew :apps:im_java_client_demo:client-core:test --tests '*TcpPacketCodecTest'`
Expected: FAIL because codec classes do not exist

- [ ] **Step 3: Implement minimal packet model and codec**

Implement:
- fixed header constants
- `encode(TcpPacket)`
- `read(InputStream)`
- operation id clamp/pad to 16 bytes
- big-endian length handling

- [ ] **Step 4: Re-run codec test**

Run: `./gradlew :apps:im_java_client_demo:client-core:test --tests '*TcpPacketCodecTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/im_java_client_demo/client-core/src/main
git add apps/im_java_client_demo/client-core/src/test
git commit -m "feat: add tcp client packet codec"
```

## Task 3: Build HTTP login client with tests first

**Files:**
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/auth/AuthLoginRequest.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/auth/AuthLoginResponse.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/auth/AuthHttpClient.java`
- Test: `apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/auth/AuthHttpClientTest.java`

- [ ] **Step 1: Write the failing auth client test**

Cover:

```java
@Test
void loginShouldPostUsernamePasswordAndParseAccessToken() { }

@Test
void loginShouldFailWhenAccessTokenMissing() { }
```

Use a stubbed `HttpClient` seam or injected exchange function so the test does not make network calls.

- [ ] **Step 2: Run auth client test to verify red state**

Run: `./gradlew :apps:im_java_client_demo:client-core:test --tests '*AuthHttpClientTest'`
Expected: FAIL because auth client classes do not exist

- [ ] **Step 3: Implement minimal auth DTOs and HTTP login client**

Implement login against:
- `POST /api/auth/login`

Parse:
- `accessToken`
- optional `refreshToken`
- optional expiry fields

Throw a clear exception if `accessToken` is absent.

- [ ] **Step 4: Re-run auth client test**

Run: `./gradlew :apps:im_java_client_demo:client-core:test --tests '*AuthHttpClientTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/auth
git add apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/auth
git commit -m "feat: add auth http client for java demo"
```

## Task 4: Build client session and request tracking

**Files:**
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/session/ClientSession.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/session/ConnectionState.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/RequestTracker.java`
- Test: `apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/tcp/RequestTrackerTest.java`

- [ ] **Step 1: Write the failing request tracker test**

Cover:

```java
@Test
void trackerShouldRememberOutboundOperationMetadata() { }

@Test
void trackerShouldResolveAndRemoveOperationOnResponse() { }
```

- [ ] **Step 2: Run request tracker test to verify red state**

Run: `./gradlew :apps:im_java_client_demo:client-core:test --tests '*RequestTrackerTest'`
Expected: FAIL because tracker classes do not exist

- [ ] **Step 3: Implement minimal session and tracker**

Implement:
- session state fields
- connection/auth flags
- tracker map keyed by `operationId`
- request type and timestamp bookkeeping

- [ ] **Step 4: Re-run request tracker test**

Run: `./gradlew :apps:im_java_client_demo:client-core:test --tests '*RequestTrackerTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/session
git add apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/RequestTracker.java
git add apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/tcp/RequestTrackerTest.java
git commit -m "feat: add java client session and request tracking"
```

## Task 5: Build the TCP IM client with tests first

**Files:**
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/IncomingMessageListener.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/TcpClientConfig.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/PayloadFactory.java`
- Create: `apps/im_java_client_demo/client-core/src/main/java/com/cheeseocean/im/client/tcp/TcpImClient.java`
- Test: `apps/im_java_client_demo/client-core/src/test/java/com/cheeseocean/im/client/tcp/TcpImClientTest.java`

- [ ] **Step 1: Write the failing TCP client test**

Cover:

```java
@Test
void connectShouldReadInitialConnectPushAndTransitionConnected() { }

@Test
void authenticateShouldSendTcpAuthReqWithTokenUserAndPlatform() { }

@Test
void sendTextShouldEmitSingleChatPayloadAndTrackAck() { }

@Test
void inboundNotifyShouldBeDeliveredToListener() { }
```

Use fake input/output streams or a socket seam rather than a real network dependency.

- [ ] **Step 2: Run TCP client test to verify red state**

Run: `./gradlew :apps:im_java_client_demo:client-core:test --tests '*TcpImClientTest'`
Expected: FAIL because TCP client classes do not exist

- [ ] **Step 3: Implement minimal TCP client**

Implement:
- socket connect/disconnect
- background read loop
- connect-success handling
- auth request send
- heartbeat send
- single-chat send
- response and notify dispatch

Payloads to support:
- auth payload
- send payload with `clientMsgID`, `recvID`, `content`, `contentType=101`, `chatType=1`

- [ ] **Step 4: Re-run TCP client test**

Run: `./gradlew :apps:im_java_client_demo:client-core:test --tests '*TcpImClientTest'`
Expected: PASS

- [ ] **Step 5: Run the full `client-core` test suite**

Run: `./gradlew :apps:im_java_client_demo:client-core:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add apps/im_java_client_demo/client-core
git commit -m "feat: add java tcp im client core"
```

## Task 6: Build the CLI shell with tests first

**Files:**
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/CliApplication.java`
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/CliCommandLoop.java`
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/ConsolePrinter.java`
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/DemoState.java`
- Create: `apps/im_java_client_demo/cli-demo/src/main/java/com/cheeseocean/im/client/cli/ParsedCommand.java`
- Test: `apps/im_java_client_demo/cli-demo/src/test/java/com/cheeseocean/im/client/cli/CliCommandLoopTest.java`

- [ ] **Step 1: Write the failing CLI parsing test**

Cover:

```java
@Test
void parseLoginCommandShouldExtractUserPasswordAndPlatform() { }

@Test
void parseSendCommandShouldPreserveRemainingTextAsMessageBody() { }

@Test
void parseUnknownCommandShouldReturnHelpCommand() { }
```

- [ ] **Step 2: Run CLI test to verify red state**

Run: `./gradlew :apps:im_java_client_demo:cli-demo:test --tests '*CliCommandLoopTest'`
Expected: FAIL because CLI classes do not exist

- [ ] **Step 3: Implement the CLI shell**

Commands:
- `login <userId> <password> <platformId>`
- `connect`
- `send <peerUserId> <text>`
- `heartbeat`
- `status`
- `quit`

Wire the command loop to:
- `AuthHttpClient`
- `TcpImClient`
- listener callbacks printed through `ConsolePrinter`

- [ ] **Step 4: Re-run CLI test**

Run: `./gradlew :apps:im_java_client_demo:cli-demo:test --tests '*CliCommandLoopTest'`
Expected: PASS

- [ ] **Step 5: Run both client module tests**

Run: `./gradlew :apps:im_java_client_demo:client-core:test :apps:im_java_client_demo:cli-demo:test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add apps/im_java_client_demo/cli-demo
git commit -m "feat: add java tcp cli demo shell"
```

## Task 7: Add usage docs and final verification

**Files:**
- Create: `apps/im_java_client_demo/README.md`

- [ ] **Step 1: Write the app README**

Document:
- required server endpoints and ports
- sample run command
- supported CLI commands
- two-terminal demo flow

- [ ] **Step 2: Verify the app compiles and can launch**

Run: `./gradlew :apps:im_java_client_demo:cli-demo:run --args="--help"`
Expected: process prints CLI help or startup usage and exits cleanly

- [ ] **Step 3: Run all client demo tests**

Run: `./gradlew :apps:im_java_client_demo:client-core:test :apps:im_java_client_demo:cli-demo:test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add apps/im_java_client_demo/README.md
git commit -m "docs: add java tcp client demo usage"
```

## Task 8: End-to-end manual demo verification

**Files:**
- No code changes required unless issues are found

- [ ] **Step 1: Start the required server modules locally**

Run the current local stack needed for:
- auth login
- TCP gateway
- message send and receive

- [ ] **Step 2: Launch terminal A**

Run:

```bash
./gradlew :apps:im_java_client_demo:cli-demo:run --args="--host 127.0.0.1 --tcp-port 5148 --base-url http://127.0.0.1:8080"
```

Then execute:

```text
login userA passwordA 2
connect
```

- [ ] **Step 3: Launch terminal B**

Run the same command in another shell, then execute:

```text
login userB passwordB 2
connect
```

- [ ] **Step 4: Verify bidirectional messaging**

Terminal A:

```text
send userB hello
```

Terminal B:

```text
send userA hi
```

Expected:
- both terminals show auth success
- sender shows send ack
- receiver shows inbound notify

- [ ] **Step 5: If the demo required fixes, patch and re-run the relevant automated tests**

- [ ] **Step 6: Final commit if demo fixes were needed**

```bash
git add apps/im_java_client_demo
git commit -m "fix: polish java tcp client demo flow"
```
