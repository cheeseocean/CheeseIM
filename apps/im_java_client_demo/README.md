# Java TCP Client Demo

`im_java_client_demo` is a Java client-side demo for the current CheeseIM server.

It is intended to verify:

- auth HTTP login
- TCP connect and auth
- one-to-one text send
- inbound message receive

## Modules

- `client-core`: reusable auth, protocol, and TCP client logic
- `cli-demo`: interactive terminal shell

## Requirements

- `authcenter` login endpoint available at `http://127.0.0.1:8080/api/auth/login`
- `postoffice` TCP gateway available at `127.0.0.1:5148`
- existing demo accounts on the server side

## Run

```bash
cd server
./gradlew :apps:im_java_client_demo:cli-demo:run --args="--host 127.0.0.1 --tcp-port 5148 --base-url http://127.0.0.1:8080"
```

## Commands

- `login <userId> <password> <platformId>`
- `connect`
- `send <peerUserId> <text>`
- `heartbeat`
- `status`
- `quit`

Note:

- the CLI keeps a `password` parameter for the demo UX
- the current server login API is still a lightweight bootstrap API and does not validate a real password

## Two-terminal demo

Terminal A:

```text
login userA secret 2
connect
send userB hello
```

Terminal B:

```text
login userB secret 2
connect
send userA hi
```

Expected:

- both terminals print login success
- both terminals print TCP auth success
- sender prints send ack
- receiver prints inbound payload

## Verification

```bash
./gradlew :apps:im_java_client_demo:client-core:test
./gradlew :apps:im_java_client_demo:cli-demo:test
./gradlew :apps:im_java_client_demo:cli-demo:run --args="--help"
```
