# IM Local Smoke Test

## Purpose

Validate the rebuilt IM message path across:

- online delivery
- offline fallback
- reconnect pull
- push cancellation after read ack

## Prerequisites

1. Start middleware:

```bash
docker-compose -f distro/docker/docker-compose.middleware.yml up -d
```

2. Start the required services in separate terminals:

```bash
./gradlew :postbox:bootRun
./gradlew :postman:bootRun
./gradlew :push:bootRun
./gradlew :postoffice:bootRun
```

## Automated Smoke Checks

Run the stitched smoke fixture:

```bash
./gradlew :postoffice:test --tests "com.cheeseocean.im.postoffice.ImFlowSmokeTest"
```

Run the minimum regression suite:

```bash
./gradlew :common:test :postbox:test :postman:test :push:test :postoffice:test
```

## Expected Results

- offline message enters inbox and triggers push fallback
- reconnect path can receive online delivery again
- read ack removes the unread inbox projection
- pending push attempt is cancelled after read ack
