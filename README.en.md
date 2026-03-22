# CheeseIM

CheeseIM is a monorepo for an instant messaging system built around Java 17, Spring Boot, Netty, Dubbo, Kafka, Redis, and MongoDB.

This repository currently contains:

- a multi-module backend under `server/`
- client applications under `apps/`
- a reusable TCP SDK under `sdks/`
- architecture and runbook documents under `docs/`

The current codebase is centered on a rebuilt IM message pipeline with clear service boundaries:

- `postoffice`: access layer and online route management
- `postman`: delivery orchestration and convergence
- `postbox`: durable message history and query boundary
- `push`: online dispatch execution and offline push
- `authcenter`: lightweight authentication bootstrap

This README focuses on the repository as it exists today, rather than describing an idealized feature list.

## Overview

The current repository is organized around a rebuilt IM message pipeline with these primary service boundaries:

- `postoffice`: access layer, long-lived connections, and online routing
- `postman`: delivery orchestration, idempotency, compensation, and state convergence
- `postbox`: durable message history, query boundary, and conversation views
- `push`: online dispatch execution and offline push decisions
- `authcenter`: lightweight authentication bootstrap

If you are new to the repository, the simplest mental model is:

- the root directory is a monorepo workspace
- backend build and runtime work happens mainly under `server/`
- clients and SDKs are developed in their own subdirectories

## Repository Layout

```text
CheeseIM/
├── server/                  # Java 17 Gradle multi-module backend
│   ├── authcenter/          # authentication service
│   ├── bootstrap-all/       # all-in-one runtime module
│   ├── common-api/          # shared APIs and contracts
│   ├── common-core/         # shared infrastructure and core utilities
│   ├── config/              # service configuration files
│   ├── postoffice/          # gateway / TCP / WebSocket access
│   ├── postman/             # delivery orchestration and state machine
│   ├── postbox/             # message storage and query boundary
│   └── push/                # delivery execution and offline push
├── apps/
│   ├── im_flutter_client/   # Flutter client
│   ├── im_java_client_demo/ # Java TCP client demo
│   └── im_web_client/       # React + Vite Web client
├── sdks/
│   └── im_tcp_sdk/          # Dart TCP SDK
├── distro/docker/           # local middleware orchestration
└── docs/                    # runbooks, specs, and plans
```

## Core Service Responsibilities

### `postoffice`

`postoffice` is the access and online-route layer. It is responsible for:

- TCP / WebSocket long-lived connections
- authentication, heartbeat handling, disconnect handling, and session binding
- maintaining user-device-gateway online route snapshots
- normalizing inbound client messages and receipts before forwarding downstream

It does not own durable message truth, offline storage, or full delivery orchestration.

### `postman`

`postman` is the orchestration core of the message pipeline. It is responsible for:

- send idempotency
- conversation sequence allocation
- delivery state progression
- ack, read, and recall convergence
- compensation scheduling and dead-letter handling

It does not directly own query-side persistence models or gateway access.

### `postbox`

`postbox` is the storage boundary. It is responsible for:

- durable message history persistence
- block-based history storage and message ID mapping
- history pull and conversation view queries
- Redis-backed hot state related to conversations

### `push`

`push` is the delivery-execution and offline-push boundary. It is responsible for:

- consuming delivery events and executing online delivery
- deciding whether vendor push is still needed
- deduplicating push attempts
- canceling stale push attempts after receipt convergence
- integrating with APNs / FCM / JPush and similar providers

### `authcenter`

`authcenter` provides a lightweight authentication entry point. In the current repository, it is primarily used for local integration and demo login flows.

## Technology Stack

### Backend

- Java 17
- Gradle multi-module build
- Spring Boot 3.2.x
- Apache Dubbo 3.x
- Netty
- Kafka
- Redis
- MongoDB

### Clients and SDKs

- React + Vite Web client
- Flutter client
- Dart TCP SDK
- Java TCP CLI demo

## Infrastructure Dependencies

Based on the current configuration, the backend expects these middleware dependencies:

- Nacos for service registry and config
- Kafka for asynchronous event flow and delivery pipeline integration
- Redis for online routes, hot state, idempotency, and cache data
- MongoDB for message history and durable query data

The repository already includes a local middleware compose file:

```bash
docker-compose -f distro/docker/docker-compose.middleware.yml up -d
```

That compose file currently includes:

- `nacos`
- `kafka`
- `zookeeper`
- `kafka-console`

Notes:

