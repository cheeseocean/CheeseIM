# IM Session 持久化升级说明

> 状态：规划中，未实现。
> 当前工程的有效鉴权与会话边界以
> 原始设计链接已失效；当前认证事实以 `authcenter/ARCH.md` 和 `ASSESSMENT.md` 为准。
> 以及 `common-core/common-api` 中已落地的共享契约为准。
> 本文描述的是后续可能推进的 session 权威态升级方向，不代表当前代码已经切换到数据库权威态。

## 1. 目的

当前 IM 鉴权链路已经打通：

- `authcenter` 负责登录、刷新、登出、`ws_ticket` 签发
- `postoffice` 负责 `AUTH(ticket)`、连接态绑定、踢线
- `postman` 负责发送前会话权限校验
- `postbox` 负责历史消息与附件资源权限校验

当前 `session` 仍以 Redis 为主存储，这是符合当前阶段目标的。  
本说明用于记录后续将 Redis 版 session 升级为“数据库权威态 + Redis 运行态缓存”的目标方案、触发时机和实施边界。

## 2. 当前状态

### 2.1 已落地

- `access_token + refresh_token`
- `ws_ticket`
- `ConnectionContext`
- session revoke + kickoff
- 历史消息读取鉴权
- 附件下载授权

### 2.2 当前 session 方案

当前 `authcenter` 使用 Redis 维护：

- `im:user_session:{sessionId}`
- `im:user_security:{userId}`
- `im:ws_ticket:{ticket}`
- `im:user_conns:{userId}`
- `im:session_conns:{sessionId}`
- `im:device_conns:{userId}:{deviceId}`

这套方案适合当前阶段，因为：

- 鉴权读写频繁，Redis 延迟低
- Gateway、心跳、踢线、ticket 消费都需要运行态快速访问
- 先把链路打通比先做重持久化更重要

## 3. 为什么暂时不升级数据库版 session

当前不做数据库版 `user_session`，主要有三个原因：

### 3.1 当前主要目标不是会话治理，而是链路闭环

当前优先级仍然是：

- 长连接接入稳定
- 鉴权边界清晰
- sender 身份可信
- 会话权限与资源权限生效
- revoke/kickoff 可用

如果过早引入数据库权威态，会让当前阶段同时承担：

- 表结构设计
- ORM / repository
- 缓存一致性
- 数据迁移
- 审计能力

这会显著增加复杂度，但不会直接提升当前链路打通率。

### 3.2 当前链路里大量动作是运行态高频操作

例如：

- `AUTH(ticket)`
- 心跳续命
- 连接在线索引
- session 是否有效
- revoke 后快速踢线

这些动作本质上更适合 Redis，不适合每次都回源数据库。

### 3.3 当前还没有完整账号中心 / 安全状态源对接

数据库版 session 真正有价值，通常是在这些能力都要接入时：

- 修改密码全量失效
- 封号 / 解封
- 权限版本变更
- 设备管理
- 登录历史审计
- 多租户运营查询

在这些上游还没完整接入前，先做数据库主表的收益有限。

## 4. Redis 版与数据库版的职责差异

### 4.1 Redis 版 session

适合承载：

- `ws_ticket`
- 当前活跃 session 缓存
- 在线连接索引
- 快速 revoke / kickoff
- 心跳与消息前快速校验

特点：

- 快
- 简单
- 适合高并发运行态
- 不适合做长期权威审计

### 4.2 数据库版 session

适合承载：

- 正式 `user_session`
- 正式 `user_security_state`
- `user_device`
- 登录历史
- 管理后台查询
- 审计、风控、排障

特点：

- 语义稳定
- 可追溯
- 可查询
- 不适合直接承担 Gateway 高频校验

### 4.3 最终推荐模型

最终推荐是双层：

- 数据库：权威状态源
- Redis：运行态缓存和索引

而不是二选一。

## 5. 后续升级触发条件

满足以下任一条件时，建议启动数据库版 session 升级：

### 5.1 业务侧开始要求设备管理

例如：

