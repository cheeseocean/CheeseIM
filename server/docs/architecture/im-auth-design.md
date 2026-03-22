# IM 鉴权体系设计

> 状态：已过期。
> 当前共享鉴权契约与模块边界已收敛到 `common-core/common-api` 和
> [2026-03-22-im-full-refactor-design.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/docs/superpowers/specs/2026-03-22-im-full-refactor-design.md)。
> 本文保留仅用于历史设计参考，其中 `:common` 模块、旧 Dubbo 接口路径与旧编译命令不再适用于当前工程。

## 1. 目标

构建一套适用于中大型 IM 系统的鉴权体系，满足：

- 多端登录：iOS / Android / Web / PC
- 长连接接入：WebSocket
- 业务能力：单聊、群聊、频道、历史消息、附件下载
- 安全要求：主动踢线、封号、改密失效、权限变更快速生效
- 可扩展性：多租户、风控、频道角色、内部服务间鉴权

本方案显式区分：

- 登录态
- 连接态
- 会话权限
- 资源权限

## 2. 设计原则

1. 登录态采用 `access_token + refresh_token`
2. 服务端维护 `user_session`
3. WebSocket 不长期使用登录 token
4. 客户端必须先用 `access_token` 换短期 `ws_ticket`
5. 建连成功后 gateway 绑定 `uid/session_id/device_id/conn_id`
6. 服务端不能信任客户端传入的 `sender_id`
7. 单聊、群聊、频道统一做会话级权限校验
8. 历史消息、附件统一做资源级权限校验
9. token 失效不能只依赖自然过期，必须支持主动踢线
10. 内部服务调用不复用用户 token

## 3. 核心结论

### 3.1 登录态

登录态只解决“用户是谁、当前登录 session 是否有效”。

载体：

- `access_token`
- `refresh_token`
- `user_session`

### 3.2 连接态

连接态只解决“当前 WebSocket 连接代表谁”。

载体：

- `ws_ticket`
- `ConnectionContext`
- `ConnectionManager`

### 3.3 会话权限

会话权限只解决“这个用户是否可以访问某个会话并执行某种动作”。

动作包括：

- `READ`
- `SEND`
- `RECALL`
- `UPLOAD_ATTACHMENT`

### 3.4 资源权限

资源权限只解决“这个用户是否可以访问某个具体资源”。

资源包括：

- 历史消息
- 附件

## 4. 架构分层

### 4.1 Auth / Session 域

职责：

- 登录
- 刷新 token
- 签发 `ws_ticket`
- 管理 `user_session`
- 管理用户安全态
- 处理改密、封号、踢线

### 4.2 IM Gateway (`postoffice`)

职责：

- WebSocket 接入
- `AUTH(ticket)`
- 建立连接上下文
- 心跳
- 断连清理
- 主动踢线执行

### 4.3 Permission / Resource 域 (`postbox`)

职责：

- 会话权限校验
- 历史消息访问校验
- 附件访问校验

### 4.4 Message 域 (`postman`)

职责：

- 消息发送编排
- 发送前鉴权
- 幂等与投递

### 4.5 Push 域 (`push`)

职责：

- 离线推送
- 基于 session/security state 控制推送目标与内容

## 5. 登录态设计

### 5.1 Access Token

建议：

- TTL：15-30 分钟
- 用途：业务 API、申请 `ws_ticket`

claim 建议：

```json
{
  "sub": "u_10001",
  "sid": "sess_abc123",
  "did": "dev_ios_001",
  "tid": "tenant_01",
  "plat": "ios",
  "scope": ["im:connect", "msg:send", "history:read"],
  "ver": 5,
  "jti": "atk_xxx",
  "iat": 1710000000,
  "exp": 1710001800
}
```

### 5.2 Refresh Token

建议：

- TTL：7-30 天
- 仅认证服务可用
- 支持轮换
- 支持单 session 撤销

### 5.3 `user_session`

每次登录创建或更新一条 session。

关键字段：

- `session_id`
- `user_id`
- `tenant_id`
- `device_id`
- `platform`
- `status`
- `token_version`
- `permission_version`
- `password_version`
- `last_active_at`

## 6. WebSocket 建连鉴权设计

