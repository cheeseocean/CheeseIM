# Message Protobuf Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有消息协议从 JSON 全量切换为 protobuf，并让 `postoffice` 基于 `Message` 模型完成二进制协议解析与投递。

**Architecture:** 在 `common-api` 定义 protobuf 协议和 Java mapper，网络层统一收发 protobuf envelope，业务层继续使用现有 `Message` / `SendMessageReq` 模型。TCP codec 与 WebSocket binary frame 共用同一套 envelope/message 映射逻辑。

**Tech Stack:** Java 17, Gradle, protobuf-java, Netty, Spring Boot

---

### Task 1: Add Protobuf Build Support

**Files:**
- Modify: `server/build.gradle`
- Modify: `server/common-api/build.gradle`

- [ ] Step 1: Add protobuf Gradle plugin and dependency coordinates
- [ ] Step 2: Configure `common-api` protobuf source generation
- [ ] Step 3: Run `./gradlew :common-api:compileJava`

### Task 2: Define Protocol Schema

**Files:**
- Create: `server/common-api/src/main/proto/message_protocol.proto`

- [ ] Step 1: Write failing compile expectation by referencing generated classes in a mapper test or compile path
- [ ] Step 2: Define `ProtoClientEnvelope`, `ProtoServerEnvelope`, `ProtoMessage`, `ProtoMessageOptions`
- [ ] Step 3: Run `./gradlew :common-api:generateProto`

### Task 3: Add Proto Mappers

**Files:**
- Create: `server/common-api/src/main/java/com/cheeseocean/im/common/api/protocol/ProtoMessageMapper.java`
- Create: `server/common-api/src/main/java/com/cheeseocean/im/common/api/protocol/ProtoEnvelopeMapper.java`

- [ ] Step 1: Add mapper tests for `Message <-> ProtoMessage`
- [ ] Step 2: Verify tests fail
- [ ] Step 3: Implement minimal mappers
- [ ] Step 4: Run mapper tests and `:common-api:compileJava`

### Task 4: Switch TCP Codec To Protobuf

**Files:**
- Modify: `server/postoffice/src/main/java/com/cheeseocean/im/postoffice/codec/TcpEnvelopeDecoder.java`
- Modify: `server/postoffice/src/main/java/com/cheeseocean/im/postoffice/codec/TcpEnvelopeEncoder.java`
- Test: `server/postoffice/src/test/java/com/cheeseocean/im/postoffice/client/TcpClientTest.java`

- [ ] Step 1: Update tests/fixtures to expect protobuf bytes instead of JSON payloads
- [ ] Step 2: Verify tests fail
- [ ] Step 3: Implement protobuf decode/encode
- [ ] Step 4: Run TCP protocol tests

### Task 5: Switch WebSocket To Binary Protobuf

**Files:**
- Modify: `server/postoffice/src/main/java/com/cheeseocean/im/postoffice/server/WsServerHandler.java`
- Modify: related WebSocket tests under `server/postoffice/src/test/java`

- [ ] Step 1: Add/adjust tests for binary frames only
- [ ] Step 2: Verify tests fail
- [ ] Step 3: Implement protobuf binary frame parsing and sending
- [ ] Step 4: Run WebSocket protocol tests

### Task 6: Remove JSON Parsing From Chat Handler

**Files:**
- Modify: `server/postoffice/src/main/java/com/cheeseocean/im/postoffice/handler/ChatMessageHandler.java`
- Test: `server/postoffice/src/test/java/com/cheeseocean/im/postoffice/handler/*`

- [ ] Step 1: Add failing test for protobuf-decoded `Message` body handling
- [ ] Step 2: Verify test fails
- [ ] Step 3: Remove JSON fallback parsing and consume protobuf-decoded `Message`
- [ ] Step 4: Run handler tests

### Task 7: End-to-End Verification

**Files:**
- Modify: protocol fixture files as needed

- [ ] Step 1: Run `./gradlew :common-api:test :postoffice:test`
- [ ] Step 2: Fix remaining protocol mismatches
- [ ] Step 3: Re-run verification until green
