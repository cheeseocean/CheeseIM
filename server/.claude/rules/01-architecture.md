# 项目架构与分层规范

## 模块结构

```text
xxx-project
├── settings.gradle
├── build.gradle
├── gradle.properties
├── bootstrap-all
├── common-api
├── postoffice
├── postman
├── postmaster
└── postbox
```

## 核心服务职责

### `postoffice`

`postoffice` 是接入层与在线路由层，负责：

- 处理 TCP / WebSocket 长连接
- 认证、心跳、断连与会话绑定
- 维护用户-设备-网关节点的在线路由
- 规范化客户端消息与回执后转发给后续服务

它不负责最终消息真相、离线存储或完整投递编排。

### `postmaster`

`postmaster` 是消息链路中的编排核心，负责：

- 消息幂等校验
- 会话序列分配
- 投递状态推进
- 回执、已读、撤回等状态收敛
- 补偿任务与死信处理

它不直接承担查询侧存储模型，也不负责网关接入。

### `postbox`

`postbox` 是存储边界，负责：

- 持久化消息历史
- 维护消息块和消息 ID 映射
- 提供历史拉取、会话视图与部分查询能力
- 维护与会话状态相关的 Redis 热数据

### `postman`

`postman` 是投递执行与离线推送边界，负责：

- 消费投递事件并执行在线投递
- 判断是否需要厂商离线推送
- 对推送尝试去重
- 在回执收敛后取消过期推送
- 对接 APNs / FCM / 极光等推送适配器

### `authcenter`

`authcenter` 提供轻量认证入口，当前主要用于本地联调和 Demo 登录流程。

### `social`

`social` 提供用户、好友、会话能力，主要用于IM中具有业务含义服务