### 6.1 流程

1. 客户端登录，拿到 `access_token`
2. 客户端调用 `/api/im/ws-ticket`
3. 服务端校验 `access_token + session`
4. 服务端签发短期一次性 `ws_ticket`
5. 客户端建立 WebSocket
6. 首包发送 `AUTH(ticket)`
7. Gateway 校验 ticket
8. 绑定连接身份
9. 连接进入 `AUTHENTICATED`

### 6.2 Ticket 特性

- 一次性
- 短 TTL，建议 60 秒
- 绑定 `session_id/device_id/user_id`
- 可带 `token_version`
- 消费成功立即失效

### 6.3 连接上下文

建连成功后绑定：

- `conn_id`
- `user_id`
- `tenant_id`
- `session_id`
- `device_id`
- `platform`
- `token_version`
- `connected_at`
- `last_heartbeat_at`
- `state`

## 7. 消息发送鉴权

### 7.1 基本原则

客户端消息体中即使包含 `sender_id`，服务端也必须忽略。

真实发送者只能来自：

- `ConnectionContext.user_id`

### 7.2 发送校验顺序

1. 连接已认证
2. session 有效
3. 用户未封禁
4. 会话权限允许 `SEND`
5. 风控、限流通过
6. 写消息、投递

### 7.3 会话级权限动作

统一动作：

- `READ`
- `SEND`
- `RECALL`
- `UPLOAD_ATTACHMENT`

## 8. 历史消息与附件访问鉴权

### 8.1 历史消息

读取历史时必须校验：

1. 登录态有效
2. session 有效
3. conversation `READ` 权限有效

### 8.2 附件下载

附件下载必须走两段式：

1. 请求下载授权接口
2. 服务端校验资源权限后返回短期签名 URL

不允许直接暴露永久附件 URL。

## 9. 多端登录与设备管理

### 9.1 维度划分

- 用户级：`user_id`
- 设备级：`device_id`
- 会话级：`session_id`
- 连接级：`conn_id`

### 9.2 推荐策略

- iOS / Android：一个设备一个活跃 session
- Web：允许多标签页，多连接
- PC：按产品策略决定单实例或多实例

### 9.3 设备管理能力

应提供：

- 查看设备列表
- 下线指定设备
- 下线全部设备
- 查看最近登录时间 / IP

## 10. token 失效与踢线

### 10.1 失效场景

- 主动退出登录
- 封号
- 修改密码
- 权限变更
- 风控命中
- 管理员踢线

### 10.2 机制

同时使用：

- `user_session.status`
- `token_version`
- `permission_version`
- `password_version`
- `kickoff` 事件

### 10.3 踢线执行

事件产生后，Gateway 按：

- `user_id`
- `session_id`
- `device_id`

找到连接并执行：

1. 发送 `KICKOFF`
2. 主动关闭连接

### 10.4 兜底

即使踢线事件丢失，也要在以下环节兜底校验：

- 心跳
- 发消息
- 拉历史
- 下载附件

## 11. 内部服务间鉴权

内部服务调用不使用用户 token。

建议：

- mTLS，或
- internal JWT

要求：

- 服务身份和用户身份分离
- 需要代表用户执行时，显式透传用户上下文
- 接收方同时校验服务身份与用户上下文

## 12. 推荐存储结构

### 12.1 MySQL / PostgreSQL

表：

- `user_session`
- `user_security_state`
- `user_device`
- `conversation`
- `conversation_member`
- `message`
- `attachment`
- `kickoff_event_log`

### 12.2 Redis

key：

- `im:ws_ticket:{ticket}`
- `im:user_session:{session_id}`
- `im:user_security:{user_id}`
- `im:conn:{conn_id}`
- `im:user_conns:{user_id}`
- `im:session_conns:{session_id}`
- `im:device_conns:{user_id}:{device_id}`

## 13. 工程落地

### 13.1 `common`

输出：

- Dubbo 接口
- Auth DTO
- Redis key 常量
- 状态枚举

### 13.2 `postoffice`

负责：

- `AUTH(ticket)`
- 建立 `ConnectionContext`
- 心跳与踢线
- 转发“已绑定身份”的命令

