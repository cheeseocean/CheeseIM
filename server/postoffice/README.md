# Postoffice

`postoffice` 是 CheeseIM 的长连接网关模块，负责维护客户端 TCP/WebSocket 连接、完成连接鉴权、管理在线路由，并接收/下发 Protobuf 消息。

## 职责

- 启动 TCP 服务端与 WebSocket 服务端。
- 基于 `common-api/src/main/proto/message_protocol.proto` 进行二进制协议编解码。
- 处理 `AUTH`、心跳、聊天消息入口和错误响应。
- 维护本机连接表、用户连接绑定关系和多端登录策略。
- 对外暴露在线路由查询、在线投递和踢下线能力。
- 调用 `authcenter` 完成 ticket/session 校验，调用 `postbox` 进入消息发送链路。

## 非职责

- 不负责生成 token 或 ticket。
- 不负责持久化历史消息。
- 不负责会话列表、好友、群组等 HTTP 业务接口。
- 不直接做离线推送；离线推送由 `postman` 编排。

## 关键类

| 类 | 说明 |
| --- | --- |
| `PostOffice` | 独立模块启动入口。 |
| `TcpServer` / `WebSocketServer` | TCP/WS 网络服务启动与生命周期管理。 |
| `TcpEnvelopeCodecSupport` | Protobuf envelope 编解码。 |
| `AuthMessageHandler` | 处理长连接鉴权请求。 |
| `ChatMessageHandler` | 接收客户端聊天消息并调用 `MessageSender`。 |
| `HeartbeatMessageHandler` | 心跳处理。 |
| `ConnectionManager` | 本机连接管理。 |
| `ConnectionBindService` | 用户、设备、连接绑定。 |
| `RedisOnlineRouteService` | 在线路由缓存实现。 |
| `OnlineDispatcherImpl` | 服务端向在线客户端投递消息。 |

## 端口

默认配置来自 `server/config/src/main/resources/module-postoffice.yml`：

| 协议 | 端口 |
| --- | --- |
| WebSocket | `5147` |
| TCP | `5148` |

本地开发推荐通过 `server/bootstrap-all` 启动，Dubbo 调用走 injvm。
