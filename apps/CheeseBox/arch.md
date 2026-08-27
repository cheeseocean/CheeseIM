# CheeseBox 架构

> **状态：权威。** CheeseBox 是 CheeseIM 当前唯一的交互式联调客户端；它只负责 TUI 状态和渲染，不拥有 IM 协议或业务规则。

## 边界

```text
CheeseBox TUI
    -> sdks/go client
        -> HTTP API：登录、ticket、会话与社交查询、历史同步
        -> TCP：Binary Protobuf 实时消息与控制通知
```

- 服务端 `message_protocol.proto` 是 TCP/WS 的唯一协议源。CheeseBox 只能使用 `sdks/go/proto` 的生成类型，不能维护 JSON envelope、手写协议模型或第二份 Proto。
- HTTP 仅承载登录、ticket 和查询/同步控制面；实时消息和控制通知走 TCP Protobuf。
- CheeseBox 不直连 MongoDB、Redis、Kafka 或 Dubbo，也不在 UI 层复写 SDK 的鉴权、重连、seq 同步策略。

## 目录

| 路径 | 职责 |
| --- | --- |
| `cmd/cheesebox` | 唯一应用入口，装配运行时配置和 TUI。 |
| `internal/config` | 环境变量读取；默认 all-in-one API 为 `http://127.0.0.1:18079`，TCP 为 `127.0.0.1:5148`。 |
| `internal/ui` | Bubble Tea Model、View 与用户交互状态。 |
| `sdks/go` | 通用客户端能力的唯一提供方。 |

## 生命周期

1. 读取 `CHEESEBOX_*` 环境变量，未配置时使用 all-in-one 默认地址。
2. 通过 SDK 调用 HTTP 登录并申请 TCP ticket。
3. SDK 建立 TCP Binary Protobuf 连接，负责认证、心跳、重连与同步游标修复。
4. UI 订阅 SDK 的消息与控制事件；实时消息写入本地 store 后，由 SDK 提交设备级 delivery 高水位，发送方按 broker ACK、delivery notify 和单聊 peer read notify 更新展示状态。
   撤回 notify 以 `serverMsgId + mutationVersion` 幂等覆盖消息；通知先到时暂存 tombstone，消息后到仍必须隐藏原内容。
   输入中信号仅保存在内存：客户端对 START 节流，按服务端 `expiresAt` 自动清理，并在提交、清空或离开输入框时尽力发送 STOP；不得写入本地消息历史。
5. 断线或重启后，SDK 从服务端拉取同步结果；本地状态不是真相来源。

好友申请列表与处理走 SDK HTTP API；TCP 70–72 仅作为 roster 失效信号触发刷新，不作为聊天消息写入本地历史。

## 开发与验证

```bash
cd sdks/go
go generate ./proto
go test ./...

cd ../../apps/CheeseBox
go test ./...
go run ./cmd/cheesebox
```

新增 UI 能力前，先在 SDK 提供通用契约；CheeseBox 只负责将其绑定到界面。富媒体与管理类入口尚未实现时，应显式展示能力缺口，不得绕过 SDK 直连服务端基础设施。