- Redis and MongoDB are not included in that compose file and need to be prepared separately
- the default addresses are `localhost:6379` for Redis and `localhost:27017` for MongoDB
- backend configuration files live under `server/config/src/main/resources/`

## Running the System

### 1. Start middleware

Run from the repository root:

```bash
docker-compose -f distro/docker/docker-compose.middleware.yml up -d
```

### 2. Enter the backend directory

The backend Gradle wrapper is under `server/`:

```bash
cd server
```

### 3. Build the backend

```bash
./gradlew build
```

### 4. Start core services

For local integration, the common setup is to run these modules separately:

```bash
./gradlew :authcenter:bootRun
./gradlew :postbox:bootRun
./gradlew :postman:bootRun
./gradlew :push:bootRun
./gradlew :postoffice:bootRun
```

### 5. Start all-in-one mode

If you want to run the backend modules in a single process locally, use `bootstrap-all`:

```bash
./gradlew :bootstrap-all:bootRun
```

The current `application-all.yml` shows that:

- the all-in-one HTTP port is `18079`
- Dubbo runs in `injvm` mode and is not registered externally

## Default Ports

According to the current configuration files, the default ports are:

- `postoffice` HTTP: `18080`
- `postoffice` WebSocket: `5147`
- `postoffice` TCP: `5148`
- `postman` HTTP: `18081`
- `postbox` HTTP: `18082`
- `push` HTTP: `18083`
- `authcenter` HTTP: `18084`
- `bootstrap-all` HTTP: `18079`
- Nacos: `8848`
- Kafka: `9092`

## Clients and Demo Programs

### Java TCP Client Demo

`apps/im_java_client_demo` is the most direct client-side demo for local backend integration. It is intended to verify:

- login
- TCP connect and auth
- one-to-one text sending
- inbound message receiving

Run it with:

```bash
cd server
./gradlew :apps:im_java_client_demo:cli-demo:run --args="--host 127.0.0.1 --tcp-port 5148 --base-url http://127.0.0.1:18084"
```

Notes:

- the command above uses the current default `authcenter` HTTP port `18084`
- `apps/im_java_client_demo/README.md` still includes an older `8080` example
- confirm your actual `base-url` before running the demo

### Web / Flutter / Dart SDK

The repository also contains:

- `apps/im_web_client`
- `apps/im_flutter_client`
- `sdks/im_tcp_sdk`

Their development and test entry points are summarized in [docs/client-runbook.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/client-runbook.md).

## Tests and Verification

### Backend module tests

Run from `server/`:

```bash
./gradlew :postoffice:test
./gradlew :postman:test
./gradlew :postbox:test
./gradlew :push:test
```

### Local smoke test

The repository already includes a local IM smoke test runbook:

[docs/superpowers/runbooks/im-local-smoke-test.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/superpowers/runbooks/im-local-smoke-test.md)

It covers:

- online delivery
- offline fallback
- reconnect pull
- push cancellation after read ack

Minimal regression command example:

```bash
cd server
./gradlew :postbox:test :postman:test :push:test :postoffice:test
```

## Configuration Entry Points

The main configuration files are:

- `server/config/src/main/resources/common.yml`
- `server/config/src/main/resources/application-postoffice.yml`
- `server/config/src/main/resources/application-postman.yml`
- `server/config/src/main/resources/application-postbox.yml`
- `server/config/src/main/resources/application-push.yml`
- `server/config/src/main/resources/application-authcenter.yml`
- `server/config/src/main/resources/application-all.yml`

Current module defaults include:

- `postoffice` enables both WebSocket and TCP access by default
- `postman` enables compensation listeners by default
- `push` enables scheduled tasks by default
- `postbox` wires MongoDB, Redis, and Kafka by default

## Architecture Documents

If you want to understand the rebuilt architecture before reading code, start with:

- [docs/superpowers/specs/2026-03-17-im-architecture-design.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/superpowers/specs/2026-03-17-im-architecture-design.md)
- [docs/superpowers/specs/2026-03-17-im-message-pipeline-invariants.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/superpowers/specs/2026-03-17-im-message-pipeline-invariants.md)
- [docs/client-runbook.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/docs/client-runbook.md)

The `server/` directory also contains additional historical architecture and refactor documents for deeper context.

## Current Status and Caveats

This repository is better understood as a converging IM system implementation than as a fully productized end-user messaging platform.

Keep these points in mind:

- the repository root is a monorepo workspace, not the backend runtime root
- backend build and boot commands should generally be executed from `server/`
- this document only describes capabilities reflected in the current code and configuration
- some older demo or design documents may use ports or assumptions that differ from the current default configuration
