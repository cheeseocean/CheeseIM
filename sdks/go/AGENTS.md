# sdks/go/AGENTS.md — Go IM Client SDK 约束

> 通用 IM Client SDK，供 CheeseBox 和后续其它 Go 应用复用。
> 改动前先读根 `AGENTS.md` + 本文件。

## 1. 包结构

| 包 | 职责 |
| --- | --- |
| `client` | 顶层 `Client` 装配各子模块，对应用层暴露统一入口 |
| `auth` | HTTP 登录、token 刷新、登出、ws-ticket 申请 |
| `transport/httpapi` | HTTP 调用封装 |
| `transport/tcpim` | TCP 长连接（Protobuf），心跳、重连 |
| `proto` | 由服务端唯一 Proto 源生成的 Go 代码，**不要手改** |
| `sync` | 会话同步、seq range 拉取、gap repair |
| `social` | 好友/群成员查询 |
| `types` | 公共类型 |

## 2. 与 Java 服务端的契约对齐

- **协议源只一个**：`server/common-api/src/main/proto/message_protocol.proto`。执行 `go generate ./proto` 将其直接生成到 `proto/`，SDK 内不得维护 Proto 副本。
- HTTP API 路径与 `server/api-server` 的 Controller 路径一致（见 `api-server/ARCH.md` §1）。
- 会话 id / seq 语义与 server `ConversationIdUtil` 完全一致：`s:/g:/n:/ng:`，**不要**在 Go 侧引入新的 id 形态。

## 3. 命名与风格

- 导出标识 `CamelCase`，包名小写，接口**不加 `I` 前缀**。
- 错误用 `error` 返回；业务错误用 sentinel 变量 + `errors.Is`，不要 panic 跨包传播。
- 不引入 Java 依赖；构建只走 `go test ./...` + `go build`。

## 4. 边界

- SDK 不做业务策略（谁允许给谁发消息等），业务策略在 server。
- SDK 只缓存展示状态和同步游标；消息真相以 server Mongo 为准，重启后必须从 server 重新同步。
- TCP 与 WS 都使用 Binary Protobuf envelope；HTTP 控制面仍使用 JSON。**不得新增 JSON 长连接协议路径**。

## 5. 验证

```bash
cd sdks/go
go test ./...
go build ./...
```

## 6. 改动评估 checklist

- [ ] 改 proto 字段后重新生成 + server 同步重生成
- [ ] 改 HTTP 路径需同步 `api-server` Controller
- [ ] 改 seq 同步语义需同步 `business/ARCH.md` §3 与 `docs/CheeseIM-数据同步设计文档.md`
- [ ] 加新模块不要引入 Java 依赖或与 server 共享运行时

## 7. 勘误记录

（暂无）
