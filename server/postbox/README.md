# Postbox

`postbox` 是 CheeseIM 的消息接入与历史查询模块。它处在长连接网关和消息编排模块之间，负责接收发送请求、做发送前策略判断，并把消息发布为 ingress event。

## 职责

- 实现 `MessageSender`，作为 TCP/WS、通知系统和其他业务模块的统一发送入口。
- 封装消息选项判断，例如是否计入未读、是否需要离线推送、是否需要历史持久化。
- 将合法消息写入 ingress 队列，由 `postmaster` 继续编排。
- 提供历史消息查询服务，供 HTTP 会话同步接口按需拉取消息。

## 非职责

- 不分配最终消息 seq。
- 不直接写历史块。
- 不维护会话列表展示模型。
- 不直接向在线连接投递消息。

## 关键类

| 类 | 说明 |
| --- | --- |
| `Postbox` | 独立模块启动入口。 |
| `MessageSenderImpl` | 发送入口实现。 |
| `MessageOptionPolicy` | 消息选项与投递策略判断。 |
| `IngressMessagePublisher` | ingress event 发布。 |
| `HistoryQueryService` | 历史消息查询。 |
| `BlockMessageQueryService` | 基于历史块的消息读取。 |

## 与其他模块的关系

- `postoffice` 调用 `MessageSender` 发送客户端聊天消息。
- `common-core` 的 `NotificationSender` 调用 `MessageSender` 发送系统通知。
- `postmaster` 消费 `postbox` 发布的 ingress event。
- `api-server`/`business` 可通过历史查询能力完成会话消息同步。
