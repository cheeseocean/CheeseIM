# Client Runbook

当前客户端侧以 Go SDK 和 CheeseBox 为主。历史 Dart/Flutter/Web 客户端文档已经不再代表当前代码结构；当前事实以本手册、README 与代码为准。

## Workspace

| 路径 | 说明 |
| --- | --- |
| `sdks/go` | 通用 Go IM Client SDK，封装 HTTP 登录、ticket 获取、TCP 长连接、消息收发和同步能力。 |
| `apps/CheeseBox` | TUI 聊天应用，集成 `sdks/go`，用于真实双端联调。 |
| `server/postoffice` | 长连接协议与网关实现，协议以 Protobuf 为准。 |
| `server/api-server` | HTTP API 入口，提供登录、ticket、会话同步、好友/用户相关接口。 |

## Protocol Boundary

- HTTP API 用于登录、刷新 token、申请长连接 ticket、会话同步、好友和用户设置。
- TCP/WebSocket 用于实时长连接消息，统一承载 `ProtoClientEnvelope` / `ProtoServerEnvelope`。
- WebSocket 不再使用 JSON envelope；TCP/WS 都以 `message_protocol.proto` 为准。
- `server/common-api/src/main/proto/message_protocol.proto` 是唯一协议源；在 SDK 根目录执行 `go generate ./proto` 生成 Go 产物，禁止维护 SDK 内 Proto 副本或手改 `message_protocol.pb.go`。
- 客户端本地只缓存展示状态和同步游标，服务端消息历史以 MongoDB 历史块为准。

## Common Commands

### Server

```bash
cd server
./gradlew :bootstrap-all:bootRun
```

### Go SDK

```bash
cd sdks/go
go generate ./proto
go test ./...
```

### CheeseBox

```bash
cd apps/CheeseBox
go test ./...
go run ./cmd/cheesebox
```

## Manual Smoke Test

1. 启动 MongoDB 与 Redis。
2. 启动 `server:bootstrap-all`。
3. 打开两个终端，分别启动 CheeseBox。
4. 使用两个不同用户登录。
5. 通过好友/会话入口发送消息。
6. 验证在线消息到达。
7. 重启其中一个 CheeseBox，验证会话同步与历史消息拉取。

## Current Coverage

- Go SDK：HTTP 登录、ticket 获取、长连接认证、消息发送、下行消息监听、同步接口封装。
- CheeseBox：TUI 登录、会话/好友导航、左右消息布局、主题/语言设置、真实服务端连接。
- Server：all-in-one 本地联调、Protobuf 长连接协议、会话同步 HTTP API。

## Current Limitations

- 文件、图片、语音等富媒体消息仍需继续补齐。
- 分模块部署需要先校准独立启动配置和 Dubbo 注册中心。
- CheeseBox 是唯一主线联调客户端；实验 Web 客户端已删除，未来 Web 客户端必须直接消费同一份 Protobuf 协议。