### 13.3 `postman`

负责：

- 发送消息前鉴权
- 使用服务端注入 sender 身份

### 13.4 `postbox`

负责：

- 会话权限服务
- 资源权限服务

### 13.5 `push`

负责：

- 基于安全态过滤推送受众

## 14. 当前实现状态

截至当前仓库版本，以下能力已经落地：

- `common` 已统一输出 session、permission、kickoff 的 Dubbo 契约、DTO、状态枚举与 Redis key 常量
- `authcenter` 已独立承接登录、刷新、登出、`ws_ticket` 签发与 session revoke API，`/api/im/ws-ticket` 现仅由 `authcenter` 提供
- `postoffice` 已切换为 `AUTH(ticket)`，并在连接成功后绑定 `uid/session_id/device_id/conn_id`
- `postoffice` 发送消息与心跳只信任 `ConnectionContext`，不再信任客户端传入的 sender 身份
- `postman` 已接入发送前会话权限校验
- `postbox` 已接入会话列表、历史消息 `READ` 校验和附件资源授权校验
- `postoffice` standalone 现已默认依赖独立 `authcenter`，不再默认导出本地 session provider

当前仍然保留的阶段性实现：

- `authcenter` 的 session 目前仍是 Redis 持久化，不是数据库版 `user_session / user_security_state`
- 附件下载已升级为“鉴权后签发短期代理下载地址”，但底层仍依赖消息元数据中的 `downloadUrl`，还不是最终对象存储签名方案
- 密码修改失效、封号、权限版本变更已在架构中预留，但还没有完整接入真实账号中心与安全状态源
- `push` 仅完成安全态边界预留，尚未完成完整的受众过滤和敏感内容降级

## 15. 实施顺序

1. `common` 契约
2. Session / Ticket 域
3. `postoffice` 连接态鉴权
4. `postbox` 权限服务
5. `postman` 发送鉴权
6. 历史消息与附件鉴权
7. 踢线机制
8. 指标与审计

## 16. 验收标准

必须满足：

- WebSocket 只能使用 `ws_ticket`
- 登录 token 不再直接用于长连接
- Gateway 能绑定 `uid/session_id/device_id/conn_id`
- 服务端不信任客户端 `sender_id`
- 单聊、群聊、频道都有统一会话权限校验
- 历史消息与附件都有资源级权限校验
- 改密、封号、踢线能主动生效
- 内部服务间不复用用户 token

## 17. MVP 范围

MVP 必做：

1. `access_token + refresh_token`
2. Redis 版 `user_session`
3. `/api/im/ws-ticket`
4. WebSocket `AUTH(ticket)`
5. `ConnectionContext`
6. `SEND` 会话权限校验
7. `READ` 会话权限校验
8. 附件下载授权
9. session revoke + kickoff

MVP 暂缓：

- 复杂频道角色
- 复杂风控
- 多租户细粒度策略
- 设备指纹
- `jti` 黑名单体系

## 18. 已验证链路

当前仓库已完成并验证过的链路：

- `./gradlew :common:compileJava :authcenter:compileJava :postoffice:compileJava :postman:compileJava :postbox:compileJava :push:compileJava :bootstrap-all:compileJava`
- `./gradlew :authcenter:bootRun --args='--spring.config.name=application-authcenter'`
- `./gradlew :postoffice:bootRun --args='--spring.config.name=application-postoffice'`

最近一次 `postoffice` 干净启动验证结果：

- HTTP: `http://127.0.0.1:18080`
- Dubbo registry: `nacos://localhost:8848`
- WebSocket: `ws://localhost:5147/ws`
- TCP: `tcp://localhost:5148`

关于后续将 Redis 版 session 升级为数据库权威态 + Redis 运行态缓存的说明，见：

- [im-auth-session-upgrade.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/docs/architecture/im-auth-session-upgrade.md)

## 19. 结论

本方案将 IM 鉴权拆成四层：

- 登录态
- 连接态
- 会话权限
- 资源权限

这是最稳、最易维护、最适合中大型 IM 系统演进的结构。
后续无论扩展频道、多租户还是风控，都可以在这个边界上继续演进，而不需要推倒重来。
