# CheeseBox TUI Design

## 1. Goal

`apps/CheeseBox` 需要从零补全为一个可直接联调当前 CheeseIM server 的终端客户端。

本阶段目标只覆盖最小闭环，但要包含用户实际可用的登录和消息链路：

- 通过现成 `access token` 登录
- 通过 HTTP 获取 `ws-ticket`
- 通过 TCP protobuf 建立 IM 长连接
- 查看好友与群组入口
- 打开会话并拉取最近一页历史消息
- 发送和接收文本消息
- 提供基础错误提示和手动重连

不在本阶段实现：

- 用户名密码登录
- 自动重连补同步
- 历史分页滚动
- 已读回执
- 文件消息
- 本地数据库缓存

## 2. Server Alignment

当前服务端可明确对齐的接口和协议面：

- HTTP `POST /api/im/ws-ticket`
  - 使用 `Authorization: Bearer <access-token>`
  - 返回 `ticket`、`expire_at`、`ws_url`
- HTTP `GET /api/im/friends`
  - 返回好友列表
- IM 长连接
  - 使用 `postoffice` 的 TCP protobuf 协议
  - 核心载体为 `ProtoClientEnvelope` / `ProtoServerEnvelope`

当前仓库中，群组列表、最近会话、历史消息的 HTTP controller 暴露面还未完全稳定。因此 TUI 不直接绑定具体 controller 名字，而是通过 adapter 层封装“群组列表查询”“会话历史查询”能力，对接当前服务端可用接口或后续补齐的 controller。

## 3. Architecture

推荐采用分层实现，而不是把 HTTP/TCP 逻辑直接塞进 Bubble Tea model。

目录结构：

- `cmd/cheesebox`
  - 程序入口
- `internal/config`
  - server 地址、token、device 配置
- `internal/proto`
  - protobuf 生成代码
- `internal/transport/httpapi`
  - `ws-ticket`、好友、群组、历史、加好友请求
- `internal/transport/tcpim`
  - TCP 建连、protobuf 编解码、鉴权、心跳、消息收发
- `internal/service`
  - `AuthService`
  - `RosterService`
  - `ConversationService`
  - `ChatService`
- `internal/store`
  - 本地 UI 状态和领域状态
- `internal/ui`
  - Bubble Tea 页面与组件

边界约束：

- UI 不直接依赖 protobuf message
- transport 只处理协议和网络
- service 把协议对象转换成稳定的领域对象
- store 只保存 UI 需要的状态

## 4. Core Flows

### 4.1 Login

登录页字段：

- `api_base_url`
- `tcp_addr`
- `access_token`
- `device_id`
- `platform`

流程：

1. 调用 HTTP `POST /api/im/ws-ticket`
2. 拿到 `ticket`
3. 建立 TCP 连接
4. 发送 `ProtoClientEnvelope.auth`
5. 收到 `ProtoServerEnvelope.auth` 成功响应
6. 进入主界面

失败处理：

- HTTP 获取 ticket 失败，停留登录页
- TCP 建连失败，停留登录页
- TCP 认证失败，停留登录页
- 所有失败都在页面底部 toast 和顶部状态栏显示

### 4.2 Initial Load

登录成功后初始化数据：

- 好友列表
- 群组列表
- 最近会话列表

如果服务端“最近会话列表”对接面不稳定，允许第一版退化为：

- 左栏好友和群组列表可直接打开会话
- 最近会话由本地打开记录维护

### 4.3 Open Conversation

选择好友或群组后：

1. 构造 `ConversationRef`
2. 调用历史 adapter 查询最近一页消息
3. 把结果转换成 `MessageItem`
4. 刷新消息区

本阶段只实现最近一页历史，不做分页滚动。

### 4.4 Send Message

发送流程：

1. UI 生成临时 `client_msg_id`
2. 本地先插入一条 `sending` 状态消息
3. 通过 TCP 发送 `ProtoClientEnvelope.chat`
4. 收到 ack 后更新状态为 `accepted` 或 `failed`