- 查看已登录设备
- 下线指定设备
- 最近登录记录
- 可信设备标记

### 5.2 安全侧开始要求强审计与追溯

例如：

- 谁在什么时间登录
- 谁何时被踢线
- 修改密码后哪些 session 被失效
- 被封号前后连接与资源访问轨迹

### 5.3 多租户与运营后台开始落地

例如：

- 租户维度查询活跃 session
- 后台按用户 / 设备 / session 搜索
- 登录分布和设备画像

### 5.4 需要把安全态与账号中心强绑定

例如：

- 密码版本
- 权限版本
- 封号状态
- 风控锁定状态

### 5.5 当前 Redis session 已经成为治理瓶颈

例如：

- 需要复杂分页查询
- 需要历史归档
- 需要跨时间窗口统计
- 需要跨维度检索

## 6. 目标态设计

### 6.1 数据库表

后续建议正式落以下表：

- `user_session`
- `user_security_state`
- `user_device`
- `kickoff_event_log`

### 6.2 Redis 保留内容

即使升级数据库版，Redis 仍保留：

- `im:ws_ticket:{ticket}`
- `im:user_session:{sessionId}`
- `im:user_security:{userId}`
- `im:user_conns:{userId}`
- `im:session_conns:{sessionId}`
- `im:device_conns:{userId}:{deviceId}`

### 6.3 读写策略

建议策略：

- 写入时：
  - 先写数据库
  - 再刷新 Redis 缓存
  - 再执行 revoke / kickoff

- 读取时：
  - Gateway 高频路径优先查 Redis
  - Redis miss 再回源数据库
  - 回源后重建缓存

## 7. 升级范围

后续升级数据库版 session 时，主要会影响这些模块：

### 7.1 `authcenter`

需要新增或替换：

- `SessionRepository`
- `UserSecurityRepository`
- `UserDeviceRepository`
- `RefreshTokenRepository`

同时需要把当前 Redis-only 的 session 生命周期实现，收敛成：

- DB 主写
- Redis 缓存

### 7.2 `postoffice`

影响较小，仍然主要消费：

- `SessionQueryDubboService`
- `SessionIssueDubboService`
- `SessionRevocationDubboService`

也就是说，`postoffice` 不应该感知底层从 Redis-only 升级成 DB + Redis。

### 7.3 `postman / postbox / push`

基本不需要直接感知持久化方式变化，只依赖：

- session 查询结果
- security state
- permission 结果

## 8. 不在本次升级范围内的内容

即使后续做数据库版 session，也不应该顺手一起打包做：

- 重写全部 token 体系
- 重写 WebSocket 协议
- 重写会话权限模型
- 重写附件授权模型
- 引入复杂风控系统

这些都属于不同子问题，应该拆开。

## 9. 推荐升级顺序

建议等现有链路全部稳定后，再按下面顺序推进：

1. 设计 `user_session / user_security_state / user_device` 表
2. 在 `authcenter` 增加数据库 repository
3. 登录、刷新、登出先切到 DB 主写
4. 保留 Redis 作为 session 缓存
5. revoke / kickoff 改成“先改 DB，再刷 Redis，再踢线”
6. 最后接入后台设备管理与审计能力

## 10. 升级完成判定

数据库版 session 升级完成后，至少应满足：

- session 在数据库中有权威记录
- Redis 只承担缓存与运行态索引
- 修改密码可基于数据库权威态全量失效 session
- 封号可基于数据库安全态快速踢线
- 可按用户 / 设备 / session 查询历史登录与当前状态
- Gateway 不因为数据库引入而增加明显鉴权延迟

## 11. 当前结论

当前阶段继续保留 Redis 版 session 是合理的。  
它已经足够支撑：

- `authcenter`
- `ws_ticket`
- `postoffice` 长连接鉴权
- 历史消息与附件授权
- revoke / kickoff

数据库版 session 应作为下一阶段的治理升级，而不是当前阶段的阻塞项。  
建议等现有链路、联调、权限与资源访问全部稳定后，再正式启动该升级。
