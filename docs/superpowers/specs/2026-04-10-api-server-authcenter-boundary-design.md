# API Server And AuthCenter Boundary Design

## Problem

当前 HTTP 入口分散在两个模块内：

- `business` 同时承载业务 service 和 `Controller`
- `authcenter` 同时承载认证 service 和 `Controller`

这会带来几个问题：

- REST 入口层分散，路由、鉴权解析、异常处理难以统一
- `authcenter` 同时承担认证领域和 HTTP 适配职责，边界不清晰
- `business` 引入 `spring-boot-starter-web` 只是为了暴露 HTTP 接口，不符合领域模块定位
- `bootstrap-all` 需要同时扫描多个模块中的 controller，后续扩展入口层会越来越乱

## Recommended Structure

新增统一的 `api-server` 模块，专门承接 HTTP 入口：

- `api-server`
  - 全部 `@RestController`
  - HTTP 专用 facade
  - `AccessTokenSessionResolver`
  - 全局异常处理
- `authcenter`
  - 登录、登出、刷新
  - session lifecycle
  - `ws-ticket` 签发与消费
  - 不再放 `Controller`
- `business`
  - 用户、好友、群组、会话等业务 service
  - 不再放 `Controller`
- `postoffice`
  - TCP/WS 接入
  - 长连接认证与连接绑定

## Why Keep AuthCenter Separate

`authcenter` 不应并入 `business`，原因如下：

- 认证和 IM 业务属于不同领域
- `postoffice` 需要直接依赖认证服务消费 `ws-ticket`
- 将来扩展 refresh token、设备管理、踢下线、风控时，独立模块更清晰
- 让 `business` 避免承载 token、ticket、session 这类接入安全逻辑

## Migration Scope

迁移到 `api-server` 的内容：

- `AuthController`
- `WsTicketController`
- `FriendController`
- `BlacklistController`
- `GroupController`
- `ConversationController`
- `UserSettingsController`
- `AccessTokenSessionResolver`
- `ConversationFacade`
- HTTP 层统一异常处理

保留在 `authcenter` 的内容：

- `SessionIssueService`
- `SessionLifecycleService`
- ticket 相关 service
- token/session repository

保留在 `business` 的内容：

- friend/group/user/conversation 等业务 service
- 业务领域编排

## Dependency Direction

依赖方向应调整为：

- `api-server -> authcenter`
- `api-server -> business`
- `api-server -> postbox`
- `postoffice -> authcenter`
- `business -> common-api/common-core/postbox`

不应继续存在：

- `business -> spring web controller`
- `authcenter -> spring web controller`

## Bootstrap-All Expectation

`bootstrap-all` 继续作为聚合启动模块，但 HTTP 入口只来自 `api-server`。  
`authcenter` 和 `business` 仍作为被扫描的 service 模块参与装配，不再直接暴露各自 controller。