### 4.5 Receive Message

TCP 收到聊天推送后：

1. 解析 `ProtoServerEnvelope`
2. 转换为 `MessageItem`
3. 若当前会话匹配，则直接追加到消息区
4. 若当前不在该会话，则更新最近会话摘要和未读占位

### 4.6 Reconnect

本阶段只做手动重连：

- 连接断开后状态栏显示 `disconnected`
- 用户按快捷键触发重新获取 ticket 和重新建连
- 不做自动补历史和断点同步

## 5. Data Model

领域模型保持稳定，避免 UI 直接依赖 server DTO。

### 5.1 ConversationRef

- `id`
- `kind`
- `title`
- `peerID`
- `groupID`

### 5.2 MessageItem

- `id`
- `clientMsgID`
- `senderID`
- `senderName`
- `contentText`
- `sendTime`
- `status`
- `isSelf`

### 5.3 AppState

- `connectionStatus`
- `activeNav`
- `focusArea`
- `friends`
- `groups`
- `conversations`
- `activeConversation`
- `messagesByConversation`
- `toast`

## 6. TUI Structure

### 6.1 Pages

- `LoginModel`
- `AppModel`

### 6.2 Main Layout

三栏布局：

- 左侧导航
- 中间列表
- 右侧聊天区

顶部状态栏显示：

- server 地址
- 当前连接状态
- 当前用户或 token 摘要

底部状态栏显示：

- 快捷键提示
- 最近错误或成功提示

### 6.3 Navigation

导航项：

- `Chats`
- `Friends`
- `Groups`
- `Settings`

行为：

- `Chats` 显示最近会话
- `Friends` 显示好友列表并可直接进入单聊
- `Groups` 显示群组列表并可进入群聊
- `Settings` 只显示连接信息和配置摘要

### 6.4 Chat Area

聊天区由三部分组成：

- Header
- MessageViewport
- Input

要求：

- 群聊必须显示发送者昵称
- 自己的消息与他人消息在样式上区分
- 输入框默认单行发送

## 7. Commands And Keybindings

快捷键：

- `Tab` 切换焦点
- `j` / `k` 或方向键上下移动
- `Enter` 打开会话或发送消息
- `Esc` 退出输入焦点
- `c` 切到 `Chats`
- `f` 切到 `Friends`
- `g` 切到 `Groups`
- `r` 手动重连
- `?` 打开帮助
- `q` 退出

第一版“加好友”不做弹窗，统一走命令模式：

- `/addfriend <userId> [message]`

这样可以减少 Bubble Tea 焦点和弹层管理复杂度，同时覆盖文档里的加好友最小闭环。

## 8. Error Handling

必须明确反馈这些错误：

- `ws-ticket` 获取失败
- TCP 建连失败
- 鉴权失败
- 历史加载失败
- 发消息失败
- 加好友失败

呈现方式：

- 顶部状态栏展示当前连接状态
- 底部 toast 展示最近一条错误详情

## 9. Testing Strategy

测试分三层：

- unit
  - service 层对象转换
  - store 状态更新
  - keybinding 和命令解析
- integration
  - HTTP adapter 请求与响应解析
  - TCP protobuf 编解码
- manual
  - 对接本地 `bootstrap-all`
  - access token 登录
  - 单聊消息收发
  - 群聊消息接收
  - 历史消息加载

## 10. Implementation Boundaries

第一阶段交付完成的标准：

- 能输入 `access token` 登录
- 能通过 TCP protobuf 完成 IM 认证
- 能看到好友和群组入口
- 能打开会话并加载最近一页历史
- 能发送文本消息
- 能接收实时文本消息
- 能手动重连

如果服务端群组或历史 HTTP 接口在实现时仍未稳定，TUI 仍按上述结构落地，但通过 adapter 隔离接口差异，不在 UI 层传播协议不稳定性。
